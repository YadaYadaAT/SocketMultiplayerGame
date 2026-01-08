package com.athtech.gomoku.client.gui.controllers;

import com.athtech.gomoku.client.gui.enums.View;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.payload.*;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class WrapperController extends BaseController{

    @FXML private Label lblUsername;
    @FXML private Label lblError;
    @FXML private Label lblConnection;
    @FXML
    private StackPane contentPane;
    @FXML private Label lblClock;
    @FXML
    private HBox headerBar;

    private Timeline clockTimeline;
    private Pane inputBlocker;

    @Override
    public void onLeave() {
        if (clockTimeline != null) {
            clockTimeline.stop();
            clockTimeline = null;
        }
    }

    @FXML
    public void initialize() {
        startClock();

    }

    public void setUsername(String name) {
        Platform.runLater(() -> {
         lblUsername.setText(name);
        });
    }

    public void setConnectionStatus(String status) {
        Platform.runLater(() -> {
         lblConnection.setText(status);
        });
    }

    public void setLblError(String errMsg) {
        Platform.runLater(() -> {
            lblError.setText(errMsg);
        });
    }

    public StackPane getContentPane() {
        return contentPane;
    }

    public void onErrorMessageResponse(NetPacket packet) {
        ErrorMessageResponse err = (ErrorMessageResponse) packet.payload();
        Platform.runLater(() -> {
        lblConnection.setText("ERROR: " + err.message());
        });
    }

    public void onInviteDecisionResponse(NetPacket packet) {
        InviteDecisionResponse resp = (InviteDecisionResponse) packet.payload();

        Platform.runLater(() -> {
            if (resp.accepted()) {
                navigator.goTo(View.GAME);
            }
        });
    }

    public void onLoginResponse(NetPacket packet) {
        LoginResponse resp = (LoginResponse) packet.payload();
        if (resp.success()) {
            Platform.runLater(() -> {
                setUsername(resp.message());

                if(resp.currentGameState() !=null){
                    navigator.goTo(View.GAME);
                }else{
                    navigator.goTo(View.LOBBY);
                }
            });
        }
    }

    public void onResyncResponse(NetPacket netPacket){

    }

    public void onLogoutResponse(NetPacket packet) {
        LogoutResponse resp = (LogoutResponse) packet.payload();

            if (resp.success()){
                Platform.runLater(() -> {
                    setUsername("");
                    data.reset();
                    navigator.goTo(View.LOGIN);
                });
            }

    }

    public void onHandshakeResponse(NetPacket packet){
        HandshakeResponse resp = (HandshakeResponse) packet.payload();
        Platform.runLater(() -> {
            lblConnection.setText(resp.msg());
        });
    }



    @Override
    public void showInfo(InfoResponse infoResponse) {

    }

    private void startClock() {
        clockTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e ->
                        lblClock.setText("⏰ " + LocalTime.now().withNano(0))
                )
        );
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();
    }

    public void dispose() {
        if (clockTimeline != null) {
            clockTimeline.stop();
            clockTimeline = null;
        }
    }


    public void blockInput() {
        if (inputBlocker != null) return;
        Platform.runLater(() ->{
        inputBlocker = new Pane();
        inputBlocker.setStyle("-fx-background-color: rgba(0,0,0,0.50);");

        inputBlocker.setPickOnBounds(true); // capture all clicks
        inputBlocker.setMouseTransparent(false); // block interaction

        // make it resize automatically with contentPane
        inputBlocker.prefWidthProperty().bind(contentPane.widthProperty());
        inputBlocker.prefHeightProperty().bind(contentPane.heightProperty());

        contentPane.getChildren().add(inputBlocker);
        });



    }

    public void unblockInput() {
        if (inputBlocker == null) return;
        Platform.runLater(() ->{
        contentPane.getChildren().remove(inputBlocker);
        inputBlocker = null;
        });
    }

    public void showHeader(boolean show) {
        headerBar.setVisible(show);
        headerBar.setManaged(show);
    }

    @FXML
    public void handleExit() {
        Platform.exit();
    }

}
