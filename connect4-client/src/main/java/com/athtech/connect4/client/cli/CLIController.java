package com.athtech.connect4.client.cli;

import com.athtech.connect4.client.net.ClientNetworkAdapter;
import com.athtech.connect4.protocol.messaging.NetPacket;
import com.athtech.connect4.protocol.messaging.PacketType;
import com.athtech.connect4.protocol.payload.*;

import java.util.Scanner;

public class CLIController {

    private final CLIView view;
    private final ClientNetworkAdapter clientNetwork;

    private String username = "User";
    private boolean loggedIn = false;

    private InviteNotification pendingInvite = null;

    private final Object loginLock = new Object();

    public CLIController(CLIView view, ClientNetworkAdapter clientNetwork) {
        this.view = view;
        this.clientNetwork = clientNetwork;

        clientNetwork.setListener(this::handleServerPacket);
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            // --- LOGIN LOOP ---
            while (!loggedIn) {
                view.displayMessage("\n--- LOGIN ---");
                System.out.print("Username: ");
                username = scanner.nextLine();
                System.out.print("Password: ");
                String password = scanner.nextLine();

                NetPacket loginPacket = new NetPacket(PacketType.LOGIN_REQUEST,username,
                        new LoginRequest(username, password));
                clientNetwork.sendPacket(loginPacket);

                synchronized (loginLock) {
                    try {
                        loginLock.wait(); // wait for server response
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                if (!loggedIn) {
                    view.displayMessage("Login failed. Try again.");
                }
            }

            // --- MAIN MENU ---
            displayMenu();
            String choice = scanner.nextLine();
            switch (choice) {
                case "1" -> handleInvite(scanner);
                case "2" -> handleMakeMove(scanner);
                case "3" -> checkPendingInvite(scanner);
                case "0" -> {
                    running = false;
                    loggedIn = false;
                }
                default -> view.displayMessage("Invalid choice.");
            }
        }

        clientNetwork.disconnect();
    }

    private void handleServerPacket(NetPacket packet) {
        switch (packet.type()) {
            case LOGIN_RESPONSE -> {
                LoginResponse resp = (LoginResponse) packet.payload();
                loggedIn = resp.success();
                view.displayMessage(resp.message());
                synchronized (loginLock) {
                    loginLock.notify();
                }
            }

            case INVITE_NOTIFICATION -> {
                InviteNotification notif = (InviteNotification) packet.payload();
                pendingInvite = notif;
                view.displayMessage("You received an invite from: " + notif.fromUsername());
            }

            case INVITE_RESPONSE -> {
                InviteResponse resp = (InviteResponse) packet.payload();
                view.displayMessage(resp.delivered()
                        ? "Invite delivered."
                        : "Invite failed: " + resp.reason());
            }

            case INVITE_DECISION_RESPONSE -> {
                InviteDecisionResponse resp = (InviteDecisionResponse) packet.payload();
                view.displayMessage(resp.accepted()
                        ? "Invitation accepted! Match starting."
                        : "Invitation declined.");
            }

            case GAME_STATE -> {
                GameState gs = (GameState) packet.payload();
                view.displayMessage("Game state updated.");
            }

            case ERROR_MESSAGE -> {
                ErrorMessage err = (ErrorMessage) packet.payload();
                view.displayMessage("SERVER ERROR: " + err.message());
            }

            default -> view.displayMessage("Unhandled packet: " + packet.type());
        }
    }

    private void displayMenu() {
        view.displayMessage("\n--- MAIN MENU ---");
        view.displayMessage("1. Invite Player");
        view.displayMessage("2. Make Move");
        view.displayMessage("3. Check Pending Invite");
        view.displayMessage("0. Logout / Quit");
        view.displayMessage("Enter choice: ");
    }

    private void handleInvite(Scanner scanner) {
        view.displayMessage("Enter player to invite:");
        String target = scanner.nextLine();
        InviteRequest req = new InviteRequest(target);
        clientNetwork.sendPacket(new NetPacket(PacketType.INVITE_REQUEST,username, req));
    }

    private void handleMakeMove(Scanner scanner) {
        view.displayMessage("Enter row:");
        int row = Integer.parseInt(scanner.nextLine());
        view.displayMessage("Enter column:");
        int col = Integer.parseInt(scanner.nextLine());

        Move move = new Move(row, col);
        clientNetwork.sendPacket(new NetPacket(PacketType.MOVE_REQUEST,username, move));
    }

    private void checkPendingInvite(Scanner scanner) {
        if (pendingInvite == null) {
            view.displayMessage("No pending invites.");
            return;
        }

        view.displayMessage("Pending invite from: " + pendingInvite.fromUsername());
        view.displayMessage("Accept invite? (y/n):");
        String resp = scanner.nextLine();
        boolean accepted = resp.equalsIgnoreCase("y");
        clientNetwork.sendPacket(new NetPacket(PacketType.INVITE_DECISION_REQUEST,username,
                new InviteDecisionRequest(pendingInvite.fromUsername(), accepted)));

        pendingInvite = null; // clear after decision
    }
}
