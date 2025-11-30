package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.messaging.NetPacket;

public interface MatchManager {
    void createMatch(String player1Id, String player2Id);
    void handleMove(String matchId, NetPacket movePacket);
    void endMatch(String matchId);
}
