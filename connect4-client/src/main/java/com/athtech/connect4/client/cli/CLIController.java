package com.athtech.connect4.client.cli;

import com.athtech.connect4.client.net.ClientNetworkAdapter;
import com.athtech.connect4.protocol.messaging.*;
import com.athtech.connect4.protocol.payload.*;

public class CLIController {

    private final CLIView view;
    private final CLIInputHandler input;
    private final ClientNetworkAdapter clientNetwork;

    private String username;
    private boolean loggedIn = false;
    private boolean inGame = false;
    private NetPacket lastInvite = null;

    private final Object loginLock = new Object();
    private final Object gameLock = new Object();
    private final Object inviteLock = new Object();

    public CLIController(CLIView view, ClientNetworkAdapter clientNetwork, CLIInputHandler input) {
        this.view = view;
        this.clientNetwork = clientNetwork;
        this.input = input;
        clientNetwork.setListener(this::handleServerPacket);
    }

    /* ======================================================
       ENTRY POINT
       ====================================================== */
    public void run() {
        boolean running = true;
        while (running) {
            resetSessionState();
            loginLoop();
            if (!loggedIn) {
                continue;
            }
            running = mainLoop();
        }
    }

    private void resetSessionState() {
        loggedIn = false;
        inGame = false;
        lastInvite = null;
    }


    /* ======================================================
       LOGIN
       ====================================================== */
    private void loginLoop() {
        while (!loggedIn) {
            view.showLoginScreen();

            view.prompt("Username: ");
            username = input.readLine();

            view.prompt("Password: ");
            String password = input.readLine();

            LoginRequest req = new LoginRequest(username, password);
            clientNetwork.sendPacket(new NetPacket(PacketType.LOGIN_REQUEST, username, req));

            waitFor(loginLock);
        }
    }


    /* ======================================================
       MAIN LOOP (LOBBY/GAME)
       ====================================================== */
    private boolean mainLoop() {
        while (loggedIn) {
            if (!inGame) {
                view.showLobbyMenu();
                handleLobbyChoice(input.readLine());
            } else {
                runGameLoop();
            }
        }
        return true;
    }

    private void handleLobbyChoice(String choice) {
        switch (choice) {
            case "1" -> sendInvite();
            case "2" -> handleIncomingInviteDecision();
            case "0" -> logout();
            default -> view.show("Invalid option.");
        }
    }


    /* ======================================================
       LOBBY & INVITES
       ====================================================== */
    private void sendInvite() {
        view.showInvitePrompt();
        String target = input.readLine();

        InviteRequest req = new InviteRequest(target);
        clientNetwork.sendPacket(new NetPacket(PacketType.INVITE_REQUEST, username, req));
    }

    private void handleIncomingInviteDecision() {
        synchronized (inviteLock) {
            if (lastInvite == null) {
                view.show("No pending invites.");
                return;
            }

            InviteNotification notif = (InviteNotification) lastInvite.payload();
            view.show("Invite from: " + notif.fromUsername());

            view.showAcceptPrompt();
            boolean accept = input.readLine().equalsIgnoreCase("y");

            InviteDecisionRequest req =
                    new InviteDecisionRequest(notif.fromUsername(), accept);

            clientNetwork.sendPacket(new NetPacket(PacketType.INVITE_DECISION_REQUEST, username, req));
            lastInvite = null;
        }
    }


    /* ======================================================
       GAME LOOP
       ====================================================== */
    private void runGameLoop() {
        view.showGameStart();

        while (inGame) {
            view.showGamePromptRow();
            int row = input.readInt();

            view.showGamePromptCol();
            int col = input.readInt();

            Move move = new Move(row, col);
            clientNetwork.sendPacket(new NetPacket(PacketType.MOVE_REQUEST, username, move));

            waitFor(gameLock);
        }
    }


    /* ======================================================
       NETWORK PACKET HANDLING
       ====================================================== */
    private void handleServerPacket(NetPacket packet) {
        switch (packet.type()) {
            case LOGIN_RESPONSE -> onLoginResponse(packet);
            case LOBBY_PLAYERS -> onLobbyPlayers(packet);
            case INVITE_NOTIFICATION -> onInviteNotification(packet);
            case INVITE_RESPONSE -> onInviteResponse(packet);
            case INVITE_DECISION_RESPONSE -> onInviteDecisionResponse(packet);
            case GAME_STATE -> onGameState(packet);
            case GAME_END -> onGameEnd(packet);
            case ERROR_MESSAGE -> onError(packet);
            default -> view.show("Unhandled packet: " + packet.type());
        }
    }

    private void onLoginResponse(NetPacket packet) {
        LoginResponse resp = (LoginResponse) packet.payload();
        loggedIn = resp.success();
        view.show(resp.message());
        notify(loginLock);
    }

    private void onLobbyPlayers(NetPacket packet) {
        LobbyPlayers lobby = (LobbyPlayers) packet.payload();
        view.show("Lobby: " + String.join(", ", lobby.players()));
    }

    private void onInviteNotification(NetPacket packet) {
        synchronized (inviteLock) {
            lastInvite = packet;
        }
        InviteNotification notif = (InviteNotification) packet.payload();
        view.show("Invite from: " + notif.fromUsername());
    }

    private void onInviteResponse(NetPacket packet) {
        InviteResponse resp = (InviteResponse) packet.payload();
        view.show(resp.delivered() ? "Invite delivered." : "Invite failed: " + resp.reason());
    }

    private void onInviteDecisionResponse(NetPacket packet) {
        InviteDecisionResponse resp = (InviteDecisionResponse) packet.payload();
        if (resp.accepted()) {
            inGame = true;
            view.show("Invitation accepted. Starting match...");
            notify(gameLock);
        } else {
            view.show("Invitation declined.");
        }
    }

    private void onGameState(NetPacket packet) {
        GameState gs = (GameState) packet.payload();
        view.show("Game updated.");
        inGame = true;
        notify(gameLock);
    }

    private void onGameEnd(NetPacket packet) {
        GameEnd end = (GameEnd) packet.payload();
        inGame = false;
        view.show(end.reason());
        notify(gameLock);
    }

    private void onError(NetPacket packet) {
        ErrorMessage err = (ErrorMessage) packet.payload();
        view.show("ERROR: " + err.message());
    }


    /* ======================================================
       HELPERS
       ====================================================== */
    private void logout() {
        loggedIn = false;
        clientNetwork.disconnect();
    }

    private void waitFor(Object lock) {
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ignored) {}
        }
    }

    private void notify(Object lock) {
        synchronized (lock) {
            lock.notify();
        }
    }
}
