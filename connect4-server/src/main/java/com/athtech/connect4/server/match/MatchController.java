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

    private final MatchManager matchManager;
    private final BiConsumer<String, NetPacket> sendToClient;
    private final LobbyController lobbyController;
    private final PersistenceManager persistence;
    // pending invites <targetUsername, list of fromUsername>
    private final Map<String, CopyOnWriteArrayList<String>> pendingInvites = new ConcurrentHashMap<>();
    private final Set<String> endedMatches = ConcurrentHashMap.newKeySet();//one shot game ends...

    public MatchController(ServerNetworkAdapter server , LobbyController lobbyController, PersistenceManager persistence) {
        this.matchManager = new MatchManagerImpl();
        this.sendToClient = server::sendToClient;
        this.lobbyController = lobbyController;
        this.persistence = persistence;
    }


    public void createMatch(String player1, String player2) {
        Match m = matchManager.createMatch(player1, player2);
        broadcastMatchCreate(m);
    }

    public void processMove(String username, MoveRequest move) {
        matchManager.getMatchByPlayer(username).ifPresentOrElse(match -> {
            boolean ok = match.makeMove(username, move);
            if (!ok) sendToClient.accept(username, new NetPacket(PacketType.MOVE_REJECTED_RESPONSE, "server",
                    new MoveRejectedResponse("Invalid move or not your turn",match.getCurrentPlayer())));
            else {
                if (!match.isFinished()){
                    broadcastMatchUpdate(match);
                }else{
                    endMatch(match);
                }

            }
        }, () -> sendToClient.accept(username, new NetPacket(PacketType.MOVE_REJECTED_RESPONSE, "server",
                new MoveRejectedResponse("No active match found. Are you in a game?",null))));
    }

    public void endMatch(Match match) {
        if (!match.markEnded()) return;

        boolean draw = match.isDraw();
        String winner = draw ? null : match.getWinner();

        persistence.recordMatchResult(
                match.getPlayer1(),
                match.getPlayer2(),
                draw,
                winner
        );

        // push updated stats to both players
        sendUpdatedStats(match.getPlayer1());
        sendUpdatedStats(match.getPlayer2());

        broadcastMatchEnd(match);
        matchManager.endMatch(match.getMatchId());
    }



    public void sendInvite(String fromUsername, String targetUsername) {

        if (!lobbyController.isUserLoggedIn(targetUsername)){

            sendToClient.accept(fromUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                    new InviteResponse(false, "Player is not online")));
            return;
        }

        if (!matchManager.getMatchByPlayer(targetUsername).isEmpty()) {
            System.out.println("sendInvite: user not in match triggered");
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

        if (accepted) createMatch(inviterUsername, targetUsername);
    }

    public InviteNotificationResponse[] getInvitationsFor(String targetUsername) {
        CopyOnWriteArrayList<String> invites = pendingInvites.get(targetUsername);
        if (invites == null || invites.isEmpty()) return new InviteNotificationResponse[0];

        return invites.stream()
                .map(InviteNotificationResponse::new)
                .toArray(InviteNotificationResponse[]::new);
    }

    // -----------------------
    // Rematch logic (flattened)
    // -----------------------
    public void sendRematchRequest(String username) {
        matchManager.getMatchByPlayer(username).ifPresent(Match -> {

            if (Match.isRematchTimedOut()) {
                Match.cancelRematch();
                sendToClient.accept(Match.getPlayer1(), new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                        new RematchResponse(false, "Rematch timed out")));
                sendToClient.accept(Match.getPlayer2(), new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                        new RematchResponse(false, "Rematch timed out")));
                return;
            }

            Match.requestRematch(username);

            String other = Match.getPlayer1().equals(username) ? Match.getPlayer2() : Match.getPlayer1();
            sendToClient.accept(other, new NetPacket(PacketType.REMATCH_NOTIFICATION_RESPONSE, "server",
                    new RematchNotificationResponse(username)));

            if (Match.canStartRematch()) {
                Match.resetRematchState();
                matchManager.endMatch(Match.getMatchId());
                matchManager.createMatch(Match.getPlayer1(), Match.getPlayer2());
            }
        });
    }

    public void processRematchDecision(String username, boolean accepted) {
        matchManager.getMatchByPlayer(username).ifPresent(Match -> {
            if (!accepted) {
                Match.cancelRematch();
                sendToClient.accept(Match.getPlayer1(), new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                        new RematchResponse(false, "Rematch declined")));
                sendToClient.accept(Match.getPlayer2(), new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                        new RematchResponse(false, "Rematch declined")));
            }
        });
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
        sendToClient.accept(
                username,
                new NetPacket(PacketType.PLAYER_STATS_RESPONSE, "server", stats)
        );
    }


}
