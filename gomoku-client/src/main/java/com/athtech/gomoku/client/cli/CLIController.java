package com.athtech.gomoku.client.cli;

import com.athtech.gomoku.client.net.ClientNetworkAdapter;
import com.athtech.gomoku.client.net.ClientNetworkAdapterImpl;
import com.athtech.gomoku.client.net.NetState;
import com.athtech.gomoku.client.net.NetworkLifecycleListener;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.*;

import java.util.*;

public class CLIController {
    private final CLIView view;
    private final ClientNetworkAdapter clientNetwork;
    private final CLIInputHandler input;

    // Session states / UI flow
    private volatile boolean loggedIn = false; //equals to being in lobby
    private volatile boolean inGame = false;
    private volatile boolean rematchPhase = false; //end of game rematch
    private volatile boolean sessionClosing = false; //during logout
    private volatile boolean forceExitGame = false;

    private volatile boolean rematchTriggered = false;
    private volatile long lastServerActivity = System.currentTimeMillis();
    //turns true after resync, altough lobby returns with "resync response" there can be a timing issue and miss someone log
    private volatile boolean resyncWasTriggered = false;//because of broadcast triggered by users user might be out of sync
                                                            // till anyone in lobby trigger a broadcast (login,logout,gameIn/out)
                                                            //rare case in big lobbies...
    private String username;

    private String nickname;
    private String relogCode;
    private final Map<String, Boolean> lobbyPlayers = new LinkedHashMap<>();

    private volatile PlayerStatsResponse myStats;
    private volatile InviteNotificationResponse lastInvite = null;
    // Locks
    private final Object loginLock = new Object();
    private final Object logoutLock = new Object();
    private final Object gameLock = new Object();
    private final Object gameQuitLock = new Object();
    private final Object inviteLock = new Object();
    private final Object resyncLock = new Object();
    private final Object rematchLock = new Object();
    private final Object resyncWastriggeredLock = new Object();

    private volatile CLIstateIndicatorHelper stateIndicator;
    private volatile boolean gameStartingPromptConsumsed = false;
    private boolean shouldAppExit = false;




    public CLIController(CLIView view, ClientNetworkAdapter clientNetwork ) {
        stateIndicator = CLIstateIndicatorHelper.AUTHENTICATION_LOOP;
        this.view = view;
        this.clientNetwork = clientNetwork;
        input = new CLIInputHandler();
        clientNetwork.setListener(this::handleServerPacket);
    }

    public void run() {
        while (!shouldAppExit) {
            stateIndicator = CLIstateIndicatorHelper.AUTHENTICATION_LOOP;
            resetSessionState();
            authenticationMenu();
            if (loggedIn) mainLoop();
        }
        view.show("Hope you enjoyed our app. o/");
    }



    private synchronized void resetSessionState() {
        loggedIn = false;
        sessionClosing = false;
        inGame = false;
        lastInvite = null;
        nickname = null;
        username = null;
        relogCode = null;
        lobbyPlayers.clear();
        myStats = null;
        gameStartingPromptConsumsed = false;
        rematchPhase = false;
        resyncWasTriggered = false;
        rematchTriggered =false;
        forceExitGame = false;
    }

    private void authenticationMenu() {
        view.showLoginScreen();
        switch (input.readChoice()) {
            case "1" -> attemptLoginFlow();
            case "2" -> attemptSignupFlow();
            case "5" -> pingToServer();
            case "0" -> {
                shouldAppExit = true;
                try { clientNetwork.disconnect(); } catch (Exception ignored) {}
            }
            default -> view.show("Invalid choice.");
        }
    }

    private void attemptLoginFlow() {
        view.prompt("Username: ");
        username = input.readLine();
        if ("exit".equalsIgnoreCase(username)) { shouldAppExit = true; return; }
        view.prompt("Password: ");
        String password = input.readLine();
        clientNetwork.sendPacket(new NetPacket(PacketType.LOGIN_REQUEST, username, new LoginRequest(username, password)));
        waitLockAndResync(loginLock);
    }

    private void attemptSignupFlow() {
        view.showSignupPrompt();
        view.prompt("Choose username: ");
        String desired = input.readLine();
        view.prompt("Choose password: ");
        String pwd = input.readLine();
        view.prompt("Pick a nickname: ");
        String nickname = input.readLine();
        clientNetwork.sendPacket(new NetPacket(PacketType.SIGNUP_REQUEST, desired, new SignupRequest(desired, pwd,nickname)));
        waitLockAndResync(loginLock);
    }

    private void mainLoop() {
        while (loggedIn) {
            stateIndicator = CLIstateIndicatorHelper.LOBBY_LOOP;
            if (sessionClosing) { waitLockAndResync(logoutLock); continue; }

            if (!inGame && rematchPhase){
                handleRematchPrompt();
                waitLockAndResync(rematchLock);
                rematchPhase = false;
            }
            gameStartingPromptConsumsed = false;
            if (!inGame) handleLobby();
            if (inGame) runGameLoop();
        }
    }

    // CLIController
    private void runGameLoop() {

        view.showGameStart();
        while (inGame && loggedIn && !sessionClosing) {
            stateIndicator = CLIstateIndicatorHelper.GAME_LOOP;
            String rawInput = input.readMoveRaw();

            if (rawInput.equalsIgnoreCase("q")) {
                // user wants to quit
                clientNetwork.sendPacket(new NetPacket(PacketType.GAME_QUIT_REQUEST, username, new GameQuitRequest(false)));
                waitLockAndResync(gameQuitLock);
                break;
            }


            if ( forceExitGame || !inGame ) {
                break;
            }
            // attempt to parse move
            String[] parts = rawInput.split(",");
            if (parts.length != 2) {
                view.showCallback("Invalid format. Enter as row,column or 'q' to leave the game.");
                continue;
            }

            try {
                int row = Integer.parseInt(parts[0].trim()) - 1 ;
                int col = Integer.parseInt(parts[1].trim()) - 1 ;
                clientNetwork.sendPacket(new NetPacket(PacketType.MOVE_REQUEST, username, new MoveRequest(row, col)));
                waitLockAndResync(gameLock);
            } catch (NumberFormatException e) {
                view.showCallback("Invalid numbers. Enter as row,column or 'q' to leave the game.");
            }
        }
        forceExitGame = false;
    }



    private void handleLobby() {
        if (inGame || rematchPhase) {
            view.show("You are currently in a game or rematch phase. Lobby actions are disabled.");
            return;
        }

        view.showLobbyMenu();
        var choice = input.readChoice();
        if (inGame || rematchPhase) {
            gameStartingPromptConsumsed = true;
            return; }

        switch (choice) {
            case "1" -> sendInviteRequest();
            case "2" -> handleReceivedInviteRequest();
            case "3" -> requestPlayerStats();
            case "4" -> endPreviousMatch();
            case "5" -> pingToServer();
            case "0" -> requestLogout();
            default -> view.show("Invalid option.");
        }
    }

    private void handleRematchPrompt() {
        if (!rematchPhase) return;
        view.showRematchPrompt();
        String line;
        do { //GZ you found a CLI bug....although o consume the buffers on the callback
            //there's a good chance that console buffering still have values
            //so we had to guard this specifically with a do while T.T ...CLI games for the win!
            //thereore we cant have messages here if put something else since they might confuse the user.
            line = input.readLine().trim().toLowerCase();
        } while (!line.equals("y") && !line.equals("n"));

        boolean wantRematch = line.equals("y");
        if (wantRematch){
            view.show("Rematch request in progress please wait...");
        }

        clientNetwork.sendPacket(new NetPacket(PacketType.REMATCH_REQUEST, username, new RematchRequest(wantRematch)));
    }

    private void sendInviteRequest() {
        if (inGame) return;
        if (resyncWasTriggered){

            view.unsynchronizedCallback(
                    "Syncing lobby state , please wait..."
            );
            resyncWasTriggered = false;
            synchronized (resyncWastriggeredLock){
                clientNetwork.sendPacket(new NetPacket(PacketType.LOBBY_PLAYERS_REQUEST,username,new LobbyPlayersRequest()));
                    try { resyncWastriggeredLock.wait(120_000); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt();}
            }

        }
        List<String> snapshot = requestLobbyPlayers();
        if (snapshot.isEmpty()) return;

        view.prompt("Choose a player to invite (number), press `q` to go back: ");

        int choice = input.readIntOrQuit();
        if (choice == -1) {
            view.show("Invite cancelled. Returning to lobby menu.");
            return;
        }

        if (choice < 1 || choice > snapshot.size()) {
            view.show("Invalid choice. Returning to lobby menu.");
            return;
        }

        clientNetwork.sendPacket(
                new NetPacket(
                        PacketType.INVITE_REQUEST,
                        username,
                        new InviteRequest(snapshot.get(choice - 1))
                )
        );
    }


    private List<String> requestLobbyPlayers() {
        if (inGame) return List.of();
        if (lobbyPlayers.isEmpty()) {
            view.show("No players available.");
            return List.of();
        }

        List<String> inviteable = new ArrayList<>();

        for (var entry : lobbyPlayers.entrySet()) {
            if (!entry.getValue()) { // not in game
                inviteable.add(entry.getKey());
            }
        }

        if (inviteable.isEmpty()) {
            view.show("No players available to invite.");
            return List.of();
        }

        for (int i = 0; i < inviteable.size(); i++) {
            view.unsynchronizedCallback((i + 1) + ") " + inviteable.get(i));
        }

        return inviteable;
    }

    private void handleReceivedInviteRequest() {
        synchronized (inviteLock) {
            if(lastInvite == null){
                view.show("There are no invites");
                return;
            }
            if (inGame){
                return;
            }
            view.show("Invite from: " + lastInvite.fromUsername());
            view.showAcceptPrompt();
            boolean accept = input.readLine().trim().equalsIgnoreCase("y");
            clientNetwork.sendPacket(new NetPacket(PacketType.INVITE_DECISION_REQUEST, username,
                    new InviteDecisionRequest(lastInvite.fromUsername(), accept)));
            lastInvite = null;
        }
    }

    public void requestResync() {
        if (username == null || relogCode == null) return;
        clientNetwork.requestResync(username, relogCode);
    }



    private void requestPlayerStats() {
        if (inGame || myStats == null) return;
        view.unsynchronizedCallback(
                "Heres your stats " + nickname +
                        "\nPlayed: " + myStats.gamesPlayed() +
                ", Wins: " + myStats.wins() +
                ", Losses: " + myStats.losses() +
                ", Draws: " + myStats.draws());
    }


    private void pingToServer(){
        clientNetwork.sendPacket(new NetPacket(PacketType.HANDSHAKE_REQUEST, username,new HandshakeRequest()));
        System.out.println("You just sent a ping request to server... ,(async response is expected)");
    }

    private void endPreviousMatch(){
        clientNetwork.sendPacket(new NetPacket(PacketType.GAME_QUIT_REQUEST,username,new GameQuitRequest(true)));
        System.out.println("Sent a game quit response to the server");
    }

    private void requestLogout() {
        if (inGame || sessionClosing) { view.show("Logout already in progress..."); return; }
        sessionClosing = true;
        clientNetwork.sendPacket(new NetPacket(PacketType.LOGOUT_REQUEST, username, new LogoutRequest()));
        waitLockAndResync(logoutLock);
    }


    private void handleServerPacket(NetPacket packet) {
        lastServerActivity = System.currentTimeMillis();
        if (sessionClosing && packet.type() != PacketType.LOGOUT_RESPONSE) return;

        switch (packet.type()) {
            case LOGIN_RESPONSE -> onLoginResponse(packet);
            case SIGNUP_RESPONSE -> onSignupResponse(packet);
            case LOGOUT_RESPONSE -> onLogoutResponse(packet);
            case LOBBY_PLAYERS_RESPONSE -> onLobbyPlayersResponse(packet);
            case GAME_QUIT_RESPONSE -> onGameQuitResponse(packet);
            case PLAYER_STATS_RESPONSE -> onPlayerStatsResponse(packet);
            case INVITE_RESPONSE -> onInviteResponse(packet);
            case INVITE_NOTIFICATION_RESPONSE -> onInviteNotificationResponse(packet);
            case INVITE_DECISION_RESPONSE -> onInviteDecisionResponse(packet);
            case GAME_START_RESPONSE -> onGameStartResponse(packet);
            case GAME_STATE_RESPONSE -> onGameStateResponse(packet);
            case GAME_END_RESPONSE ->  onGameEndResponse(packet);
            case REMATCH_RESPONSE ->  onRematchResponse(packet);
            case MATCH_SESSION_ENDED_RESPONSE ->  onMatchSessionEndedResponse(packet);
            case MOVE_REJECTED_RESPONSE -> onMoveRejectedResponse(packet);
            case PLAYER_INACTIVITY_WARNING_RESPONSE -> onPlayerInactivityWarningResponse(packet);
            case RESYNC_RESPONSE -> onResyncResponse(packet);
            case ERROR_MESSAGE_RESPONSE -> onErrorMessageResponse(packet);
            case INFO_RESPONSE -> onInfoResponse(packet);
            case HANDSHAKE_RESPONSE -> onHandshakeResponse(packet);
            case GAME_QUIT_NOTIFICATION_RESPONSE -> onGameQuitNotification(packet);
            case PLAYER_DISCONNECTED_NOTIFICATION_RESPONSE -> onPlayerDisconnectedNotificationResponse(packet);
            case PLAYER_RECONNECTED_NOTIFICATION_RESPONSE ->  onPlayerReconnectedNotificationResponse(packet);
            case PLAYER_RECONNECTED_RESPONSE -> onPlayerReconnectedResponse(packet);
            default -> view.unsynchronizedCallback("Unhandled packet: " + packet.type());
        }
    }

    private void onPlayerReconnectedResponse(NetPacket packet){
        PlayerReconnectedResponse res = (PlayerReconnectedResponse) packet.payload();
        view.unsynchronizedCallback(res.msg());
    }

    private void onGameQuitNotification(NetPacket packet) {
        GameQuitNotificationResponse resp =
                (GameQuitNotificationResponse) packet.payload();

        abortGameSession("Opponent " + resp.quitter() + " quit the game.");
        view.showLobbyMenu();

    }

    private void onPlayerDisconnectedNotificationResponse(NetPacket packet){
        PlayerDisconnectedNotificationResponse msg = (PlayerDisconnectedNotificationResponse) packet.payload();
        view.showCallback(msg.message());
    }

    private void onPlayerReconnectedNotificationResponse(NetPacket packet){
        PlayerReconnectedNotificationResponse msg = (PlayerReconnectedNotificationResponse) packet.payload();
        view.showCallback(msg.message());
    }

    private void onHandshakeResponse(NetPacket packet){
        HandshakeResponse handshake = (HandshakeResponse)  packet.payload();
        view.unsynchronizedCallback(handshake.msg());
    }

    private void onLoginResponse(NetPacket packet) {
        LoginResponse resp = (LoginResponse) packet.payload();
        loggedIn = resp.success();
        view.showCallback(resp.message());

        if (loggedIn) {
            relogCode = resp.relogCode();
            myStats = resp.myStats();
            nickname = resp.nickname();
            username = resp.username();

            // <-- ADD THIS
            if (clientNetwork instanceof ClientNetworkAdapterImpl adapter) {
                adapter.updateCredentials(username, relogCode);
            }

            // handle invites like resync
            InviteNotificationResponse[] invites = resp.pendingInvites();
            lastInvite = (invites != null && invites.length > 0) ? invites[invites.length - 1] : null;

            gameStartingPromptConsumsed = false;
        }
        notifyAllLock(loginLock);
    }
    private void onSignupResponse(NetPacket packet) {
        SignupResponse resp = (SignupResponse) packet.payload();
        view.showCallback(resp.message());
        notifyAllLock(loginLock);
    }

    private void onLobbyPlayersResponse(NetPacket packet) {
        LobbyPlayersResponse resp = (LobbyPlayersResponse) packet.payload();
        if (inGame){
            onLobbyPlayersFromPayload(resp.players(), false);
        }else{
            onLobbyPlayersFromPayload(resp.players(), true);
        }

    }

    private void onLobbyPlayersFromPayload(Map<String, Boolean> players, boolean printOn) {
        lobbyPlayers.clear();

        for (var entry : players.entrySet()) {
            String user = entry.getKey();
            boolean inGame = entry.getValue();

            if (!user.equals(username)) {
                lobbyPlayers.put(user, inGame);
            }
        }

        if (printOn) {
            StringBuilder sb = new StringBuilder("Lobby:");
            lobbyPlayers.forEach((user, inGame) -> {
                sb.append(" - ")
                        .append(user)
                        .append(inGame ? " 🎮 [IN GAME]" : " ✅ [AVAILABLE]")
                        .append(", ");
            });
            view.showLobby(sb.toString());
        }


        notifyAllLock(resyncWastriggeredLock);

    }

    private void onInviteNotificationResponse(NetPacket packet) {
        lastInvite = (InviteNotificationResponse) packet.payload();
        view.showCallback("Invite from: " + lastInvite.fromUsername());
    }

    private void onInviteResponse(NetPacket packet) {
        var resp = (InviteResponse) packet.payload();
        view.showCallback(resp.delivered() ? "Invite delivered." : "Invite failed: " + resp.reason());
    }

    private void onInviteDecisionResponse(NetPacket packet) {
        var resp = (InviteDecisionResponse) packet.payload();
        if (resp.accepted()) {
            view.showCallback("Invitation accepted. Match starting...");
            inGame = true;
        } else {
            if (resp.targetUsername().equals(username)){
                view.showCallback("You declined invitation to " + resp.inviterUsername());
            }else{
                view.showCallback(resp.targetUsername() + " declined your invitation");
            }

        }
        notifyAllLock(gameLock);
    }

    private void onGameStartResponse(NetPacket packet) {
        GameStateResponse gs = (GameStateResponse) packet.payload();
        view.showBoard(gs.board(), username, gs.player1());
        boolean yourTurn = gs.currentPlayer().equals(username);
        // Fake "press enter" workaround – only on first game
        if (!rematchTriggered) {
            view.showGameStarted(
                    "\n Connect "+ gs.winCount() +" of your symbols to win the game" +
                    ". Good luck and have fun \uD83D\uDE08");

        }else{
            view.showGameStarted("Game started!");
        }
        if (yourTurn) {
            view.showYourTurn(
                    """
                    It's your turn! ('q' to quit — quitting counts as a defeat)
                    Enter your move as: row,column
                    """
            );
        } else {
            view.showWaitTurn(
                    """
                    Waiting for opponent's move... ('q' to quit — quitting counts as a defeat)
                    """
            );
        }
        if (!rematchTriggered) {
            view.show(
                    "Press " +
                            "\u001B[38;5;208m`enter`\u001B[0m" +
                            " once to enter game mode"
            );
        }

        // Rematch no longer needs the workaround
        rematchTriggered = false;

        inGame = !gs.gameOver();
        notifyAllLock(gameLock);
    }

    private void onGameStateResponse(NetPacket packet) {
        GameStateResponse gs = (GameStateResponse) packet.payload();
        onGameStateFromPayload(gs);
    }

    private void onGameStateFromPayload(GameStateResponse gs){
        view.showBoard(gs.board(),username, gs.player1());

        if (gs.currentPlayer().equals(username)) {
            if (!gameStartingPromptConsumsed) {
                view.showYourTurn(
                        """
                                It's your turn but you haven't yet pressed \u001B[38;5;208m`enter`\u001B[0m\
                                 to enter into game mode! Activate and proceed to row,column :"""
                );
            } else {
                view.showYourTurn("It's your turn!\n `q` -> quiting \n  row,column -> move");
            }
        } else {
            view.showWaitTurn("Opponent's turn. Please wait for your turn. ('q' -> anytime game quit");
        }

        inGame = !gs.gameOver();
        notifyAllLock(gameLock);
    }

    private void onGameEndResponse(NetPacket packet) {
        GameEndResponse end = (GameEndResponse) packet.payload();

        // Update session flags
        inGame = false;
        rematchPhase = true;

        // Display final board if available
        if (end.finalBoard() != null) {
            view.showBoard(end.finalBoard(),username, end.player1());
        }

        // Show outcome based on MatchEndReason
        String outcomeMsg;
        switch (end.reason()) {
            case WIN_NORMAL -> outcomeMsg = "You won! 🎉";
            case WIN_QUIT -> outcomeMsg = "Opponent quit the game. You win by default! (Press enter to continue)";
            case WIN_TIMEOUT -> outcomeMsg = "Opponent was AFK. You win! (Press enter to continue)";
            case WIN_DISCONNECT -> outcomeMsg = "Opponent disconnected. You win! (Press enter to continue)";
            case LOSS_NORMAL -> outcomeMsg = "You lost. 😢 (Press enter to continue)";
            case LOSS_QUIT -> outcomeMsg = "You quit the game. 😢";
            case LOSS_TIMEOUT -> outcomeMsg = "You were AFK. You lost!";
            case LOSS_DISCONNECT -> outcomeMsg = "You disconnected. You lost!";
            case DRAW -> outcomeMsg = "It's a draw.";
            case UNKNOWN -> outcomeMsg = "Game ended unexpectedly.";
            default -> outcomeMsg = "Game ended.";
        }

        view.showCallbackHighlight(outcomeMsg);
        if(stateIndicator == CLIstateIndicatorHelper.LOBBY_LOOP){
            view.showLobbyMenu();
        }


        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        notifyAllLock(gameLock);
    }


    private void onRematchResponse(NetPacket packet) {
        RematchResponse resp = (RematchResponse) packet.payload();
        view.showCallback(resp.message());
    }

    private void onMatchSessionEndedResponse(NetPacket packet) {
        MatchSessionEndedResponse resp = (MatchSessionEndedResponse) packet.payload();
        if (resp !=null && resp.isRematchOn()){
            view.showCallback("Get Ready for the rematch");
            rematchTriggered = true;
            inGame = true;
        }else{
            rematchTriggered = false;
            inGame = false;
        }

        rematchPhase = false;

        notifyAllLock(rematchLock);
    }

    private void onMoveRejectedResponse(NetPacket packet) {

        MoveRejectedResponse rej = (MoveRejectedResponse) packet.payload();
        view.showCallback("Move rejected: " + rej.reason());
        if (rej.currentPlayer() == null) {
            inGame = false;
            view.showCallback("Game session lost. Returning to lobby...");
            notifyAllLock(gameLock);
        }else{
            notifyAllLock(gameLock);
        }

    }

    private void onPlayerStatsResponse(NetPacket packet) {
        myStats = (PlayerStatsResponse) packet.payload();
    }

    private void onLogoutResponse(NetPacket packet) {
        LogoutResponse resp = (LogoutResponse) packet.payload();
        view.showCallback(resp.message());
        sessionClosing = false;

        // notify first
        notifyAllLock(logoutLock);
        notifyAllLock(gameLock);
        notifyAllLock(loginLock);

        // only then reset session state
        resetSessionState();

    }

    private void onErrorMessageResponse(NetPacket packet) {
        ErrorMessageResponse err = (ErrorMessageResponse) packet.payload();
        view.showCallbackHighlight("ERROR: " + err.message());
        notifyAllLock(loginLock);
        notifyAllLock(gameLock);
    }

    private void onResyncResponse(NetPacket packet) {
        ResyncResponse resp = (ResyncResponse) packet.payload();



        if (!resp.success()) {
            view.showCallback("Resync attempt rejected: " + resp.message());
            resetSessionState();
            wakeAllLocks();
            return;
        }

        synchronized (resyncLock) {
            resyncLock.notifyAll();
        }


        loggedIn = true;
        sessionClosing = false;

        InviteNotificationResponse[] invites = resp.pendingInvites();
        lastInvite = (invites != null && invites.length > 0) ? invites[invites.length - 1] : null;

        myStats = resp.myStats();
        relogCode = resp.relogCode();
        if (clientNetwork instanceof ClientNetworkAdapterImpl adapter) {
            adapter.updateCredentials(username, relogCode);
        }
        onLobbyPlayersFromPayload(resp.lobbyPlayers().players(), false);

//        inGame = false;
//        gameStartingPromptConsumsed = false;

        clientNetwork.onResyncFinished();
        view.showCallback(resp.message());
        if (stateIndicator == CLIstateIndicatorHelper.LOBBY_LOOP && !inGame){
            view.showLobbyMenu();
        } else if (stateIndicator == CLIstateIndicatorHelper.GAME_LOOP && inGame) {
            view.showBoard(resp.currentGameState().board(),username,resp.currentGameState().player1());

//            view.show("This CLI does not support rejoining an ongoing match after reconnect.\n" +
//                    "To continue playing, you must end your previous match from the lobby.");
        }
        resyncWasTriggered =true;
        notifyAllLock(gameLock);
        notifyAllLock(loginLock);
    }




    private void onPlayerInactivityWarningResponse(NetPacket packet){
        view.showCallback((String) packet.payload());
    }

    private void onGameQuitResponse(NetPacket packet) {

        view.showCallback("You quit the game.");


        inGame = false;
        rematchPhase = false;
        gameStartingPromptConsumsed = false;

        notifyAllLock(gameQuitLock);
    }

    private void onInfoResponse(NetPacket packet){
        if (!(packet.payload() instanceof InfoResponse(String msg))){
            return;
        }
        view.showCallback(msg);
    }

    private void waitLockAndResync(Object lock){
        long sentAt = System.currentTimeMillis();
        if (clientNetwork.getState() == NetState.CONNECTED) {
            new Thread(() -> {
                try {
                    Thread.sleep(6_000);
                } catch (InterruptedException ignored) {}

                if (lastServerActivity < sentAt && clientNetwork.getState() == NetState.CONNECTED) {
                    view.showCallback("No server activity detected. Attempting resync...");
                    requestResync();
                }
            }).start();
        };

        synchronized (lock) {
            try { lock.wait(0); }
            catch (InterruptedException e) { Thread.currentThread().interrupt();}
        }
    }

    private void notifyAllLock(Object lock) {
        synchronized (lock) { lock.notifyAll(); }
    }

    public boolean getIsInGame() {
        return inGame;
    }

    private void abortGameSession(String reason) {
        view.showCallbackHighlight(reason);

        forceExitGame = true;
        inGame = false;
        rematchPhase = false;
        gameStartingPromptConsumsed = false;

        // Wake all relevant locks
        notifyAllLock(gameLock);
        notifyAllLock(gameQuitLock);
        notifyAllLock(rematchLock);
        notifyAllLock(inviteLock);
    }

    private void wakeAllLocks() {
        notifyAllLock(resyncLock);
        notifyAllLock(loginLock);
        notifyAllLock(logoutLock);
        notifyAllLock(gameLock);
        notifyAllLock(gameQuitLock);
        notifyAllLock(inviteLock);
        notifyAllLock(rematchLock);
        notifyAllLock(resyncWastriggeredLock);
    }



}
