package com.athtech.connect4.client.net;

import com.athtech.connect4.protocol.messaging.NetPacket;

import java.io.IOException;

public interface ClientNetworkAdapter {
    void disconnect();
    void sendPacket(NetPacket packet);
    void reconnect() throws IOException;
    void setListener(PacketListener listener);
    void setConnectionLostListener(Runnable callback);

    interface PacketListener {
        void onPacketReceived(NetPacket packet);
    }
}
