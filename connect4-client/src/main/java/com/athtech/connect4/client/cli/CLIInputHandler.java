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


    /** Read a full line (blocks). Trims result. */
    public String readLine() {
        return scanner.nextLine().trim();
    }

    /** Read an integer with validation; reprompts until a valid int is entered. */
    public int readInt() {
        while (true) {
            String s = scanner.nextLine().trim();
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.print("Invalid number. Try again: ");
            }
        }
    }

    /**
     * Reads a move input in the form "row,column" and returns it as an int array [row, col].
     * Keeps prompting until a valid input is entered.
     */
    public int[] readMove() {
        while (true) {
            String line = scanner.nextLine().trim();
            if (!isInGame.getAsBoolean() || line.isEmpty()) return null;
            String[] parts = line.split(",");
            if (parts.length != 2) {
                System.out.print("Invalid format. Enter as row,column: ");
                continue;
            }
            try {
                int row = Integer.parseInt(parts[0].trim());
                int col = Integer.parseInt(parts[1].trim());
                return new int[]{row, col};
            } catch (NumberFormatException e) {
                System.out.print("Invalid numbers. Enter as row,column: ");
            }
        }
    }

    /**
     * Read a menu choice (single token). Returns the trimmed token.
     * Keeps the caller in charge of validation.
     */
    public String readChoice() {
        return scanner.nextLine().trim();
    }
}
