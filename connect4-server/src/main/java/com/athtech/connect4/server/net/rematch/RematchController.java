package com.athtech.connect4.server.net.rematch;

import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.*;
import com.athtech.connect4.server.match.match;
import com.athtech.connect4.server.match.MatchManager;

import java.util.function.BiConsumer;

public class RematchController {

    private final MatchManager matchManager;
    private final BiConsumer<String, NetPacket> sendToClient;

    public RematchController(
            MatchManager matchManager,
            BiConsumer<String, NetPacket> sendToClient
    ) {
        this.matchManager = matchManager;
        this.sendToClient = sendToClient;
    }

    public void sendRematchRequest(String username) {
        matchManager.getMatchByPlayer(username).ifPresent(match -> {

            if (match.isRematchTimedOut()) {
                match.cancelRematch();

                sendToClient.accept(match.getPlayer1(),
                        new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                                new RematchResponse(false, "Rematch timed out")));
                sendToClient.accept(match.getPlayer2(),
                        new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                                new RematchResponse(false, "Rematch timed out")));
                return;
            }

            match.requestRematch(username);

            String other = match.getPlayer1().equals(username)
                    ? match.getPlayer2()
                    : match.getPlayer1();

            sendToClient.accept(other, new NetPacket(
                    PacketType.REMATCH_NOTIFICATION_RESPONSE,
                    "server",
                    new RematchNotificationResponse(username)
            ));

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

                sendToClient.accept(match.getPlayer1(),
                        new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                                new RematchResponse(false, "Rematch declined")));
                sendToClient.accept(match.getPlayer2(),
                        new NetPacket(PacketType.REMATCH_RESPONSE, "server",
                                new RematchResponse(false, "Rematch declined")));
            }
        });
    }
}
