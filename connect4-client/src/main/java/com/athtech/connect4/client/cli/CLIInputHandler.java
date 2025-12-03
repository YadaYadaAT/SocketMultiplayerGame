package com.athtech.connect4.client.cli;

import java.util.Scanner;

public class CLIInputHandler {

    private final Scanner scanner = new Scanner(System.in);

    public String readLine() {
        return scanner.nextLine();
    }

    public int readInt() {
        return Integer.parseInt(scanner.nextLine());
    }
}