package com.athtech.gomoku.client.net;

import com.athtech.gomoku.protocol.messaging.NetPacket;

public interface ClientNetworkAdapter {
    void disconnect();
    void sendPacket(NetPacket packet);

    NetState getState();

    void requestResync(String username, String relogCode);

    void onResyncFinished();

    void setListener(PacketListener listener);
    void updateCredentials(String username, String relogCode);

    interface PacketListener {
        void onPacketReceived(NetPacket packet);
    }
}
