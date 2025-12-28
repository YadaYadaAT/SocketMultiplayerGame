package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.*;
import com.athtech.connect4.server.net.LobbyController;
import com.athtech.connect4.server.net.ServerNetworkAdapter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public class MatchController {

    private final MatchManager matchManager;
    private final BiConsumer<String, NetPacket> sendToClient;
    private final LobbyController lobbyController;
    // pending invites <targetUsername, list of fromUsername>
    private final Map<String, CopyOnWriteArrayList<String>> pendingInvites = new ConcurrentHashMap<>();

    public MatchController(ServerNetworkAdapter server , LobbyController lobbyController) {
        this.matchManager = new MatchManagerImpl();
        this.sendToClient = server::sendToClient;
        this.lobbyController = lobbyController;
    }

    public void createMatch(String player1, String player2) {
        match m = matchManager.createMatch(player1, player2);
        broadcastMatchCreate(m);
    }

    public void endMatch(String matchId) {
        matchManager.getMatch(matchId).ifPresent(this::broadcastMatchEnd);
        matchManager.endMatch(matchId);
    }


    public void processMove(String username, MoveRequest move) {
        matchManager.getMatchByPlayer(username).ifPresentOrElse(match -> {
            boolean ok = match.makeMove(username, move);
            if (!ok) sendToClient.accept(username, new NetPacket(PacketType.MOVE_REJECTED_RESPONSE, "server",
                    new MoveRejectedResponse("Invalid move or not your turn")));
            else {
                broadcastMatchUpdate(match);
                if (match.isFinished()) endMatch(match.getMatchId());
            }
        }, () -> sendToClient.accept(username, new NetPacket(PacketType.MOVE_REJECTED_RESPONSE, "server",
                new MoveRejectedResponse("No active match found. Are you in a game?"))));
    }


    public void sendInvite(String fromUsername, String targetUsername) {
        System.out.println("sendInvite: attempting Send invite");
        if (!lobbyController.isUserLoggedIn(targetUsername)){
            System.out.println("user is not online triggered");
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
        matchManager.getMatchByPlayer(username).ifPresent(match -> {

            if (match.isRematchTimedOut()) {
                match.cancelRematch();
                sendToClient.accept(match.getPlayer1(), new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                        new RematchResponse(false, "Rematch timed out")));
                sendToClient.accept(match.getPlayer2(), new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                        new RematchResponse(false, "Rematch timed out")));
                return;
            }

            match.requestRematch(username);

            String other = match.getPlayer1().equals(username) ? match.getPlayer2() : match.getPlayer1();
            sendToClient.accept(other, new NetPacket(PacketType.REMATCH_NOTIFICATION_RESPONSE, "server",
                    new RematchNotificationResponse(username)));

            if (match.canStartRematch()) {
                match.resetRematchState();
                matchManager.endMatch(match.getMatchId());
                matchManager.createMatch(match.getPlayer1(), match.getPlayer2());
            }
        });
    }

    public void processRematchDecision(String username, boolean accepted) {
        matchManager.getMatchByPlayer(username).ifPresent(match -> {
            if (!accepted) {
                match.cancelRematch();
                sendToClient.accept(match.getPlayer1(), new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                        new RematchResponse(false, "Rematch declined")));
                sendToClient.accept(match.getPlayer2(), new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                        new RematchResponse(false, "Rematch declined")));
            }
        });
    }

    public GameStateResponse getCurrentGame(String username) {
        return matchManager.getMatchByPlayer(username).map(match::getCurrentState).orElse(null);
    }


    // -----------------------
    // Broadcasting helpers
    // -----------------------
    private void broadcastMatchCreate(match match) {
        NetPacket packet = new NetPacket(PacketType.GAME_START_RESPONSE, "server", match.getCurrentState());
        sendToClient.accept(match.getPlayer1(), packet);
        sendToClient.accept(match.getPlayer2(), packet);
    }

    private void broadcastMatchUpdate(match match) {
        NetPacket packet = new NetPacket(PacketType.GAME_STATE_RESPONSE, "server", match.getCurrentState());
        sendToClient.accept(match.getPlayer1(), packet);
        sendToClient.accept(match.getPlayer2(), packet);
    }

    private void broadcastMatchEnd(match match) {
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
}
