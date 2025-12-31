package com.athtech.connect4.client.cli;

import java.util.Scanner;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Single responsibility: read user input. Keeps Scanner local so
 * other classes don't mix I/O concerns.
 */
public class CLIInputHandler {
    private final BooleanSupplier isInGame;
    private final Scanner scanner = new Scanner(System.in);

    public CLIInputHandler(BooleanSupplier isInGame) {
        this.isInGame = isInGame;
    }

    public String readLine() {
        return scanner.nextLine().trim();
    }

    public int readIntOrQuit() {
        while (true) {
            String s = scanner.nextLine().trim();
            if ("q".equalsIgnoreCase(s)) return -1;
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.print("Invalid number. Try again or type 'q' to go back: ");
            }
        }
    }


    public int[] readMove() {

        while (true) {
            String line = scanner.nextLine().trim();
            if (!isInGame.getAsBoolean() || line.isEmpty()) return null;
            if ("q".equalsIgnoreCase(line)) return null; // treat as cancel/back
            String[] parts = line.split(",");
            if (parts.length != 2) {
                System.out.print("Invalid format. Enter as row,column or 'q' to leave the game: ");
                continue;
            }
            try {
                int row = Integer.parseInt(parts[0].trim());
                int col = Integer.parseInt(parts[1].trim());
                return new int[]{row, col};
            } catch (NumberFormatException e) {
                System.out.print("Invalid numbers. Enter as row,column or 'q' to leave the game: ");
            }
        }
    }

    // CLIInputHandler
    public String readMoveRaw() {
        return scanner.nextLine().trim();
    }

    public String readChoice() {
        String choice = scanner.nextLine().trim();
        if ("q".equalsIgnoreCase(choice)) return "q";
        return choice;
    }
}

