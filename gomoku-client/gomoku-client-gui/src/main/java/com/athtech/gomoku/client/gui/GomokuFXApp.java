package com.athtech.gomoku.client.gui;

import com.athtech.gomoku.client.gui.controllers.LoginController;
import com.athtech.gomoku.client.gui.enums.View;
import com.athtech.gomoku.client.net.ClientNetworkAdapterImpl;
import javafx.application.Application;
import javafx.stage.Stage;

public class GomokuFXApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        var viewNavigator = new GomokuFXViewNavigator(stage);
        viewNavigator.preload(View.LOGIN);
        var data = new GomokuFXCommonToAllControllersData();
        var cna = new ClientNetworkAdapterImpl("localhost", 999);
        var networkHandler = new GomokuFXNetworkHandler(cna,data);
        networkHandler.setLoginCtrl((LoginController) viewNavigator.getController(View.LOGIN));
        viewNavigator.initControllers(viewNavigator,networkHandler,data);
        networkHandler.initCallbackHandler();

        // Set initial scene root and size
        stage.setScene(new javafx.scene.Scene(viewNavigator.getRoot(View.LOGIN)));
        stage.setTitle("YadaYada Gomoku 2026");
        stage.setWidth(800);
        stage.setHeight(600);
        stage.show();

    }

    public static void main(String[] args) {
        launch(args);
    }
}
