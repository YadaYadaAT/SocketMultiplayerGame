package com.athtech.connect4.client.cli;

import com.athtech.connect4.protocol.payload.BoardState;

/** All display logic lives here. Keep it simple so view can be swapped later. */
public class CLIView {

    // ANSI escape codes for colors
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";    // async messages from server
    private static final String GREEN = "\u001B[32m";   // send / local confirmations
    private static final String YELLOW = "\u001B[33m";  // highlights / warnings

    // Console lock to synchronize all outputs
    private final Object consoleLock = new Object();

    public void show(String msg) {
        synchronized (consoleLock) {
            System.out.println(msg);
        }
    }

    public void prompt(String msg) {
        synchronized (consoleLock) {
            System.out.print(msg);
        }
    }

    /** --- For server callback messages (async, from on* methods) --- */
    public void showCallback(String msg) {
        synchronized (consoleLock) {
            System.out.println("                                             " + CYAN + ">> " + msg + RESET);
        }
    }

    /** ---  --- */
    public void showGameStarted(String msg) {
        synchronized (consoleLock) {
            System.out.println(">> " + msg + RESET);
        }
    }

    /** --- For your turn highlighting --- */
    public void showYourTurn(String msg) {
        synchronized (consoleLock) {
            System.out.println(GREEN + ">> " + msg + RESET);
        }
    }

    /** --- For wait turn --- */
    public void showWaitTurn(String msg) {
        synchronized (consoleLock) {
            System.out.println(CYAN + ">> " + msg + RESET);
        }
    }

    /** --- For send/confirmation messages (local thread actions) --- */
    public void showSend(String msg) {
        synchronized (consoleLock) {
            System.out.println(GREEN + ">> " + msg + RESET);
        }
    }

    /** Optional: for high-priority or warning messages */
    public void showCallbackHighlight(String msg) {
        synchronized (consoleLock) {
            System.out.println(YELLOW + ">> " + msg + RESET);
        }
    }

    // Login screen with choices: Login / Signup / Exit
    public void showLoginScreen() {
        synchronized (consoleLock) {
            System.out.println("\n--- LOGIN ---");
            System.out.println("1. Login");
            System.out.println("2. Signup");
            System.out.println("0. Exit");
            System.out.print("Choice: ");
        }
    }

    public void showSignupPrompt() {
        synchronized (consoleLock) {
            System.out.println("\n--- SIGNUP ---");
        }
    }

    // Lobby menu
    public void showLobbyMenu() {
        synchronized (consoleLock) {
            System.out.println("\n--- LOBBY MENU ---");
            System.out.println("1. Invite Player");
            System.out.println("2. Check Last Invite");
            System.out.println("3. Refresh Lobby Players");
            System.out.println("4. View My Stats");
            System.out.println("0. Logout");
            System.out.print("Enter choice: ");
        }
    }

    public void showGameStart() {
        synchronized (consoleLock) {
            System.out.println("\n (Game mode enabled)");
        }
    }

    public void showGamePromptRow() {
        synchronized (consoleLock) {
            System.out.print("Row: ");
        }
    }

    public void showGamePromptCol() {
        synchronized (consoleLock) {
            System.out.print("Column: ");
        }
    }

    public void showInvitePrompt() {
        synchronized (consoleLock) {
            System.out.print("Player to invite: ");
        }
    }

    public void showAcceptPrompt() {
        synchronized (consoleLock) {
            System.out.print("Accept? (y/n): ");
        }
    }

    public void showRematchPrompt() {
        synchronized (consoleLock) {
            System.out.print("Request rematch? (y/n): ");
        }
    }

    public void showBoard(BoardState board) {
        synchronized (consoleLock) {
            System.out.println();
            System.out.println("   0   1   2   3   4   5   6");
            System.out.println(" +---+---+---+---+---+---+---+");

            char[][] cells = board.cells();

            for (int r = 0; r < cells.length; r++) {
                System.out.print(r + "|");
                for (int c = 0; c < cells[r].length; c++) {
                    char cell = cells[r][c];
                    System.out.print(" " + (cell == '\0' || cell == ' ' ? '.' : cell) + " |");
                }
                System.out.println();
                System.out.println(" +---+---+---+---+---+---+---+");
            }
        }
    }
}
