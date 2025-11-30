package com.athtech.connect4.server.net;

import com.athtech.connect4.protocol.messaging.NetPacket;

public interface ServerNetworkAdapter {
    void startServer(int port);
    void stopServer();
    void sendToClient(String clientId, NetPacket packet);
}
