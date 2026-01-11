package com.athtech.gomoku.client.cli;
//STUDENTS-CODE-NUMBER : CSY-22117

import com.athtech.gomoku.client.net.ClientNetworkAdapter;
import com.athtech.gomoku.client.net.ClientNetworkAdapterImpl;
import com.athtech.gomoku.client.net.NetState;
import com.athtech.gomoku.protocol.messaging.MatchEndReason;
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
    private volatile long  gameVersion = 0; // var used by client to ensure packets received are the latest ones

    private volatile boolean rematchTriggered = false;
    private volatile long lastServerActivity = System.currentTimeMillis();
    //turns true after resync, altough lobby returns with "resync response" there can be a timing issue and miss someone log

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
        clientNetwork.setListener(this::handleServerPacket); // Define callback method to be called when packet is received
    }

    // Run the application
    public void run() {
        while (!shouldAppExit) {
            stateIndicator = CLIstateIndicatorHelper.AUTHENTICATION_LOOP;
            resetSessionState();
            authenticationMenu();
            if (loggedIn) mainLoop();
        }
        view.show("Hope you enjoyed our app. o/");
    }

    // Resets all temporal session data
    private synchronized void resetSessionState() {
        loggedIn = false;
        sessionClosing = false;
        inGame = false;
        lastInvite = null;
        nickname = null;
        username = null;
        relogCode = null;
        gameVersion =0;
        lobbyPlayers.clear();
        myStats = null;
        gameStartingPromptConsumsed = false;
        rematchPhase = false;
        rematchTriggered =false;
        forceExitGame = false;
    }

    // Displays menu choices to pre-logged-in user
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

    // Login UI
    private void attemptLoginFlow() {
        view.prompt("Username: ");
        username = input.readLine();
        if ("exit".equalsIgnoreCase(username)) { shouldAppExit = true; return; }
        view.prompt("Password: ");
        String password = input.readLine();
        clientNetwork.sendPacket(new NetPacket(PacketType.LOGIN_REQUEST, username, new LoginRequest(username, password)));
        waitLockAndResync(loginLock);
    }

    // Sign up UI
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

    // Displays menu or in-game choices to logged-in user
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

        view.showGameStart(); // starting screen
        while (inGame && loggedIn && !sessionClosing) {
            stateIndicator = CLIstateIndicatorHelper.GAME_LOOP;
            String rawInput = input.readMoveRaw();

            if (rawInput.equalsIgnoreCase("q")) {
                // user wants to quit
                clientNetwork.sendPacket(new NetPacket(PacketType.GAME_QUIT_REQUEST, username, new GameQuitRequest(false)));
                waitLockAndResync(gameQuitLock);
                break;
            }

            if (rawInput.equalsIgnoreCase("r")) {
                clientNetwork.sendPacket(
                        new NetPacket(
                                PacketType.REMATCH_REQUEST,
                                username,
                                new RematchRequest(true)
                        )
                );
                view.showCallback("Rematch intent sent. Game continues...");
                continue;
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


    // Display all available lobby options to logged-in user
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

    // Handles rematch prompting
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

    // Handles invite sending from one user to another
    private void sendInviteRequest() {
        if (inGame) return;
        // request all lobby players from server to ensure correct state (in case of crashes)
        synchronized (resyncWastriggeredLock) { // we could have only at the latest but its safer this way...
            clientNetwork.sendPacket(new NetPacket(PacketType.LOBBY_PLAYERS_REQUEST,username,new LobbyPlayersRequest()));
                try { resyncWastriggeredLock.wait(120_000); }
                catch (InterruptedException e) { Thread.currentThread().interrupt();}
        }

        List<String> snapshot = requestLobbyPlayers();
        if (snapshot.isEmpty()) return; // in case of empty lobby, return

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

        // send the invite request
        clientNetwork.sendPacket(
                new NetPacket(
                        PacketType.INVITE_REQUEST,
                        username,
                        new InviteRequest(snapshot.get(choice - 1))
                )
        );
    }

    // Receive a list of available players
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

    // Handles most recent received invite (in case of multiple invites, shows only the most recent one)
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

    // Handle resync request (ensure correct server state)
    public void requestResync() {
        if (username == null || relogCode == null) return;
        clientNetwork.requestResync();
    }

    // Player data
    private void requestPlayerStats() {
        if (inGame || myStats == null) return;
        view.unsynchronizedCallback(
                "Heres your stats " + nickname +
                        "\nPlayed: " + myStats.gamesPlayed() +
                ", Wins: " + myStats.wins() +
                ", Losses: " + myStats.losses() +
                ", Draws: " + myStats.draws());
    }

    // Ping the server / Test connection
    private void pingToServer(){
        clientNetwork.sendPacket(new NetPacket(PacketType.HANDSHAKE_REQUEST, username,new HandshakeRequest()));
        System.out.println("You just sent a ping request to server... ,(async response is expected)");
    }

    // Send a game quit response to the server
    private void endPreviousMatch(){
        clientNetwork.sendPacket(new NetPacket(PacketType.GAME_QUIT_REQUEST,username,new GameQuitRequest(true)));
        System.out.println("Sent a game quit response to the server");
    }

    // Handle user logout request
    private void requestLogout() {
        if (inGame || sessionClosing) { view.show("Logout already in progress..."); return; }
        sessionClosing = true;
        clientNetwork.sendPacket(new NetPacket(PacketType.LOGOUT_REQUEST, username, new LogoutRequest()));
        waitLockAndResync(logoutLock);
    }

    // Handle packets sent from the server - for details on each of the packet types, see gomoku-protocol package
    private void handleServerPacket(NetPacket packet) {
        lastServerActivity = System.currentTimeMillis(); // keep track of server response time
        if (sessionClosing && packet.type() != PacketType.LOGOUT_RESPONSE
                && packet.type()!=PacketType.RESYNC_RESPONSE){
            return;
        }
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
//            default -> view.unsynchronizedCallback("Unhandled packet: " + packet.type());
            default -> view.unsynchronizedCallback("");//GUI will introduce more unhandled and it would spam the cli user
        }
    }


// ------ FOLLOWING METHODS ARE CALLED BY THE LISTEN LOOP EVERY TIME A PACKET IS RECEIVED -------

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

    // Populate session on login
    private void onLoginResponse(NetPacket packet) {
        LoginResponse resp = (LoginResponse) packet.payload();
        loggedIn = resp.success();
        view.showCallback(resp.message());

        if (loggedIn) {
            relogCode = resp.relogCode();
            myStats = resp.myStats();
            nickname = resp.nickname();
            username = resp.username();

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
            onLobbyPlayersFromPayload(resp.players(), false); // Do not display active lobby members if user is in game
        }else{
            onLobbyPlayersFromPayload(resp.players(), true);
        }
    }

    // Helper method to populate lobby
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

    // Transition from lobby to game flow
    private void onGameStartResponse(NetPacket packet) {
        gameVersion = 0;// reset version..
        GameStateResponse gs = (GameStateResponse) packet.payload(); // receive game state from server
        view.showBoard(gs.board(), username, gs.player1());
        boolean yourTurn = gs.currentPlayer().equals(username);
        // Fake "press enter" workaround – only on first game
        if (!rematchTriggered) {
            view.showGameStarted(
                    "\n Connect "+ gs.winCount() +" of your symbols to win the game" +
                    ". Good luck and have fun \uD83D\uDE08");

        }else{
            gameStartingPromptConsumsed = true;
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

    // All logic of the above method lives here
    private void onGameStateFromPayload(GameStateResponse gs){
        if (gs.version() < gameVersion) {
            // old packet, ignore
            return;
        }
        gameVersion = gs.version();
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
        if (end.reason() == MatchEndReason.MID_GAME_REMATCH){
            //code here wont be ever triggered since midgame rematch never
            // sends onGameEndResponse ; leave for future scalability
        }else{
            //Not interuppted by midgame rematch :
            // Update session flags
            inGame = false;
            rematchPhase = true;

            // Display final board if available
            if (end.finalBoard() != null) {
                view.showBoard(end.finalBoard(),username, end.player1());
            }
        }


        // Show outcome based on MatchEndReason
        String outcomeMsg;
        switch (end.reason()) {
            case MID_GAME_REMATCH -> outcomeMsg = "Good luck at your rematch!";
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


        if (!(end.reason() == MatchEndReason.MID_GAME_REMATCH)) {
            if (stateIndicator == CLIstateIndicatorHelper.LOBBY_LOOP) {
                view.showLobbyMenu();
            }
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

    // terminate entire game session
    private void onMatchSessionEndedResponse(NetPacket packet) {
        gameVersion = 0;
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

    // Repopulate session data on successful resync or reset everything, forcing user to login again
    private void onResyncResponse(NetPacket packet) {
        ResyncResponse resp = (ResyncResponse) packet.payload();

        if (!resp.success()) {
            view.showCallback("Resync attempt rejected: " + resp.message());
            resetSessionState();
            wakeAllLocks(); // Unlock all relevant methods
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
        notifyAllLock(gameLock);
        notifyAllLock(loginLock);
    }


    private void onPlayerInactivityWarningResponse(NetPacket packet){
        PlayerInactivityWarningResponse pck = (PlayerInactivityWarningResponse) packet.payload();
        view.showCallback(pck.message());
    }

    private void onGameQuitResponse(NetPacket packet) {
        GameQuitResponse gameQuitResponse = (GameQuitResponse)  packet.payload();
        if (gameQuitResponse.wasItUnstuckProcess()){
            view.showCallback(gameQuitResponse.msg());
            notifyAllLock(gameQuitLock);
            return;
        }
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

    // Used by all the callback methods above to wake them up
    private void notifyAllLock(Object lock) {
        synchronized (lock) { lock.notifyAll(); }
    }

// ---------------

    // The following method is used after any packet is sent to the server. It checks for valid server response times - if a server response has not arrived after 6 seconds from the time that the request was sent, a resync is requested.
    private void waitLockAndResync(Object lock){
        long sentAt = System.currentTimeMillis(); // log the time that client sends a request
        if (clientNetwork.getState() == NetState.CONNECTED) {
            new Thread(() -> {
                try {
                    Thread.sleep(6_000); // start a new thread and wait 6 secs
                } catch (InterruptedException ignored) {}

                if (lastServerActivity < sentAt) { // if the most recent server activity is before the time that the request was sent, we assume the server is unresponsive
                    view.showCallback("No server activity detected. Attempting resync...");
                    requestResync(); // request resync with server
                }
            }).start();
        };

        synchronized (lock) { // use mutex to block main thread until resync is complete
            try { lock.wait(0); }
            catch (InterruptedException e) { Thread.currentThread().interrupt();}
        }
    }


    // resets all flow variables after quitting game
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
