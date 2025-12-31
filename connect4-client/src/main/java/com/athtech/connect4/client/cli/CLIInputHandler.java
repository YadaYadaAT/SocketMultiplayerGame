package com.athtech.connect4.client.cli;

import java.util.Scanner;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Single responsibility: read user input. Keeps Scanner local so
 * other classes don't mix I/O concerns.
 */
public class CLIInputHandler {

    private final Scanner scanner = new Scanner(System.in);

    public CLIInputHandler() {

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

