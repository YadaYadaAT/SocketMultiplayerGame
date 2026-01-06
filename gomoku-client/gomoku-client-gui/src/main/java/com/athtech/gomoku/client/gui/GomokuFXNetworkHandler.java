package com.athtech.gomoku.client.gui;

import com.athtech.gomoku.client.gui.controllers.LoginController;
import com.athtech.gomoku.client.gui.controllers.SignupController;
import com.athtech.gomoku.client.gui.controllers.WrapperController;
import com.athtech.gomoku.client.net.ClientNetworkAdapter;

import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.payload.*;

import java.util.Map;

public class GomokuFXNetworkHandler {
    private final ClientNetworkAdapter cna;
    private final GomokuFXCommonToAllControllersData ctAllControllersData;
    private WrapperController wrapperCtrl;
    private LoginController loginCtrl;
    private SignupController signupCtrl;


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
        cna.sendPacket(packet);
    }

    public synchronized void updateCredentials(String username,String relogcode){
        cna.updateCredentials(username,relogcode);
    }



    private void handleServerPacket(NetPacket packet) {
        ctAllControllersData.setLastServerActivity(System.currentTimeMillis());
//        if (sessionClosing && packet.type() != PacketType.LOGOUT_RESPONSE) return;
        switch (packet.type()) {
            case LOGIN_RESPONSE -> loginCtrl.onLoginResponse(packet);
            case SIGNUP_RESPONSE -> signupCtrl.onSignupResponse(packet);
//            case LOGOUT_RESPONSE -> onLogoutResponse(packet);
//            case LOBBY_PLAYERS_RESPONSE -> onLobbyPlayersResponse(packet);
//            case GAME_QUIT_RESPONSE -> onGameQuitResponse(packet);
//            case PLAYER_STATS_RESPONSE -> onPlayerStatsResponse(packet);
//            case INVITE_RESPONSE -> onInviteResponse(packet);
//            case INVITE_NOTIFICATION_RESPONSE -> onInviteNotificationResponse(packet);
//            case INVITE_DECISION_RESPONSE -> onInviteDecisionResponse(packet);
//            case GAME_START_RESPONSE -> onGameStartResponse(packet);
//            case GAME_STATE_RESPONSE -> onGameStateResponse(packet);
//            case GAME_END_RESPONSE ->  onGameEndResponse(packet);
//            case REMATCH_RESPONSE ->  onRematchResponse(packet);
//            case MATCH_SESSION_ENDED_RESPONSE ->  onMatchSessionEndedResponse(packet);
//            case MOVE_REJECTED_RESPONSE -> onMoveRejectedResponse(packet);
//            case PLAYER_INACTIVITY_WARNING_RESPONSE -> onPlayerInactivityWarningResponse(packet);
//            case RESYNC_RESPONSE -> onResyncResponse(packet);
//            case ERROR_MESSAGE_RESPONSE -> onErrorMessageResponse(packet);
            case INFO_RESPONSE -> onInfoResponse(packet);
            case HANDSHAKE_RESPONSE -> System.out.println(((HandshakeResponse)  packet.payload()).msg());
//            case GAME_QUIT_NOTIFICATION_RESPONSE -> onGameQuitNotification(packet);
//            case PLAYER_DISCONNECTED_NOTIFICATION_RESPONSE -> onPlayerDisconnectedNotificationResponse(packet);
//            case PLAYER_RECONNECTED_NOTIFICATION_RESPONSE ->  onPlayerReconnectedNotificationResponse(packet);
//            case PLAYER_RECONNECTED_RESPONSE -> onPlayerReconnectedResponse(packet);
            default -> System.out.println("Debugging only ; Unhandled packet: " + packet.type());
        }
    }

    private void onInfoResponse(NetPacket packet){
        if (!(packet.payload() instanceof InfoResponse(String msg))){
            return;
        }
        InfoResponse infoResponse = (InfoResponse) packet.payload();
        loginCtrl.showInfo(infoResponse);
    }



//    private void onPlayerReconnectedResponse(NetPacket packet){
//        PlayerReconnectedResponse res = (PlayerReconnectedResponse) packet.payload();
//        view.unsynchronizedCallback(res.msg());
//    }
//
//    private void onGameQuitNotification(NetPacket packet) {
//        GameQuitNotificationResponse resp =
//                (GameQuitNotificationResponse) packet.payload();
//
//        abortGameSession("Opponent " + resp.quitter() + " quit the game.");
//        view.showLobbyMenu();
//
//    }
//
//    private void onPlayerDisconnectedNotificationResponse(NetPacket packet){
//        PlayerDisconnectedNotificationResponse msg = (PlayerDisconnectedNotificationResponse) packet.payload();
//        view.showCallback(msg.message());
//    }
//
//    private void onPlayerReconnectedNotificationResponse(NetPacket packet){
//        PlayerReconnectedNotificationResponse msg = (PlayerReconnectedNotificationResponse) packet.payload();
//        view.showCallback(msg.message());
//    }
//


//
//    private void onLobbyPlayersResponse(NetPacket packet) {
//        LobbyPlayersResponse resp = (LobbyPlayersResponse) packet.payload();
//        if (inGame){
//            onLobbyPlayersFromPayload(resp.players(), false);
//        }else{
//            onLobbyPlayersFromPayload(resp.players(), true);
//        }
//
//    }
//
//    private void onLobbyPlayersFromPayload(Map<String, Boolean> players, boolean printOn) {
//        lobbyPlayers.clear();
//
//        for (var entry : players.entrySet()) {
//            String user = entry.getKey();
//            boolean inGame = entry.getValue();
//
//            if (!user.equals(username)) {
//                lobbyPlayers.put(user, inGame);
//            }
//        }
//
//        if (printOn) {
//            StringBuilder sb = new StringBuilder("Lobby:");
//            lobbyPlayers.forEach((user, inGame) -> {
//                sb.append(" - ")
//                        .append(user)
//                        .append(inGame ? " 🎮 [IN GAME]" : " ✅ [AVAILABLE]")
//                        .append(", ");
//            });
//            view.showLobby(sb.toString());
//        }
//
//
//        notifyAllLock(resyncWastriggeredLock);
//
//    }
//
//    private void onInviteNotificationResponse(NetPacket packet) {
//        lastInvite = (InviteNotificationResponse) packet.payload();
//        view.showCallback("Invite from: " + lastInvite.fromUsername());
//    }
//
//    private void onInviteResponse(NetPacket packet) {
//        var resp = (InviteResponse) packet.payload();
//        view.showCallback(resp.delivered() ? "Invite delivered." : "Invite failed: " + resp.reason());
//    }
//
//    private void onInviteDecisionResponse(NetPacket packet) {
//        var resp = (InviteDecisionResponse) packet.payload();
//        if (resp.accepted()) {
//            view.showCallback("Invitation accepted. Match starting...");
//            inGame = true;
//        } else {
//            if (resp.targetUsername().equals(username)){
//                view.showCallback("You declined invitation to " + resp.inviterUsername());
//            }else{
//                view.showCallback(resp.targetUsername() + " declined your invitation");
//            }
//
//        }
//        notifyAllLock(gameLock);
//    }
//
//    private void onGameStartResponse(NetPacket packet) {
//        GameStateResponse gs = (GameStateResponse) packet.payload();
//        view.showBoard(gs.board(), username, gs.player1());
//        boolean yourTurn = gs.currentPlayer().equals(username);
//        // Fake "press enter" workaround – only on first game
//        if (!rematchTriggered) {
//            view.showGameStarted(
//                    "\n Connect "+ gs.winCount() +" of your symbols to win the game" +
//                            ". Good luck and have fun \uD83D\uDE08");
//
//        }else{
//            gameStartingPromptConsumsed = true;
//            view.showGameStarted("Game started!");
//        }
//        if (yourTurn) {
//            view.showYourTurn(
//                    """
//                    It's your turn! ('q' to quit — quitting counts as a defeat)
//                    Enter your move as: row,column
//                    """
//            );
//        } else {
//            view.showWaitTurn(
//                    """
//                    Waiting for opponent's move... ('q' to quit — quitting counts as a defeat)
//                    """
//            );
//        }
//        if (!rematchTriggered) {
//            view.show(
//                    "Press " +
//                            "\u001B[38;5;208m`enter`\u001B[0m" +
//                            " once to enter game mode"
//            );
//        }
//
//        // Rematch no longer needs the workaround
//        rematchTriggered = false;
//
//        inGame = !gs.gameOver();
//        notifyAllLock(gameLock);
//    }
//
//    private void onGameStateResponse(NetPacket packet) {
//        GameStateResponse gs = (GameStateResponse) packet.payload();
//        onGameStateFromPayload(gs);
//    }
//
//    private void onGameStateFromPayload(GameStateResponse gs){
//        view.showBoard(gs.board(),username, gs.player1());
//
//        if (gs.currentPlayer().equals(username)) {
//            if (!gameStartingPromptConsumsed) {
//                view.showYourTurn(
//                        """
//                                It's your turn but you haven't yet pressed \u001B[38;5;208m`enter`\u001B[0m\
//                                 to enter into game mode! Activate and proceed to row,column :"""
//                );
//            } else {
//                view.showYourTurn("It's your turn!\n `q` -> quiting \n  row,column -> move");
//            }
//        } else {
//            view.showWaitTurn("Opponent's turn. Please wait for your turn. ('q' -> anytime game quit");
//        }
//
//        inGame = !gs.gameOver();
//        notifyAllLock(gameLock);
//    }
//
//    private void onGameEndResponse(NetPacket packet) {
//        GameEndResponse end = (GameEndResponse) packet.payload();
//        if (end.reason() == MatchEndReason.MID_GAME_REMATCH){
//            //code here wont be ever triggered since midgame rematch never
//            // sends onGameEndResponse ;...was meant to do initially and might change later.
//            // since the development ends here we let it be...
//        }else{
//            //Not interuppted by midgame rematch :
//            // Update session flags
//            inGame = false;
//            rematchPhase = true;
//
//            // Display final board if available
//            if (end.finalBoard() != null) {
//                view.showBoard(end.finalBoard(),username, end.player1());
//            }
//        }
//
//
//        // Show outcome based on MatchEndReason
//        String outcomeMsg;
//        switch (end.reason()) {
//            case MID_GAME_REMATCH -> outcomeMsg = "Good luck at your rematch!";
//            case WIN_NORMAL -> outcomeMsg = "You won! 🎉";
//            case WIN_QUIT -> outcomeMsg = "Opponent quit the game. You win by default! (Press enter to continue)";
//            case WIN_TIMEOUT -> outcomeMsg = "Opponent was AFK. You win! (Press enter to continue)";
//            case WIN_DISCONNECT -> outcomeMsg = "Opponent disconnected. You win! (Press enter to continue)";
//            case LOSS_NORMAL -> outcomeMsg = "You lost. 😢 (Press enter to continue)";
//            case LOSS_QUIT -> outcomeMsg = "You quit the game. 😢";
//            case LOSS_TIMEOUT -> outcomeMsg = "You were AFK. You lost!";
//            case LOSS_DISCONNECT -> outcomeMsg = "You disconnected. You lost!";
//            case DRAW -> outcomeMsg = "It's a draw.";
//            case UNKNOWN -> outcomeMsg = "Game ended unexpectedly.";
//            default -> outcomeMsg = "Game ended.";
//        }
//        view.showCallbackHighlight(outcomeMsg);
//
//
//        if (!(end.reason() == MatchEndReason.MID_GAME_REMATCH)) {
//            if (stateIndicator == CLIstateIndicatorHelper.LOBBY_LOOP) {
//                view.showLobbyMenu();
//            }
//        }
//
//        try {
//            Thread.sleep(300);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//
//        notifyAllLock(gameLock);
//    }
//
//
//    private void onRematchResponse(NetPacket packet) {
//        RematchResponse resp = (RematchResponse) packet.payload();
//        view.showCallback(resp.message());
//    }
//
//    private void onMatchSessionEndedResponse(NetPacket packet) {
//        MatchSessionEndedResponse resp = (MatchSessionEndedResponse) packet.payload();
//        if (resp !=null && resp.isRematchOn()){
//            view.showCallback("Get Ready for the rematch");
//            rematchTriggered = true;
//            inGame = true;
//        }else{
//            rematchTriggered = false;
//            inGame = false;
//        }
//
//        rematchPhase = false;
//
//        notifyAllLock(rematchLock);
//    }
//
//    private void onMoveRejectedResponse(NetPacket packet) {
//
//        MoveRejectedResponse rej = (MoveRejectedResponse) packet.payload();
//        view.showCallback("Move rejected: " + rej.reason());
//        if (rej.currentPlayer() == null) {
//            inGame = false;
//            view.showCallback("Game session lost. Returning to lobby...");
//            notifyAllLock(gameLock);
//        }else{
//            notifyAllLock(gameLock);
//        }
//
//    }
//
//    private void onPlayerStatsResponse(NetPacket packet) {
//        myStats = (PlayerStatsResponse) packet.payload();
//    }
//
//    private void onLogoutResponse(NetPacket packet) {
//        LogoutResponse resp = (LogoutResponse) packet.payload();
//        view.showCallback(resp.message());
//        sessionClosing = false;
//
//        // notify first
//        notifyAllLock(logoutLock);
//        notifyAllLock(gameLock);
//        notifyAllLock(loginLock);
//
//        // only then reset session state
//        resetSessionState();
//
//    }
//
//    private void onErrorMessageResponse(NetPacket packet) {
//        ErrorMessageResponse err = (ErrorMessageResponse) packet.payload();
//        view.showCallbackHighlight("ERROR: " + err.message());
//        notifyAllLock(loginLock);
//        notifyAllLock(gameLock);
//    }
//
//    private void onResyncResponse(NetPacket packet) {
//        ResyncResponse resp = (ResyncResponse) packet.payload();
//
//
//
//        if (!resp.success()) {
//            view.showCallback("Resync attempt rejected: " + resp.message());
//            resetSessionState();
//            wakeAllLocks();
//            return;
//        }
//
//        synchronized (resyncLock) {
//            resyncLock.notifyAll();
//        }
//
//
//        loggedIn = true;
//        sessionClosing = false;
//
//        InviteNotificationResponse[] invites = resp.pendingInvites();
//        lastInvite = (invites != null && invites.length > 0) ? invites[invites.length - 1] : null;
//
//        myStats = resp.myStats();
//        relogCode = resp.relogCode();
//        if (clientNetwork instanceof ClientNetworkAdapterImpl adapter) {
//            adapter.updateCredentials(username, relogCode);
//        }
//        onLobbyPlayersFromPayload(resp.lobbyPlayers().players(), false);
//
////        inGame = false;
////        gameStartingPromptConsumsed = false;
//
//        clientNetwork.onResyncFinished();
//        view.showCallback(resp.message());
//        if (stateIndicator == CLIstateIndicatorHelper.LOBBY_LOOP && !inGame){
//            view.showLobbyMenu();
//        } else if (stateIndicator == CLIstateIndicatorHelper.GAME_LOOP && inGame) {
//            view.showBoard(resp.currentGameState().board(),username,resp.currentGameState().player1());
//
////            view.show("This CLI does not support rejoining an ongoing match after reconnect.\n" +
////                    "To continue playing, you must end your previous match from the lobby.");
//        }
//        resyncWasTriggered =true;
//        notifyAllLock(gameLock);
//        notifyAllLock(loginLock);
//    }
//
//
//
//
//    private void onPlayerInactivityWarningResponse(NetPacket packet){
//        PlayerInactivityWarningResponse pck = (PlayerInactivityWarningResponse) packet.payload();
//        view.showCallback(pck.message());
//    }
//
//    private void onGameQuitResponse(NetPacket packet) {
//        GameQuitResponse gameQuitResponse = (GameQuitResponse)  packet.payload();
//        if (gameQuitResponse.wasItUnstuckProcess()){
//            view.showCallback(gameQuitResponse.msg());
//            notifyAllLock(gameQuitLock);
//            return;
//        }
//        inGame = false;
//        rematchPhase = false;
//        gameStartingPromptConsumsed = false;
//
//        notifyAllLock(gameQuitLock);
//    }
//





}
