package com.athtech.connect4.client.cli;

/** All display logic lives here. Keep it simple so view can be swapped later. */
public class CLIView {

    public void show(String msg) {
        System.out.println(msg);
    }

    public void prompt(String msg) {
        System.out.print(msg);
    }

    // Login screen with choices: Login / Signup / Exit
    public void showLoginScreen() {
        System.out.println("\n--- LOGIN ---");
        System.out.println("1. Login");
        System.out.println("2. Signup");
        System.out.println("0. Exit");
        System.out.print("Choice: ");
    }

    public void showSignupPrompt() {
        System.out.println("\n--- SIGNUP ---");
    }

    // Lobby menu
    public void showLobbyMenu() {
        System.out.println("\n--- LOBBY MENU ---");
        System.out.println("1. Invite Player");
        System.out.println("2. Check Last Invite");
        System.out.println("3. Refresh Lobby Players");
        System.out.println("0. Logout");
        System.out.print("Enter choice: ");
    }

    public void showGameStart() {
        System.out.println("\n--- GAME STARTED ---");
    }

    public void showGamePromptRow() {
        System.out.print("Row: ");
    }

    public void showGamePromptCol() {
        System.out.print("Column: ");
    }

    public void showInvitePrompt() {
        System.out.print("Player to invite: ");
    }

    public void showAcceptPrompt() {
        System.out.print("Accept? (y/n): ");
    }

    public void showRematchPrompt() {
        System.out.print("Request rematch? (y/n): ");
    }
}
