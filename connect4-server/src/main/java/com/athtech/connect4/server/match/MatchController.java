package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.*;
import com.athtech.connect4.server.net.LobbyController;
import com.athtech.connect4.server.net.ServerNetworkAdapter;
import com.athtech.connect4.server.persistence.PersistenceManager;

import java.util.Map;
import java.util.Set;
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
    private final Set<String> endedMatches = ConcurrentHashMap.newKeySet();

    public MatchController(ServerNetworkAdapter server, LobbyController lobbyController, PersistenceManager persistence) {
        this.lobbyController = lobbyController;
        this.matchManager = new MatchManagerImpl();
        this.sendToClient = server::sendToClient;
        this.persistence = persistence;
    }

    // -----------------------
    // Match creation
    // -----------------------
    public void createMatch(String player1, String player2) {
        try {
            Match match = matchManager.createMatch(player1, player2);
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
            if (!ok) sendToClient.accept(username, new NetPacket(PacketType.MOVE_REJECTED_RESPONSE, "server",
                    new MoveRejectedResponse("Invalid move or not your turn", match.getCurrentPlayer())));
            else {
                if (!match.isFinished()) broadcastMatchUpdate(match);
                else endMatch(match);
            }
        }, () -> sendToClient.accept(username, new NetPacket(PacketType.MOVE_REJECTED_RESPONSE, "server",
                new MoveRejectedResponse("No active match found. Are you in a game?", null))));
    }

    public void endMatch(Match match) {
        if (!match.markEnded()) return;
        match.markEnded();
        boolean draw = match.isDraw();
        String winner = draw ? null : match.getWinner();

        persistence.recordMatchResult(match.getPlayer1(), match.getPlayer2(), draw, winner);
        sendUpdatedStats(match.getPlayer1());
        sendUpdatedStats(match.getPlayer2());

        broadcastMatchEnd(match);

    }

    // -----------------------
    // Invites
    // -----------------------
    public void sendInvite(String fromUsername, String targetUsername) {
        if (!lobbyController.isUserLoggedIn(targetUsername)) {
            sendToClient.accept(fromUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                    new InviteResponse(false, "Player is not online")));
            return;
        }

        if (matchManager.getMatchByPlayer(targetUsername).isPresent()) {
            sendToClient.accept(fromUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                    new InviteResponse(false, "Player not available")));
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

    public void sendRematchRequest(String username, boolean consent) {

        matchManager.getEndedMatchByPlayer(username).ifPresent(match -> {

            // User decision
            if (!consent) {
                match.declineRematch(username);

                sendToClient.accept(username, new NetPacket(
                        PacketType.REMATCH_RESPONSE,
                        "server",
                        new RematchResponse(false, "You declined rematch")
                ));
                sendMatchSessionEndResponseToPlayer(username,false);

            } else {
                try {
                    match.requestRematch(username);
                    sendToClient.accept(username, new NetPacket(
                            PacketType.REMATCH_RESPONSE,
                            "server",
                            new RematchResponse(true, "Rematch request recorded")
                    ));

                } catch (IllegalStateException e) {
                    // Other player already declined / left or any other case that request rematch cant happen
                    sendToClient.accept(username, new NetPacket(
                            PacketType.REMATCH_RESPONSE,
                            "server",
                            new RematchResponse(false, e.getMessage())
                    ));

                    sendMatchSessionEndResponseToPlayer(username,false);
                    // Garbage collect empty match
                    if (match.getActivePlayers().isEmpty()) {
                        matchManager.endMatch(match.getMatchId());
                    }
                    return;
                }
            }



            // Both accepted → create rematch
            if (match.isRematchReady()) {
                String p1 = match.getPlayer1();
                String p2 = match.getPlayer2();
                try {
                    String matchId = match.getMatchId();
                    matchManager.endMatch(matchId);
                    Match newMatch = matchManager.createMatch(p1, p2);
                    sendMatchSessionEndResponseToPlayers(p1 ,p2,true);
                    broadcastMatchCreate(newMatch);

                } catch (IllegalStateException e) {
                    sendMatchSessionEndResponseToPlayers(p1 ,p2,false);
                }
            }
        });
    }


    private void sendMatchSessionEndResponseToPlayers(String p1 , String p2, boolean isRematchOn){
        sendToClient.accept(p1, new NetPacket(PacketType.MATCH_SESSION_ENDED_RESPONSE,
                "server",
                new MatchSessionEndedResponse(isRematchOn)
        ));

        sendToClient.accept(p2, new NetPacket(PacketType.MATCH_SESSION_ENDED_RESPONSE,
                "server",
                new MatchSessionEndedResponse(isRematchOn)
        ));
    }

    private void sendMatchSessionEndResponseToPlayer(String p1 , boolean isRematchOn){
        sendToClient.accept(p1, new NetPacket(PacketType.MATCH_SESSION_ENDED_RESPONSE,
                "server",
                new MatchSessionEndedResponse(isRematchOn)
        ));
    }

    public GameStateResponse getCurrentGame(String username) {
        return matchManager.getMatchByPlayer(username).map(Match::getCurrentState).orElse(null);
    }

    // -----------------------
    // Broadcasting helpers
    // -----------------------
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

    private void broadcastMatchEnd(Match match) {
        GameEndResponse resp1 = new GameEndResponse(match.getCurrentState().board(),
                match.isDraw() ? null : match.getWinner(),
                match.isDraw() ? null : match.getLoser(),
                match.isDraw() ? "Draw" : "Win/Loss",
                match.getPlayer2());
        GameEndResponse resp2 = new GameEndResponse(match.getCurrentState().board(),
                match.isDraw() ? null : match.getWinner(),
                match.isDraw() ? null : match.getLoser(),
                match.isDraw() ? "Draw" : "Win/Loss",
                match.getPlayer1());
        sendToClient.accept(match.getPlayer1(), new NetPacket(PacketType.GAME_END_RESPONSE, "server", resp1));
        sendToClient.accept(match.getPlayer2(), new NetPacket(PacketType.GAME_END_RESPONSE, "server", resp2));
    }

    private void sendUpdatedStats(String username) {
        PlayerStatsResponse stats = persistence.getPlayerStats(username);
        sendToClient.accept(username, new NetPacket(PacketType.PLAYER_STATS_RESPONSE, "server", stats));
    }
}
