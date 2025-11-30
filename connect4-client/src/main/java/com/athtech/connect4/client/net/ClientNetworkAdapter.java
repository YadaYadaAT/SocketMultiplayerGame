package com.athtech.connect4.client.net;

import com.athtech.connect4.protocol.messaging.NetPacket;

public interface ClientNetworkAdapter {
    void disconnect();
    void sendPacket(Object packet);
    void setListener(PacketListener listener);

    interface PacketListener {
        void onPacketReceived(NetPacket packet);
    }
}
