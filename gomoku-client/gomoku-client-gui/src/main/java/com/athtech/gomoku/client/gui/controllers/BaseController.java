package com.athtech.gomoku.client.gui.controllers;
//STUDENTS-CODE-NUMBER : CSY-22117
import com.athtech.gomoku.client.gui.GomokuFXCommonToAllControllersData;
import com.athtech.gomoku.client.gui.GomokuFXNetworkHandler;
import com.athtech.gomoku.client.gui.GomokuFXViewNavigator;
import com.athtech.gomoku.protocol.payload.InfoResponse;

// All controllers extend this Base Class
public abstract class BaseController {

    protected GomokuFXViewNavigator navigator;
    protected GomokuFXNetworkHandler clientNetwork;
    protected GomokuFXCommonToAllControllersData data;

    // Initialization method.
    // Pass reference of:
    // - navigator (handles root switching)
    // - network (handles packet routing)
    // - shared data class
    public final void init(
            GomokuFXViewNavigator navigator,
            GomokuFXNetworkHandler network,
            GomokuFXCommonToAllControllersData data
    ) {
        this.navigator = navigator;
        this.clientNetwork = network;
        this.data = data;
        onInit();
    }

    /** Called once */
    protected void onInit() {}

    /** Called every time view becomes visible */
    public void onEnter() {}

    /** Called every time view is hidden */
    public void onLeave() {}

    public abstract void showInfo(InfoResponse infoResponse);
}
