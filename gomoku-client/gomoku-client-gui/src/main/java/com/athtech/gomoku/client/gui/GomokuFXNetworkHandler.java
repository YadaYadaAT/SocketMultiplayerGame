package com.athtech.gomoku.client.gui;

import com.athtech.gomoku.client.gui.controllers.*;
import com.athtech.gomoku.client.gui.enums.View;
import com.athtech.gomoku.client.net.ClientNetworkAdapter;

import com.athtech.gomoku.client.net.NetState;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.*;
import javafx.application.Platform;

import java.util.Map;

public class GomokuFXNetworkHandler {
    private final ClientNetworkAdapter cna;
    private final GomokuFXCommonToAllControllersData ctAllControllersData;
    private WrapperController wrapperCtrl;
    private LoginController loginCtrl;
    private SignupController signupCtrl;
    private LobbyController lobbyCtrl;
    private GameController gameCtrl;


    public void setGameCtrl(GameController gameCtrl) {
        this.gameCtrl = gameCtrl;
    }

    public void setLobbyCtrl(LobbyController lobbyCtrl) {
        this.lobbyCtrl = lobbyCtrl;
    }

    public void setWrapperCtrl(WrapperController wrapperCtrl) {
        this.wrapperCtrl = wrapperCtrl;
    }

    public void setLoginCtrl(LoginController loginCtrl) {
        this.loginCtrl = loginCtrl;
    }

    public void setSignupCtrl(SignupController signupCtrl) {
        this.signupCtrl = signupCtrl;
    }

    public void initCallbackHandler(){
        cna.setListener(this::handleServerPacket);
    }

    public GomokuFXNetworkHandler(ClientNetworkAdapter networkAdapter, GomokuFXCommonToAllControllersData ctAllControllersData) {
        this.cna = networkAdapter;
        this.ctAllControllersData = ctAllControllersData;
    }


    public synchronized void sendPacket(NetPacket packet){
//        long sentAt = System.currentTimeMillis();//TODO last of all when we have ready all the FX inputs...
//        if (cna.getState() == NetState.CONNECTED) {
//            new Thread(() -> {
//                try {
//                    Thread.sleep(12_000);
//                } catch (InterruptedException ignored) {}
//
//                if (ctAllControllersData.getLastServerActivity() < sentAt && cna.getState() == NetState.CONNECTED) {
//                    wrapperCtrl.setConnectionStatus("\uD83D\uDD0C No server activity detected. Attempting resync...");
//                    if (ctAllControllersData.getUsername() == null || ctAllControllersData.getRelogCode() == null) return;
//                    cna.requestResync(ctAllControllersData.getUsername(), ctAllControllersData.getRelogCode());
//                }
//            }).start();
//        }

        cna.sendPacket(packet);
    }

    public synchronized void updateCredentials(String username,String relogcode){
        cna.updateCredentials(username,relogcode);
    }

    public synchronized void sendHandshake(){
        cna.sendPacket(new NetPacket(PacketType.HANDSHAKE_REQUEST,"", new HandshakeRequest()));
    }



    private void handleServerPacket(NetPacket packet) {
        ctAllControllersData.setLastServerActivity(System.currentTimeMillis());
//        if (sessionClosing && packet.type() != PacketType.LOGOUT_RESPONSE) return;
        switch (packet.type()) {
            case LOGIN_RESPONSE -> onLoginResponse(packet);
            case SIGNUP_RESPONSE -> signupCtrl.onSignupResponse(packet);
            case LOGOUT_RESPONSE -> onLogoutResponse(packet);
            case LOBBY_PLAYERS_RESPONSE -> lobbyCtrl.onLobbyPlayersResponse(packet);
            case GAME_QUIT_RESPONSE -> gameCtrl.onGameQuitResponse(packet);
            case PLAYER_STATS_RESPONSE -> lobbyCtrl.onPlayerStatsResponse(packet);
            case INVITE_RESPONSE -> lobbyCtrl.onInviteResponse(packet);
            case INVITE_NOTIFICATION_RESPONSE -> lobbyCtrl.onInviteNotificationResponse(packet);
            case INVITE_DECISION_RESPONSE -> onInviteDecisionResponse(packet);
            case LOBBY_CHAT_MESSAGE_RESPONSE -> lobbyCtrl.onLobbyChatMessageResponse(packet);
            case HANDSHAKE_RESPONSE ->  wrapperCtrl.onHandshakeResponse(packet);
            case GAME_START_RESPONSE -> gameCtrl.onGameStartResponse(packet);
            case GAME_STATE_RESPONSE -> gameCtrl.onGameStateResponse(packet);
            case GAME_END_RESPONSE ->  gameCtrl.onGameEndResponse(packet);
            case REMATCH_RESPONSE ->  gameCtrl.onRematchResponse(packet);
            case MATCH_SESSION_ENDED_RESPONSE ->  gameCtrl.onMatchSessionEndedResponse(packet);
            case MOVE_REJECTED_RESPONSE -> gameCtrl.onMoveRejectedResponse(packet);
            case PLAYER_INACTIVITY_WARNING_RESPONSE -> gameCtrl.onPlayerInactivityWarningResponse(packet);
            case RESYNC_RESPONSE -> onResyncResponse(packet);
            case ERROR_MESSAGE_RESPONSE -> wrapperCtrl.onErrorMessageResponse(packet);
            case INFO_RESPONSE -> onInfoResponse(packet);
            case GAME_QUIT_NOTIFICATION_RESPONSE -> gameCtrl.onGameQuitNotification(packet);
            case PLAYER_DISCONNECTED_NOTIFICATION_RESPONSE -> gameCtrl.onPlayerDisconnectedNotificationResponse(packet);
            case PLAYER_RECONNECTED_NOTIFICATION_RESPONSE ->  gameCtrl.onPlayerReconnectedNotificationResponse(packet);
            case PLAYER_RECONNECTED_RESPONSE -> gameCtrl.onPlayerReconnectedResponse(packet);
            default -> System.out.println("Debugging only ; Unhandled packet: " + packet.type());
        }
    }

    private void onLoginResponse(NetPacket packet){

        loginCtrl.onLoginResponse(packet);
        lobbyCtrl.onLoginResponse(packet);//extract pending invites from response...
        gameCtrl.onLoginResponse(packet); //reconnection; populate and modify,preset up to avoid triggers of regular leave/enter
        wrapperCtrl.onLoginResponse(packet);

    }

    private void onInfoResponse(NetPacket packet){
        if (!(packet.payload() instanceof InfoResponse(String msg))){
            return;
        }
        InfoResponse infoResponse = (InfoResponse) packet.payload();
        loginCtrl.showInfo(infoResponse);
        signupCtrl.showInfo(infoResponse);
        lobbyCtrl.showInfo(infoResponse);
    }

    private void onLogoutResponse(NetPacket packet) {
        lobbyCtrl.onLogoutResponse(packet);//
        wrapperCtrl.onLogoutResponse(packet);
    }

    private void onInviteDecisionResponse(NetPacket packet){
        lobbyCtrl.onInviteDecisionResponse(packet);
        wrapperCtrl.onInviteDecisionResponse(packet);
    }



    private void onResyncResponse(NetPacket packet) {
        ResyncResponse resp = (ResyncResponse) packet.payload();

        if (!resp.success()) {
            wrapperCtrl.setConnectionStatus("Resync attempt rejected: " + resp.message());
//            resetSessionState(); < - need to find a way to reset everything ..
            return;
        }
        lobbyCtrl.onResyncResponse(packet);
        gameCtrl.onResyncResponse(packet);
        loginCtrl.onResyncResponse(packet);
        wrapperCtrl.onResyncResponse(packet);
        cna.onResyncFinished();
        wrapperCtrl.setConnectionStatus(resp.message());
    }



    public void setSession(GomokuFXSession session) {
        this.session = session;
    }






}
