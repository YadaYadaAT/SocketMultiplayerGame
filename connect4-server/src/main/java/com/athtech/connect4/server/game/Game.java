package com.athtech.connect4.server.game;

public class Game {
    private final char[][] board;
    private String currentPlayer;
    private boolean gameOver;

    public Game() {
        board = new char[6][7]; // standard Connect4
        currentPlayer = "player1";
        gameOver = false;
        // initialize board with empty cells, etc.
    }

    public boolean makeMove(int row,int col) {
        // logic to place a piece, update currentPlayer, set gameOver
        return true; // or false if invalid
    }

    public char[][] getBoardCopy() {
        char[][] copy = new char[board.length][board[0].length];
        for (int i = 0; i < board.length; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, board[0].length);
        }
        return copy;
    }

    public String getCurrentPlayer() {
        return currentPlayer;
    }

    public boolean isGameOver() {
        return gameOver;
    }
}
