package com.athtech.connect4.client.cli;

import com.athtech.connect4.client.net.ClientNetworkAdapter;
import com.athtech.connect4.protocol.messaging.*;
import com.athtech.connect4.protocol.payload.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CLIController {
    private final CLIView view;
    private final ClientNetworkAdapter clientNetwork;
    private final CLIInputHandler input;

    // Session state
    private volatile boolean loggedIn = false;
    private volatile boolean sessionClosing = false;
    private volatile boolean inGame = false;
    private volatile InviteNotificationResponse lastInvite = null;
    private String username;
    private String relogCode;

    private volatile String pendingRematchRequester = null;
    private volatile String pendingRematchOpponent = null;

    private volatile List<String> lobbyPlayers = new ArrayList<>();
    private volatile PlayerStatsResponse myStats;

    // Locks
    private final Object loginLock = new Object();
    private final Object logoutLock = new Object();
    private final Object gameLock = new Object();
    private final Object inviteLock = new Object();
    private boolean shouldAppExit = false;

    public CLIController(CLIView view, ClientNetworkAdapter clientNetwork, CLIInputHandler input) {
        this.view = view;
        this.clientNetwork = clientNetwork;
        this.input = input;
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
        pendingRematchRequester = null;
        pendingRematchOpponent = null;
        lobbyPlayers.clear();
        myStats = null;
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
        waitFor(loginLock, 8000);
    }

    private void attemptSignupFlow() {
        view.showSignupPrompt();
        view.prompt("Choose username: ");
        String desired = input.readLine();
        view.prompt("Choose password: ");
        String pwd = input.readLine();

        clientNetwork.sendPacket(new NetPacket(PacketType.SIGNUP_REQUEST, desired, new SignupRequest(desired, pwd)));
        waitFor(loginLock, 8000);
    }

    private void mainLoop() {
        while (loggedIn) {
            if (sessionClosing) { waitFor(logoutLock, 1000); continue; }

            // Rematch offer received
            if (!inGame && pendingRematchRequester != null) handleIncomingRematchRequest();

            // Rematch opportunity
            if (!inGame && pendingRematchOpponent != null) handleRematchPrompt();

            // Lobby interaction
            if (!inGame && pendingRematchRequester == null && pendingRematchOpponent == null) handleLobby();

            // Game loop
            if (inGame) runGameLoop();
        }
    }

    private void handleIncomingRematchRequest() {
        view.show("Rematch offer from: " + pendingRematchRequester);
        view.prompt("Accept rematch? (y/n): ");
        boolean accept = input.readLine().trim().equalsIgnoreCase("y");
        clientNetwork.sendPacket(new NetPacket(PacketType.REMATCH_DECISION_REQUEST, username,
                new RematchDecisionRequest(pendingRematchRequester, accept)));
        pendingRematchRequester = null;
    }

    private void handleRematchPrompt() {
        view.showRematchPrompt();
        boolean wantRematch = input.readLine().trim().equalsIgnoreCase("y");
        if (wantRematch) {
            clientNetwork.sendPacket(new NetPacket(PacketType.REMATCH_REQUEST, username,
                    new RematchRequest()));
            view.show("Rematch request sent.");
        }
        pendingRematchOpponent = null;
    }

    private void handleLobby() {
        view.showLobbyMenu();
        String choice = input.readChoice();
        switch (choice) {
            case "1" -> sendInviteRequest();
            case "2" -> handleReceivedInviteRequest();
            case "3" -> requestLobbyPlayers();
            case "4" -> requestPlayerStats();
            case "0" -> requestLogout();
            default -> view.show("Invalid option.");
        }
    }

    private void sendInviteRequest() {
        if (inGame) return;
        List<String> snapshot = requestLobbyPlayers();
        if (snapshot.isEmpty()) return;

        view.prompt("Choose a player to invite (number): ");
        int choice = input.readInt();
        if (choice < 1 || choice > snapshot.size()) { view.show("Invalid choice."); return; }

        clientNetwork.sendPacket(new NetPacket(PacketType.INVITE_REQUEST, username,
                new InviteRequest(snapshot.get(choice - 1))));
    }

    private void handleReceivedInviteRequest() {
        synchronized (inviteLock) {
            if (inGame) {
                System.out.println("in game");
                return;
            }
            if (lastInvite == null){
                System.out.println("you have no invites");
                return;
            }
//            if (inGame || lastInvite == null){
//                System.out.println("");
//                return;
//            }

            view.show("Invite from: " + lastInvite.fromUsername());
            view.showAcceptPrompt();
            boolean accept = input.readLine().trim().equalsIgnoreCase("y");

            clientNetwork.sendPacket(new NetPacket(PacketType.INVITE_DECISION_REQUEST, username,
                    new InviteDecisionRequest(lastInvite.fromUsername(), accept)));
            lastInvite = null;
        }
    }

    private List<String> requestLobbyPlayers() {
        if (inGame) return List.of();
        if (lobbyPlayers.isEmpty()) { view.show("No players available."); return List.of(); }
        for (int i = 0; i < lobbyPlayers.size(); i++) view.show((i + 1) + ") " + lobbyPlayers.get(i));
        return new ArrayList<>(lobbyPlayers);
    }

    private void requestPlayerStats() {
        if (inGame || myStats == null) return;
        view.show("Stats → Played: " + myStats.gamesPlayed() +
                ", Wins: " + myStats.wins() +
                ", Losses: " + myStats.losses() +
                ", Draws: " + myStats.draws());
    }

    private void requestLogout() {
        if (inGame || sessionClosing) { view.show("Logout already in progress..."); return; }

        sessionClosing = true;
        clientNetwork.sendPacket(new NetPacket(PacketType.LOGOUT_REQUEST, username, new LogoutRequest()));
        waitFor(logoutLock, 4000);

        resetSessionState();
        try { clientNetwork.disconnect(); } catch (Exception ignored) {}
    }

    private void runGameLoop() {
        view.showGameStart();
        while (inGame && loggedIn && !sessionClosing) {
            int row = readGameInt("Row: ");
            if (!inGame) break;
            int col = readGameInt("Column: ");
            if (!inGame) break;

            clientNetwork.sendPacket(new NetPacket(PacketType.MOVE_REQUEST, username, new MoveRequest(row, col)));
            boolean notified = waitFor(gameLock, 60_000);
            if (!notified) reconnectAndFetchState();
        }
    }

    private int readGameInt(String prompt) {
        while (true) {
            view.prompt(prompt);
            try { return Integer.parseInt(input.readLine().trim()); }
            catch (NumberFormatException e) { view.show("Invalid number. Try again."); }
        }
    }

    private void handleServerPacket(NetPacket packet) {
        if (sessionClosing && packet.type() != PacketType.LOGOUT_RESPONSE) return;

        switch (packet.type()) {
            case INFO_RESPONSE -> onInfoResponse(packet);
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
            case GAME_END_RESPONSE -> onGameEndResponse(packet);
            case MOVE_REJECTED_RESPONSE -> onMoveRejectedResponse(packet);
            case REMATCH_RESPONSE -> onRematchResponse(packet);
            case REMATCH_NOTIFICATION_RESPONSE -> onRematchNotificationResponse(packet);
            case RECONNECT_RESPONSE -> onReconnectResponse(packet);
            case ERROR_MESSAGE_RESPONSE -> onErrorMessageResponse(packet);
            default -> view.show("Unhandled packet: " + packet.type());
        }
    }

    // --------- Server response handlers ---------
    private void onInfoResponse(NetPacket packet) {
        Object payload = packet.payload();
        view.show("INFO: " + (payload != null ? payload.toString() : "No details"));
    }

    private void onLoginResponse(NetPacket packet) {
        LoginResponse resp = (LoginResponse) packet.payload();
        loggedIn = resp.success();
        view.show(resp.message());
        if (loggedIn) { relogCode = resp.relogCode(); myStats = resp.stats(); }
        notifyAllLock(loginLock);
    }

    private void onSignupResponse(NetPacket packet) {
        SignupResponse resp = (SignupResponse) packet.payload();
        view.show(resp.message());
        notifyAllLock(loginLock);
    }

    private void onLobbyPlayersResponse(NetPacket packet) {
        var lobP = (String[]) packet.payload();
        lobbyPlayers = new ArrayList<>(Arrays.asList(lobP));
        view.show("Lobby: " + String.join(", ", lobbyPlayers));
    }

    private void onInviteNotificationResponse(NetPacket packet) {
        lastInvite = (InviteNotificationResponse) packet.payload();
        view.show("Invite from: " + lastInvite.fromUsername());
    }

    private void onInviteResponse(NetPacket packet) {
        var resp = (InviteResponse) packet.payload();
        view.show(resp.delivered() ? "Invite delivered." : "Invite failed: " + resp.reason());
    }

    private void onInviteDecisionResponse(NetPacket packet) {
        var resp = (InviteDecisionResponse) packet.payload();
        if (resp.accepted()) { view.show("Invitation accepted. Match starting..."); inGame = true; }
        else view.show("Invitation declined.");
        notifyAllLock(gameLock);
    }


    private void onGameStartResponse(NetPacket packet) {
        GameStateResponse gs = (GameStateResponse) packet.payload();
        view.showBoard(gs.board());
        view.show("Turn: " + gs.currentPlayer());
        inGame = !gs.gameOver();
        notifyAllLock(gameLock);
    }

    private void onGameStateResponse(NetPacket packet) {
        GameStateResponse gs = (GameStateResponse) packet.payload();
        view.showBoard(gs.board());
        view.show("Turn: " + gs.currentPlayer());
        inGame = !gs.gameOver();
        notifyAllLock(gameLock);
    }

    private void onGameEndResponse(NetPacket packet) {
        GameEndResponse end = (GameEndResponse) packet.payload();
        if (end.finalBoard() != null) view.showBoard(end.finalBoard());
        String message = "Draw".equals(end.reason()) ? "Game ended in a draw against " + end.opponent()
                : (username.equals(end.winner()) ? "You won against " + end.opponent() : "You lost against " + end.opponent());
        view.show(message);
        pendingRematchOpponent = end.opponent();
        inGame = false;
        notifyAllLock(gameLock);
    }

    private void onRematchResponse(NetPacket packet) {
        RematchResponse resp = (RematchResponse) packet.payload();
        view.show("Rematch response: " + (resp.accepted() ? "accepted" : "declined") + ". " + resp.message());
    }

    private void onRematchNotificationResponse(NetPacket packet) {
        var offer = (RematchNotificationResponse) packet.payload();
        view.show("Rematch offer from: " + offer.requester());
        pendingRematchRequester = offer.requester();
    }

    private void onMoveRejectedResponse(NetPacket packet) {
        MoveRejectedResponse rej = (MoveRejectedResponse) packet.payload();
        view.show("Move rejected: " + rej.reason());
        notifyAllLock(gameLock);
    }

    private void onPlayerStatsResponse(NetPacket packet) { myStats = (PlayerStatsResponse) packet.payload(); }

    private void onLogoutResponse(NetPacket packet) {
        LogoutResponse resp = (LogoutResponse) packet.payload();
        view.show(resp.message());
        sessionClosing = false;
        resetSessionState();
        notifyAllLock(logoutLock);
        notifyAllLock(gameLock);
        notifyAllLock(loginLock);
        try { clientNetwork.disconnect(); } catch (Exception ignored) {}
    }

    private void onErrorMessageResponse(NetPacket packet) {
        ErrorMessageResponse err = (ErrorMessageResponse) packet.payload();
        view.show("ERROR: " + err.message());
        notifyAllLock(loginLock);
        notifyAllLock(gameLock);
    }

    private void onReconnectResponse(NetPacket packet) {
        ReconnectResponse resp = (ReconnectResponse) packet.payload();
        if (!resp.success()) { view.show(resp.message()); resetSessionState(); notifyAllLock(loginLock); return; }

        view.show(resp.message());
        loggedIn = true;
        sessionClosing = false;
        relogCode = resp.relogCode();
        myStats = resp.myStats();
        lobbyPlayers = resp.lobbyPlayers() != null ? Arrays.asList(resp.lobbyPlayers()) : new ArrayList<>();
        if (resp.currentGameState() != null) {
            inGame = !resp.currentGameState().gameOver();
            view.show("Game restored. Turn: " + resp.currentGameState().currentPlayer());
        }
        InviteNotificationResponse[] invites = resp.pendingInvites();
        lastInvite = (invites != null && invites.length > 0) ? invites[invites.length - 1] : null;
        notifyAllLock(loginLock);
        notifyAllLock(gameLock);
    }

    private void reconnectAndFetchState() {
        if (username == null || relogCode == null) { view.show("Cannot reconnect automatically."); resetSessionState(); return; }

        view.show("Attempting to reconnect using relog code...");
        boolean success = clientNetwork.attemptReconnectWithRelogCode(username, relogCode);
        if (!success) { view.show("Reconnection failed."); resetSessionState(); return; }

        sessionClosing = false;
        view.show("Reconnected successfully.");
        notifyAllLock(gameLock);
        notifyAllLock(loginLock);
    }

    // --------- Utilities ---------
    private boolean waitFor(Object lock, long timeoutMillis) {
        synchronized (lock) {
            try { lock.wait(timeoutMillis > 0 ? timeoutMillis : 0); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            return true;
        }
    }

    private void notifyAllLock(Object lock) {
        synchronized (lock) { lock.notifyAll(); }
    }
}
