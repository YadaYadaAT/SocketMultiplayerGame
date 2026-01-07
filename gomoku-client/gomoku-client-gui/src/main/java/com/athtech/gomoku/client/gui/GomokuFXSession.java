package com.athtech.gomoku.client.gui;

import com.athtech.gomoku.client.net.ClientNetworkAdapter;

public final class GomokuFXSession {

    public GomokuFXViewNavigator navigator;
    public GomokuFXCommonToAllControllersData data;
    public GomokuFXNetworkHandler networkHandler;

    public GomokuFXSession(
            GomokuFXViewNavigator navigator,
            GomokuFXCommonToAllControllersData data,
            GomokuFXNetworkHandler networkHandler
    ) {
        this.navigator = navigator;
        this.data = data;
        this.networkHandler = networkHandler;
    }

    public void destroy(ClientNetworkAdapter cna) {
        // VERY IMPORTANT: detach listener
        cna.setListener(null);

        navigator = null;
        data = null;
        networkHandler = null;
    }
}
