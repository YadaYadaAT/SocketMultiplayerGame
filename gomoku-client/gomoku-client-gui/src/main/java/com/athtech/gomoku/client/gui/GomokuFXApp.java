package com.athtech.gomoku.client.gui;


import com.athtech.gomoku.client.net.ClientNetworkAdapterImpl;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;

public class GomokuFXApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Gomoku FX Client");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
