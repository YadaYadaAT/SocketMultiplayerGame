package com.athtech.gomoku.client.net;

public interface NetworkLifecycleListener {
    void onConnectionLost();
    void onReconnectStarted();
    void onReconnectFailed();
    void onReconnectSucceeded();
}
