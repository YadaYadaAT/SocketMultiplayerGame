package com.athtech.connect4.client.cli;

import java.util.Scanner;

/**
 * Single responsibility: read user input. Keeps Scanner local so
 * other classes don't mix I/O concerns.
 */
public class CLIInputHandler {

    private final Scanner scanner = new Scanner(System.in);

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
     * Read a menu choice (single token). Returns the trimmed token.
     * Keeps the caller in charge of validation.
     */
    public String readChoice() {
        return scanner.nextLine().trim();
    }
}
