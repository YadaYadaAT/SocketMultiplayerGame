package com.athtech.connect4.client.net;

import com.athtech.connect4.protocol.messaging.NetPacket;

import java.io.IOException;

public interface ClientNetworkAdapter {
    void disconnect();
    void sendPacket(NetPacket packet);

    NetState getState();

    void requestResync(String username, String relogCode);

    void onResyncFinished();

    void setListener(PacketListener listener);

    interface PacketListener {
        void onPacketReceived(NetPacket packet);
    }
}
