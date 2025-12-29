package com.athtech.connect4.client.net;

import com.athtech.connect4.protocol.messaging.NetPacket;

public interface ClientNetworkAdapter {
    void disconnect();
    void sendPacket(NetPacket packet);
    void setListener(PacketListener listener);
    void setConnectionLostListener(Runnable callback);
    boolean attemptReconnectWithRelogCode(String username, String relogCode);

    interface PacketListener {
        void onPacketReceived(NetPacket packet);
    }
}
