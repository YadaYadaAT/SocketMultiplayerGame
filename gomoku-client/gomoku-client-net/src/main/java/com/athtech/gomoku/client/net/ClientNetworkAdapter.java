package com.athtech.gomoku.client.net;

import com.athtech.gomoku.protocol.messaging.NetPacket;

public interface ClientNetworkAdapter {
    void disconnect();
    void sendPacket(NetPacket packet);

    NetState getState();

    void requestResync();

    void onResyncFinished();
    void setConNotifier(ConnectionNotificationListener conNotifier);
    void setListener(PacketListener listener);

    void setSyncAndConInputBlocker( SyncAndConInputBlockerInter sib);
    void setSyncAndConInputUnblocker( SyncAndConInputUnblockerInter siu);

    void updateCredentials(String username, String relogCode);

    interface PacketListener {
        void onPacketReceived(NetPacket packet);
    }

    interface ConnectionNotificationListener{
        void connectionNotifer(String msg);
    }

    interface SyncAndConInputBlockerInter {
        void syncAndConInputBlocker();
    }

    interface SyncAndConInputUnblockerInter {
        void syncAndConInputUnblocker();
    }


}
