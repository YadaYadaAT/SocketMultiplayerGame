package com.athtech.gomoku.client.gui;
//STUDENTS-CODE-NUMBER : CSY-22117
import com.athtech.gomoku.client.gui.controllers.*;
import com.athtech.gomoku.client.gui.enums.View;
import com.athtech.gomoku.client.net.ClientNetworkAdapter;

import com.athtech.gomoku.client.net.NetState;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class GomokuFXNetworkHandler {
    private final ClientNetworkAdapter cna;
    private GomokuFXCommonToAllControllersData ctAllControllersData;
    private WrapperController wrapperCtrl;
    private LoginController loginCtrl;
    private SignupController signupCtrl;
    private LobbyController lobbyCtrl;
    private GameController gameCtrl;
    private Stage stage;
    private volatile boolean sessionClosing = false;
    private volatile long lastServerActivity = System.currentTimeMillis();

    public GomokuFXNetworkHandler(ClientNetworkAdapter networkAdapter
            , GomokuFXCommonToAllControllersData ctAllControllersData
            , Stage stage) {
        this.cna = networkAdapter;
        this.ctAllControllersData = ctAllControllersData;
        this.stage = stage;
    }

    // Pass all hook methods to the network adapter
    public void initCallbackHandler(){
        cna.setConNotifier(this::conNotifier);//Order matters!
        cna.setListener(this::handleServerPacket);
        cna.setSyncAndConInputBlocker(this::syncAndConInputBlocker);
        cna.setSyncAndConInputUnblocker(this::syncAndConInputUnblocker);
    }

    public void conNotifier(String msg){
        if (wrapperCtrl!=null) {
            wrapperCtrl.setConnectionStatus(msg);
        }
    }

    public void syncAndConInputBlocker(){
        if (wrapperCtrl!=null){
            wrapperCtrl.blockInput();
        }

    }

    public void syncAndConInputUnblocker(){
        if (wrapperCtrl!=null){
            wrapperCtrl.unblockInput();
        }
    }

    // Sends packets to server using the sendPacket() method of ClientNetworkAdapter
    // One centralized method to handle packet sending
    public synchronized void sendPacket(NetPacket packet){
        long sentAt = System.currentTimeMillis();

        if (cna.getState() == NetState.CONNECTED) {
            new Thread(() -> {
                try {
                    Thread.sleep(12_000); // Allow some time to pass
                } catch (InterruptedException ignored) {}

                if (lastServerActivity < sentAt) { // Check if resync is required (most recent server activity should not be older than the time the client has sent the most recent packet)
                    wrapperCtrl.setConnectionStatus("\uD83D\uDD0C No server activity detected. Attempting resync...");
                    syncAndConInputBlocker();
                    if (ctAllControllersData.getUsername() == null || ctAllControllersData.getRelogCode() == null) return;
                    cna.requestResync();
                }
            }).start();
        }

        if(packet.payload() instanceof LogoutRequest){ // Check if user wants to log out and close the session
         sessionClosing = true;
        }
        cna.sendPacket(packet); // Otherwise forward the packet
    }

    public synchronized void updateCredentials(String username,String relogcode){
        cna.updateCredentials(username,relogcode);
    }

    public synchronized void sendHandshake(){
        cna.sendPacket(new NetPacket(PacketType.HANDSHAKE_REQUEST,"", new HandshakeRequest()));
    }

    // Manages incoming packets
    private void handleServerPacket(NetPacket packet) {
        lastServerActivity = System.currentTimeMillis();
        // Ensure that resync or logout is not required
        if (sessionClosing && packet.type() != PacketType.LOGOUT_RESPONSE
                && packet.type()!=PacketType.RESYNC_RESPONSE){
            return;
        }

        // else go through all the different packet types and forward it to the appropriate method
        // these methods live in the appropriate controllers
        switch (packet.type()) {
            case LOGIN_RESPONSE -> onLoginResponse(packet);
            case SIGNUP_RESPONSE -> signupCtrl.onSignupResponse(packet);
            case LOGOUT_RESPONSE -> {
                onLogoutResponse(packet);
                sessionClosing = false;
            }
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
            case RESYNC_RESPONSE -> {
                sessionClosing = false;
                onResyncResponse(packet);
            }
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

    // Destroy all controllers and initialize them again
    private void onResyncResponse(NetPacket packet) {

        ResyncResponse resp = (ResyncResponse) packet.payload();

        if (!resp.success()) {
            cna.setListener(null); // remove the listener from the network adapter
            cna.updateCredentials(null, null);
            Platform.runLater(this::flatlineTheSession);
            return;
        }
        lobbyCtrl.onResyncResponse(packet);
        gameCtrl.onResyncResponse(packet);
        loginCtrl.onResyncResponse(packet);
        wrapperCtrl.onResyncResponse(packet);
        cna.onResyncFinished();
        wrapperCtrl.setConnectionStatus(resp.message());
        wrapperCtrl.unblockInput();
    }

    private void flatlineTheSession(){
        wrapperCtrl.dispose();//stop the clock ,might seem weird we stop a clock here since we just
        //going to create a new controller but if we do not the JavaFX thread will still have a reference
        //and it won't let GC collect the object, but even worse it would do GUI work, scaling up at each
        //session reset...(i almost missed this one)

        //The whole process is explained once in the GomokuFXAPP, we just follow the same logic
        // excluding the steps that would create what we keep (networkhandler ,stage e.t.c).
        var viewNavigator = new GomokuFXViewNavigator();
        viewNavigator.preload(View.SCENEWRAPPER);
        viewNavigator.preload(View.LOGIN);
        viewNavigator.preload(View.SIGNUP);
        viewNavigator.preload(View.LOBBY);
        viewNavigator.preload(View.GAME);
        var data = new GomokuFXCommonToAllControllersData();
        ctAllControllersData = data;
        setWrapperCtrl((WrapperController) viewNavigator.getController(View.SCENEWRAPPER));
        setLoginCtrl((LoginController) viewNavigator.getController(View.LOGIN));
        setSignupCtrl((SignupController) viewNavigator.getController(View.SIGNUP));
        setLobbyCtrl((LobbyController) viewNavigator.getController(View.LOBBY));
        setGameCtrl((GameController) viewNavigator.getController(View.GAME));
        viewNavigator.initControllers(viewNavigator, this, data);
        initCallbackHandler();
        sendHandshake();
        Parent wrapper = viewNavigator.getWrapper();
        viewNavigator.setTheContentPane();
        stage.setScene(new Scene(wrapper));
        viewNavigator.getContentPane().getChildren().setAll(viewNavigator.getRoot(View.LOGIN));
        wrapperCtrl.unblockInput();

    }

//      ---- SETTERS ----       //

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

}
