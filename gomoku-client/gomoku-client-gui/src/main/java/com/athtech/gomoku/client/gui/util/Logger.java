package com.athtech.gomoku.client.gui.util;


import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Logger {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Prints an info message with timestamp and flushes immediately.
     */
    public static void info(String message) {
        System.out.println("[INFO " + LocalTime.now().format(TIME_FORMAT) + "] " + message);
        System.out.flush();
    }

    /**
     * Prints a warning message with timestamp.
     */
    public static void warn(String message) {
        System.out.println("[WARN " + LocalTime.now().format(TIME_FORMAT) + "] " + message);
        System.out.flush();
    }

    /**
     * Prints an error message with timestamp.
     */
    public static void error(String message) {
        System.err.println("[ERROR " + LocalTime.now().format(TIME_FORMAT) + "] " + message);
        System.err.flush();
    }

    /**
     * Prints a debug message with timestamp.
     */
    public static void debug(String message) {
        System.out.println("[DEBUG " + LocalTime.now().format(TIME_FORMAT) + "] " + message);
        System.out.flush();
    }
}