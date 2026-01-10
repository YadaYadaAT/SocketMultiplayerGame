package com.athtech.gomoku.client.net;

import com.athtech.gomoku.protocol.messaging.NetPacket;

// One Adapter per user / instance of a client
public interface ClientNetworkAdapter {

    void disconnect(); // Triggered on app exit

    void sendPacket(NetPacket packet); // handles all packets to be sent to server

    NetState getState(); // Get network state

    void requestResync(); // called by send thread

    void onResyncFinished(); // indicate resync process end

    void setConNotifier(ConnectionNotificationListener conNotifier); // Hooks callback method to javafx / notification handler

    void setListener(PacketListener listener); // Sets listener method to be used in listen loop - called by main thread

    void setSyncAndConInputBlocker( SyncAndConInputBlockerInter sib); // Sets the method to be called to handle input blocks

    void setSyncAndConInputUnblocker( SyncAndConInputUnblockerInter siu); // Sets the method to be called to handle input unblocking

    void updateCredentials(String username, String relogCode); // locally update user credentials

    interface PacketListener {
        void onPacketReceived(NetPacket packet); // this interface ensures decoupling of network processes and the various ui implementations. It is a callback contract that basically says: "Anyone who wants to receive network packets must provide a method that handles them"
    }

    interface ConnectionNotificationListener{
        void connectionNotifer(String msg); // as above, regarding ui notifications
    }

    interface SyncAndConInputBlockerInter {
        void syncAndConInputBlocker(); // block ui inputs
    }

    interface SyncAndConInputUnblockerInter {
        void syncAndConInputUnblocker(); // unblock ui inputs
    }


}
