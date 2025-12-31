package com.athtech.connect4.client.cli;

import com.athtech.connect4.client.net.ClientNetworkAdapter;
import com.athtech.connect4.protocol.messaging.*;
import com.athtech.connect4.protocol.payload.*;

import java.util.*;

public class CLIController {
    private final CLIView view;
    private final ClientNetworkAdapter clientNetwork;
    private final CLIInputHandler input;

    // Session states
    private volatile boolean loggedIn = false;
    private volatile boolean sessionClosing = false;
    private volatile boolean inGame = false;
    private volatile boolean rematchPhase = false;

    private volatile long lastServerActivity = System.currentTimeMillis();


    private String username;
    private String relogCode;
    private final List<String> lobbyPlayers = new ArrayList<>();
    private volatile PlayerStatsResponse myStats;
    private volatile InviteNotificationResponse lastInvite = null;

    // Locks
    private final Object loginLock = new Object();
    private final Object logoutLock = new Object();
    private final Object gameLock = new Object();
    private final Object inviteLock = new Object();
    private final Object resyncLock = new Object();
    private final Object rematchLock = new Object();

    private volatile boolean resyncInProgress = false;
    private volatile boolean resyncSucceeded = false;
    private final int MAX_RESYNC_ATTEMPTS = 10;
    private final long RESYNC_INTERVAL_MS = 5_000;

    private volatile boolean gameStartingPromptConsumsed = false;
    private boolean shouldAppExit = false;


    public CLIController(CLIView view, ClientNetworkAdapter clientNetwork ) {
        this.view = view;
        this.clientNetwork = clientNetwork;
        input = new CLIInputHandler(this::getIsInGame);
        clientNetwork.setListener(this::handleServerPacket);
    }

    public void run() {
        while (!shouldAppExit) {
            resetSessionState();
            authenticationMenu();
            if (loggedIn) mainLoop();
        }
        view.show("Hope you enjoyed our app. o/");
    }

    private void resetSessionState() {
        loggedIn = false;
        sessionClosing = false;
        inGame = false;
        lastInvite = null;
        username = null;
        relogCode = null;
        lobbyPlayers.clear();
        myStats = null;
        gameStartingPromptConsumsed = false;
        rematchPhase = false;
        resyncInProgress = false;
    }

    private void authenticationMenu() {
        view.showLoginScreen();
        switch (input.readChoice()) {
            case "1" -> attemptLoginFlow();
            case "2" -> attemptSignupFlow();
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
        clientNetwork.sendPacket(new NetPacket(PacketType.SIGNUP_REQUEST, desired, new SignupRequest(desired, pwd)));
        waitLockAndResync(loginLock);
    }

    private void mainLoop() {
        while (loggedIn) {
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

    private void runGameLoop() {
        view.showGameStart();
        while (inGame && loggedIn && !sessionClosing) {
            int[] move = input.readMove();
            // game end consume input of loser
            if (!inGame || move == null) {break;}
            int row = move[0];
            int col = move[1];
            clientNetwork.sendPacket(new NetPacket(PacketType.MOVE_REQUEST, username, new MoveRequest(row, col)));
            waitLockAndResync(gameLock);
        }
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
            case "3" -> requestLobbyPlayers();
            case "4" -> requestPlayerStats();
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

        clientNetwork.sendPacket(new NetPacket(PacketType.INVITE_REQUEST, username,
                new InviteRequest(snapshot.get(choice - 1))));
    }

    private void handleReceivedInviteRequest() {
        synchronized (inviteLock) {
            if (inGame || lastInvite == null) return;
            view.show("Invite from: " + lastInvite.fromUsername());
            view.showAcceptPrompt();
            boolean accept = input.readLine().trim().equalsIgnoreCase("y");
            clientNetwork.sendPacket(new NetPacket(PacketType.INVITE_DECISION_REQUEST, username,
                    new InviteDecisionRequest(lastInvite.fromUsername(), accept)));
            lastInvite = null;
        }
    }

    public void onNetworkReconnected() {
        if (username != null && relogCode != null && !resyncInProgress) {
            view.showCallback("Network restored, attempting automatic resync...");
            ResyncWithAuth();
        }
    }

    public void ResyncWithAuth() {
        synchronized (resyncLock) {
            if (resyncInProgress || username == null || relogCode == null) return;
            resyncInProgress = true;
            resyncSucceeded = false;
        }

        new Thread(() -> {
            int attempts = 0;

            while (attempts < MAX_RESYNC_ATTEMPTS && resyncInProgress && !resyncSucceeded) {
                attempts++;
                view.showCallback("Attempting resync (" + attempts + "/" + MAX_RESYNC_ATTEMPTS + ")...");

                synchronized (resyncLock) {
                    clientNetwork.sendPacket(new NetPacket(PacketType.RESYNC_REQUEST, username,
                            new ResyncRequest(username, relogCode)));

                    long timeout = 8000;
                    long startTime = System.currentTimeMillis();
                    while (!resyncSucceeded && (System.currentTimeMillis() - startTime) < timeout) {
                        try {
                            resyncLock.wait(timeout - (System.currentTimeMillis() - startTime));
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                if (resyncSucceeded) break;

                try { Thread.sleep(RESYNC_INTERVAL_MS); } catch (InterruptedException ignored) {}
            }

            synchronized (resyncLock) {
                resyncInProgress = false;
            }

            if (!resyncSucceeded) {
                view.showCallback("Resync failed. Session lost.");
                resetSessionState();
                notifyAllLock(gameLock);
            }
        }, "ReconnectThread").start();
    }



    private List<String> requestLobbyPlayers() {
        if (inGame) return List.of();
        if (lobbyPlayers.isEmpty()) { view.showCallback("No players available."); return List.of(); }
        for (int i = 0; i < lobbyPlayers.size(); i++) view.showCallback((i + 1) + ") " + lobbyPlayers.get(i));
        return new ArrayList<>(lobbyPlayers);
    }

    private void requestPlayerStats() {
        if (inGame || myStats == null) return;
        view.showCallback("Stats → Played: " + myStats.gamesPlayed() +
                ", Wins: " + myStats.wins() +
                ", Losses: " + myStats.losses() +
                ", Draws: " + myStats.draws());
    }

    private void requestLogout() {
        if (inGame || sessionClosing) { view.showCallback("Logout already in progress..."); return; }

        sessionClosing = true;
        clientNetwork.sendPacket(new NetPacket(PacketType.LOGOUT_REQUEST, username, new LogoutRequest()));
                waitLockAndResync(logoutLock);
        resetSessionState();
        try { clientNetwork.disconnect(); } catch (Exception ignored) {}
    }

    private void handleServerPacket(NetPacket packet) {
        lastServerActivity = System.currentTimeMillis();
        if (sessionClosing && packet.type() != PacketType.LOGOUT_RESPONSE) return;

        switch (packet.type()) {
            case LOGIN_RESPONSE -> onLoginResponse(packet);
            case SIGNUP_RESPONSE -> onSignupResponse(packet);
            case LOGOUT_RESPONSE -> onLogoutResponse(packet);
            case LOBBY_PLAYERS_RESPONSE -> onLobbyPlayersResponse(packet);
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
            case RESYNC_RESPONSE -> onResyncResponse(packet);
            case ERROR_MESSAGE_RESPONSE -> onErrorMessageResponse(packet);
            case INFO_RESPONSE -> onInfoResponse(packet);
            case HANDSHAKE -> onHandshake(packet);
            default -> view.showCallback("Unhandled packet: " + packet.type());
        }
    }

    private void onHandshake(NetPacket packet){
        //empty just to sync ;...
    }

    // --- on* callbacks now use showCallback / showCallbackHighlight ---
    private void onLoginResponse(NetPacket packet) {
        LoginResponse resp = (LoginResponse) packet.payload();
        loggedIn = resp.success();
        view.showCallback(resp.message());
        if (loggedIn) { relogCode = resp.relogCode(); myStats = resp.stats(); }
        notifyAllLock(loginLock);
    }

    private void onSignupResponse(NetPacket packet) {
        SignupResponse resp = (SignupResponse) packet.payload();
        view.showCallback(resp.message());
        notifyAllLock(loginLock);
    }

    private void onLobbyPlayersResponse(NetPacket packet) {
        var lobP = (String[]) packet.payload();
        onLobbyPlayersFromPayload(lobP, true);
    }

    private void onLobbyPlayersFromPayload(String[] lobP, boolean printOn) {

        lobbyPlayers.clear();
        lobbyPlayers.addAll(Arrays.asList(lobP));

        if (username != null) {
            lobbyPlayers.removeIf(u -> u.equals(username));
        }
        if(printOn){
            view.showCallback("Lobby: " + String.join(", ", lobbyPlayers));
        }


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
        view.showGameStarted("Ignore Lobby Menu! Game started!");

        GameStateResponse gs = (GameStateResponse) packet.payload();
        view.showBoard(gs.board());

        if (gs.currentPlayer().equals(username)) {
            view.showYourTurn(
                    """
                            At the start of the game players need to press \
                            \u001B[38;5;208m`enter`\u001B[0m\
                             once to enter into the game mode!\
                            \s
                             It's also your turn so afterwards enter your move:
                             row,column:"""
            );
        } else {
            view.showWaitTurn(
                    "Press "
                            + "\u001B[38;5;208m`enter`\u001B[0m"
                            + " once to enter into the gaming mode"
                            + "\nAnd then wait since it's your opponent's turn."
            );
        }

        inGame = !gs.gameOver();
        System.out.println("Boolean being : " + inGame);
        notifyAllLock(gameLock);
    }

    private void onGameStateResponse(NetPacket packet) {
        GameStateResponse gs = (GameStateResponse) packet.payload();
        onGameStateFromPayload(gs);
    }

    private void onGameStateFromPayload(GameStateResponse gs){
        view.showBoard(gs.board());

        if (gs.currentPlayer().equals(username)) {
            if (!gameStartingPromptConsumsed) {
                view.showYourTurn(
                        """
                                It's your turn but you haven't yet pressed \
                                \u001B[38;5;208m`enter`\u001B[0m\
                                 to enter into game mode!\
                                
                                Once you activate it you may proceed with your move afterwards:
                                 row,column :"""
                );
            } else {
                view.showYourTurn("It's your turn! Enter your move: row,column");
            }
        } else {
            view.showWaitTurn("Opponent's turn. Please wait for your turn.");
        }

        inGame = !gs.gameOver();
        notifyAllLock(gameLock);
    }

    private void onGameEndResponse(NetPacket packet) {
        GameEndResponse end = (GameEndResponse) packet.payload();

        rematchPhase = true;
        inGame = false;
        if (end.finalBoard() != null) view.showBoard(end.finalBoard());

        boolean draw = "Draw".equals(end.reason());
        String message = draw
                ? "Game ended in a draw against " + end.opponent()
                : (username.equals(end.winner()) ? "You won against " + end.opponent() : "You lost against "
                + end.opponent() + " , press Enter and then answer if you want a rematch");
        view.showCallback(message);

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
            inGame = true;
        }else{
            view.showCallback("Match session ended. Returning to lobby...");
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
        resetSessionState();
        notifyAllLock(logoutLock);
        notifyAllLock(gameLock);
        notifyAllLock(loginLock);
        try { clientNetwork.disconnect(); } catch (Exception ignored) {}
    }

    private void onErrorMessageResponse(NetPacket packet) {
        ErrorMessageResponse err = (ErrorMessageResponse) packet.payload();
        view.showCallbackHighlight("ERROR: " + err.message());
        notifyAllLock(loginLock);
        notifyAllLock(gameLock);
    }

    private void onResyncResponse(NetPacket packet) {
        ResyncResponse resp = (ResyncResponse) packet.payload();

        // If this response is negative, we handle it safely without overwriting state
        if (!resyncInProgress) return;

        if (!resp.success()) {
            view.showCallback("Resync attempt rejected: " + resp.message());
            // do NOT touch username, relogCode, loggedIn, etc.
            synchronized (resyncLock) {
                resyncLock.notifyAll(); // wake up waiting Resync thread
            }
            return;
        }

        synchronized (resyncLock) {
            resyncSucceeded = true;
            resyncInProgress = false;
            resyncLock.notifyAll();
        }

        view.showCallback(resp.message());
        loggedIn = true;
        sessionClosing = false;

        InviteNotificationResponse[] invites = resp.pendingInvites();
        lastInvite = (invites != null && invites.length > 0) ? invites[invites.length - 1] : null;

        myStats = resp.myStats();
        relogCode = resp.relogCode();

        onLobbyPlayersFromPayload(resp.lobbyPlayers().players().toArray(new String[0]), false);

        if (resp.currentGameState() != null) {
            inGame = !resp.currentGameState().gameOver();
            if (inGame) gameStartingPromptConsumsed = true;
            onGameStateFromPayload(resp.currentGameState());
        }

        notifyAllLock(gameLock);
        notifyAllLock(loginLock);
    }



    private void onInfoResponse(NetPacket packet){
        if (!(packet.payload() instanceof String)) return;
        view.showCallback((String) packet.payload());
    }

    private void waitLockAndResync(Object lock){
        long sentAt = System.currentTimeMillis();

        new Thread(() -> {
            try {
                Thread.sleep(15_000);
            } catch (InterruptedException ignored) {}

            if (lastServerActivity < sentAt) {
                view.showCallback("No server activity detected. Attempting resync...");
                ResyncWithAuth();
            }
        }).start();
        synchronized (lock) {
            try { lock.wait(0); }
            catch (InterruptedException e) { Thread.currentThread().interrupt();}
        }
    }

//    private boolean waitFor(Object lock, long timeoutMillis) {
//        synchronized (lock) {
//            try { lock.wait(timeoutMillis > 0 ? timeoutMillis : 0); }
//            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
//            return true;
//        }
//    }

    private void notifyAllLock(Object lock) {
        synchronized (lock) { lock.notifyAll(); }
    }

    public boolean getIsInGame() {
        return inGame;
    }



}
