package com.athtech.connect4.server.net.invite;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.InviteDecisionResponse;
import com.athtech.connect4.protocol.payload.InviteNotificationResponse;
import com.athtech.connect4.protocol.payload.InviteResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class InviteController {

    // pending invites <targetUsername, list of fromUsername>
    private final Map<String, CopyOnWriteArrayList<String>> pendingGamingInvites = new ConcurrentHashMap<>();

    // deps from ServerNetworkAdapter (passed in)
    private final Function<String, Boolean> isLoggedIn;
    private final BiConsumer<String, NetPacket> sendToClient;
    private final BiConsumer<String, String> createMatch;

    public InviteController(
            Function<String, Boolean> isLoggedIn,
            BiConsumer<String, NetPacket> sendToClient,
            BiConsumer<String, String> createMatch
    ) {
        this.isLoggedIn = isLoggedIn;
        this.sendToClient = sendToClient;
        this.createMatch = createMatch;
    }

    // -------------------
    // Invite handling
    // -------------------
    public void sendInvite(String fromUsername, String targetUsername) {
        if (!Boolean.TRUE.equals(isLoggedIn.apply(targetUsername))) {
            sendToClient.accept(fromUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                    new InviteResponse(false, "Player not online")));
            return;
        }

        // get or create list of pending invites for target
        pendingGamingInvites.computeIfAbsent(targetUsername, k -> new CopyOnWriteArrayList<>())
                .add(fromUsername);

        sendToClient.accept(targetUsername, new NetPacket(PacketType.INVITE_NOTIFICATION_RESPONSE, "server",
                new InviteNotificationResponse(fromUsername)));//send invitation to target of invite
        sendToClient.accept(fromUsername, new NetPacket(PacketType.INVITE_RESPONSE, "server",
                new InviteResponse(true, null)));// send confirmation to inviter
    }

    public void processInviteDecision(String targetUsername, String inviterUsername, boolean accepted) {
        CopyOnWriteArrayList<String> invites = pendingGamingInvites.get(targetUsername);
        if (invites == null || !invites.contains(inviterUsername)) return;

        // remove the specific invite from the list
        invites.remove(inviterUsername);

        var resp = new InviteDecisionResponse(inviterUsername, targetUsername, accepted);
        sendToClient.accept(inviterUsername, new NetPacket(PacketType.INVITE_DECISION_RESPONSE, "server", resp));
        sendToClient.accept(targetUsername, new NetPacket(PacketType.INVITE_DECISION_RESPONSE, "server", resp));

        // remove the entry if no more invites left
        if (invites.isEmpty()) pendingGamingInvites.remove(targetUsername);

        if (accepted) createMatch.accept(inviterUsername, targetUsername);
    }

    public InviteNotificationResponse[] getInvitationsFor(String targetUsername) {
        CopyOnWriteArrayList<String> invites = pendingGamingInvites.get(targetUsername);
        if (invites == null || invites.isEmpty()) return new InviteNotificationResponse[0];

        return invites.stream()
                .map(InviteNotificationResponse::new)
                .toArray(InviteNotificationResponse[]::new);
    }
}
