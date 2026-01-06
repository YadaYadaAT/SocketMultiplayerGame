package com.athtech.gomoku.server.match;

import com.athtech.gomoku.protocol.messaging.MatchEndReason;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.server.game.Game;
import com.athtech.gomoku.server.net.LobbyController;
import com.athtech.gomoku.server.net.ServerNetworkAdapter;
import com.athtech.gomoku.server.persistence.PersistenceManager;
import com.athtech.gomoku.protocol.payload.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public class MatchController {

    private final MatchManagerImpl matchManager;
    private final BiConsumer<String, NetPacket> sendToClient;
    private final LobbyController lobbyController;
    private final PersistenceManager persistence;
    // pending invites <targetUsername, list of fromUsername>
    private final Map<String, CopyOnWriteArrayList<String>> pendingInvites = new ConcurrentHashMap<>();
    private final Object invitesLock = new Object();

    public MatchController(ServerNetworkAdapter server, LobbyController lobbyController, PersistenceManager persistence) {
        this.lobbyController = lobbyController;
        this.matchManager = new MatchManagerImpl(server,persistence,() -> lobbyController.broadcastLobby(this) );
        this.sendToClient = server::sendToClient;
        this.persistence = persistence;
    }

    // -----------------------
    // Match creation
    // -----------------------
    public void createMatch(String player1, String player2) {
        try {
            Match match = matchManager.createMatch(player1, player2);
            clearInvitesForMatchPlayers(player1,player2);
            broadcastMatchCreate(match);
        } catch (IllegalStateException e) {
            sendToClient.accept(player1, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                    new InviteResponse(false, "Cannot create match, one of the players is busy")));
        }
    }

    // -----------------------
    // Moves
    // -----------------------
    public void processMove(String username, MoveRequest move) {
        matchManager.getMatchByPlayer(username).ifPresentOrElse(match -> {
            boolean ok = match.makeMove(username, move);
            if (!ok) {
                sendToClient.accept(username, new NetPacket(PacketType.MOVE_REJECTED_RESPONSE, "server",
                        new MoveRejectedResponse("Invalid move or not your turn", match.getCurrentPlayer())));
            } else {
                if (!match.isFinished()) {
                    broadcastMatchUpdate(match);
                } else {
                    boolean draw = match.isDraw();
                    MatchEndReason winnerReason;
                    if (draw) {
                        winnerReason = MatchEndReason.DRAW;
                    } else {
                        winnerReason = MatchEndReason.WIN_NORMAL;
                    }
                   endDaMatch(match, winnerReason);
                }
            }
        }, () -> sendToClient.accept(username, new NetPacket(PacketType.MOVE_REJECTED_RESPONSE, "server",
                new MoveRejectedResponse("No active match found. Are you in a game?", null))));
    }


    // -----------------------
    // Ending match
    // -----------------------
    public void endDaMatch(Match match, MatchEndReason reason) {
        match.markEnded();
        persistence.recordMatchResult(match.getPlayer1(), match.getPlayer2(), match.isDraw(), match.getWinner());
        sendUpdatedStats(match.getPlayer1());
        sendUpdatedStats(match.getPlayer2());
        broadcastRegularMatchEnd(match, reason);

    }

    public boolean handleGameQuit(String quitter) {
        return matchManager.getMatchByPlayer(quitter).map(match -> {
            if (match instanceof MatchImpl impl) {
                impl.playerDisconnected(quitter);
                String opponent = quitter.equals(match.getPlayer1()) ? match.getPlayer2() : match.getPlayer1();

                // Notify opponent
                sendToClient.accept(opponent, new NetPacket(
                        PacketType.GAME_QUIT_NOTIFICATION_RESPONSE,
                        "server",
                        new GameQuitNotificationResponse(quitter)
                ));

                // Use per-player enum
                MatchEndReason winnerReason = MatchEndReason.WIN_QUIT;
                matchManager.handleForcedEnd(impl, opponent, winnerReason);
            }
            return true;
        }).orElse(false);
    }

    // Invites
    public void sendInvite(String fromUsername, String targetUsername) {
        if (fromUsername.equals(targetUsername)) {
            sendToClient.accept(fromUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                    new InviteResponse(false, "You cannot invite yourself")));
            return;
        }

        if (!lobbyController.isUserLoggedIn(targetUsername)) {
            sendToClient.accept(fromUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                    new InviteResponse(false, "Invitation failed, player is not online")));
            return;
        }

        if (matchManager.getMatchByPlayer(targetUsername).isPresent()) {
            sendToClient.accept(fromUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                    new InviteResponse(false, "Invitation failed, player already in game")));
            return;
        }

        pendingInvites.computeIfAbsent(targetUsername, k -> new CopyOnWriteArrayList<>()).add(fromUsername);

        sendToClient.accept(targetUsername, new NetPacket(PacketType.INVITE_NOTIFICATION_RESPONSE, "server",
                new InviteNotificationResponse(fromUsername)));
        sendToClient.accept(fromUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                new InviteResponse(true, null)));
    }

    public void processInviteDecision(String targetUsername, String inviterUsername, boolean accepted) {
        CopyOnWriteArrayList<String> invites = pendingInvites.get(targetUsername);
        if (invites == null || !invites.contains(inviterUsername)) return;

        invites.remove(inviterUsername);

        InviteDecisionResponse resp = new InviteDecisionResponse(inviterUsername, targetUsername, accepted);
        sendToClient.accept(inviterUsername, new NetPacket(PacketType.INVITE_DECISION_RESPONSE, "server", resp));
        sendToClient.accept(targetUsername, new NetPacket(PacketType.INVITE_DECISION_RESPONSE, "server", resp));

        if (invites.isEmpty()) pendingInvites.remove(targetUsername);

        if (accepted) {
            try {
                createMatch(inviterUsername, targetUsername);
            } catch (IllegalStateException e) {
                sendToClient.accept(inviterUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                        new InviteResponse(false, "Cannot create match, one of the players is busy")));
            }
        }
    }

    public InviteNotificationResponse[] getInvitationsFor(String targetUsername) {
        CopyOnWriteArrayList<String> invites = pendingInvites.get(targetUsername);
        if (invites == null || invites.isEmpty()) return new InviteNotificationResponse[0];

        return invites.stream().map(InviteNotificationResponse::new).toArray(InviteNotificationResponse[]::new);
    }

    public synchronized void sendRematchRequest(String username, boolean consent) {
        matchManager.getMatchByPlayer(username).ifPresent(match -> {
            if (!consent) {
                try{
                    handleRematchDecline(username,match);
                }catch (IllegalStateException e){
                    if (match.isEnded()) {
                        //empty so far case of throw on decline at the game end rematch
                    }else{//midgame rematch decline throw exceptions.
                        sendToClient.accept(username, new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                                new RematchResponse(true, e.getMessage())));
                    }
                }

            } else {
                try{
                    handleRematchAccept(username,match);
                }catch (IllegalStateException e) {

                    if (match.isEnded()) {
                        sendToClient.accept(username, new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                                new RematchResponse(false, e.getMessage())));
                        sendMatchSessionEndResponseToPlayer(username, false);
                    }else{
                        sendToClient.accept(username, new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                                new RematchResponse(true, e.getMessage())));
                    }


                    if (match.getMatchPlayers().isEmpty()) matchManager.endMatch(match.getMatchId());//remove match if empty of players
                    return;
                }

            }

            if (match.isRematchReady()) {
                handleRematchReady(match);
            }

        });

    }

    private void handleRematchReady(Match match){
        String p1 = match.getPlayer1();
        String p2 = match.getPlayer2();
        try {
            matchManager.endMatch(match.getMatchId());
            Match newMatch = matchManager.createMatch(p1, p2);
            if (match.isEnded()){
                sendMatchSessionEndResponseToPlayers(p1, p2, true);
            }
            broadcastMatchCreate(newMatch);
        } catch (IllegalStateException e) {
            if (match.isEnded()){
                sendMatchSessionEndResponseToPlayers(p1, p2, false);
            }

        }

    }

    private void handleRematchDecline(String username, Match match){
        match.declineRematch(username);
        if (match.isEnded()) {
            sendToClient.accept(username, new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                    new RematchResponse(false, "You declined rematch")));
            sendMatchSessionEndResponseToPlayer(username, false);
            var opponent = match.getRematchVoteOpponent(username);
            if ( match.getRematchVotes().get(opponent) == RematchVote.ACCEPTED  ){
                sendToClient.accept(opponent, new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                        new RematchResponse(false, "Rematch wasn't accepted")));
                sendMatchSessionEndResponseToPlayer(opponent, false);
                matchManager.endMatch(match.getMatchId());
            }

        }else{//midGame rematch toggle off
            sendToClient.accept(username, new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                    new RematchResponse(true, "You have set midgame rematch request off")));
            var opponent = match.getmidGameAsyncRematchVotesOpponent(username);
            sendToClient.accept(opponent, new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                    new RematchResponse(true, "Opponent set midgame rematch request off")));

        }
    }

    private void handleRematchAccept(String username, Match match) throws IllegalStateException{
            match.requestRematch(username);
            if (match.isEnded()) {
                sendToClient.accept(username, new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                        new RematchResponse(false, "Rematch request recorded")));

            }else{
                // ───── mid-game intent ON ─────
                sendToClient.accept(username, new NetPacket(
                        PacketType.REMATCH_RESPONSE,
                        "server",
                        new RematchResponse( true, "Rematch intent set")
                ));

                //  notify opponent ONLY if they haven't opted in yet
                String opponent = match.getmidGameAsyncRematchVotesOpponent(username);
                if (opponent != null && !match.getMidGameAsyncRematchVotes().get(opponent)) {
                    sendToClient.accept(opponent, new NetPacket(
                            PacketType.REMATCH_RESPONSE,
                            "server",
                            new RematchResponse(true, "Opponent rematch is on ")
                    ));
                }
            }
    }



    private void sendMatchSessionEndResponseToPlayers(String p1 , String p2, boolean isRematchOn){
        sendToClient.accept(p1, new NetPacket(PacketType.MATCH_SESSION_ENDED_RESPONSE, "server",
                new MatchSessionEndedResponse(isRematchOn)));
        sendToClient.accept(p2, new NetPacket(PacketType.MATCH_SESSION_ENDED_RESPONSE, "server",
                new MatchSessionEndedResponse(isRematchOn)));
    }

    private void sendMatchSessionEndResponseToPlayer(String p1 , boolean isRematchOn){
        sendToClient.accept(p1, new NetPacket(PacketType.MATCH_SESSION_ENDED_RESPONSE, "server",
                new MatchSessionEndedResponse(isRematchOn)));
    }

    public GameStateResponse getCurrentGame(String username) {
        return matchManager.getMatchByPlayer(username).map(Match::getCurrentState).orElse(null);
    }

    public void disconnectPlayer(String username){
        matchManager.playerDisconnected(username);

    }

    public void reconnectPlayer(String username){
        matchManager.playerReconnected(username);
    }

    // Broadcasting helpers
    private void broadcastMatchCreate(Match match) {
        NetPacket packet = new NetPacket(PacketType.GAME_START_RESPONSE, "server", match.getCurrentState());
        sendToClient.accept(match.getPlayer1(), packet);
        sendToClient.accept(match.getPlayer2(), packet);
    }

    private void broadcastMatchUpdate(Match match) {
        NetPacket packet = new NetPacket(PacketType.GAME_STATE_RESPONSE, "server", match.getCurrentState());
        sendToClient.accept(match.getPlayer1(), packet);
        sendToClient.accept(match.getPlayer2(), packet);
    }

    private void broadcastRegularMatchEnd(Match match, MatchEndReason endReason) {
        boolean draw = match.isDraw();
        String winner = draw ? null : match.getWinner();
        String loser  = draw ? null : match.getLoser();

        MatchEndReason p1Reason;
        MatchEndReason p2Reason;

        if (draw) {
            if (endReason == MatchEndReason.MID_GAME_REMATCH){//safety code in case we change the midgame rematch later
                p1Reason = p2Reason = MatchEndReason.MID_GAME_REMATCH;
            }else{
                p1Reason = p2Reason = MatchEndReason.DRAW;
            }
        } else if (winner.equals(match.getPlayer1())) {
            p1Reason = endReason.isWinType() ? endReason : MatchEndReason.WIN_NORMAL;
            p2Reason = endReason.correspondingLoss();
        } else {
            p1Reason = endReason.correspondingLoss();
            p2Reason = endReason.isWinType() ? endReason : MatchEndReason.WIN_NORMAL;
        }

        sendToClient.accept(match.getPlayer1(), new NetPacket(PacketType.GAME_END_RESPONSE, "server",
                new GameEndResponse(match.getCurrentState().board(), winner, loser, p1Reason, match.getPlayer2(),
                        match.getPlayer1(),
                        match.getPlayer2(),
                        Game.getWinCount()
                        )));

        sendToClient.accept(match.getPlayer2(), new NetPacket(PacketType.GAME_END_RESPONSE, "server",
                new GameEndResponse(match.getCurrentState().board(), winner, loser, p2Reason, match.getPlayer1(),
                        match.getPlayer1(),
                        match.getPlayer2(),
                        Game.getWinCount()
                )));
    }



    private void sendUpdatedStats(String username) {
        PlayerStatsResponse stats = persistence.getPlayerStats(username);
        sendToClient.accept(username, new NetPacket(PacketType.PLAYER_STATS_RESPONSE, "server", stats));
    }

    public boolean isPlayerInGame(String username) {
        return matchManager.isPlayerInMatch(username);
    }


    /**
     * Clears all pending invites involving either player (as sender or target)
     */
    public void clearInvitesForMatchPlayers(String player1, String player2) {
        synchronized (invitesLock) {
            // Remove any invites received by either player
            pendingInvites.remove(player1);
            pendingInvites.remove(player2);

            // Remove both players as senders from all other lists
            pendingInvites.forEach((target, inviteList) ->
                    inviteList.removeIf(inviter -> inviter.equals(player1) || inviter.equals(player2))
            );

            // Clean up empty lists
            pendingInvites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
    }

}
