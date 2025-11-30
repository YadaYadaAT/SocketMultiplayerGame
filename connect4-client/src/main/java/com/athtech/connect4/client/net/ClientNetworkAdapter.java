package com.athtech.connect4.client.net;

import com.athtech.connect4.protocol.messaging.NetPacket;

public interface ClientNetworkAdapter {
    void connect(String host, int port);
    void disconnect();
    void sendPacket(NetPacket packet);
    void setListener(PacketListener listener);

    interface PacketListener {
        void onPacketReceived(NetPacket packet);
    }
}
