package com.athtech.gomoku.server.game;

public class Game {

    private static final int ROWS = 10;
    private static final int COLS = 10;
    private static final int WIN_COUNT = 4;

    private final char[][] board;
    private String currentPlayer;
    private boolean gameOver;
    private final String player1;
    private final String player2;
    private String winner;

    public Game(String p1, String p2) {
        this.board = new char[ROWS][COLS];
        this.currentPlayer = p1;
        this.gameOver = false;
        this.player1 = p1;
        this.player2 = p2;
        this.winner = null;

        for (int i = 0; i < ROWS; i++)
            for (int j = 0; j < COLS; j++)
                board[i][j] = ' ';
    }

    public synchronized String getPlayer1() {
        return player1;
    }

    public synchronized String getPlayer2() {
        return player2;
    }

    public synchronized boolean makeMove(int row, int col) {
        if (gameOver || row < 0 || row >= ROWS || col < 0 || col >= COLS) return false;
        if (board[row][col] != ' ') return false;

        board[row][col] = currentPlayer.equals(player1) ? 'X' : 'O';

        if (checkWin(row, col)) {
            gameOver = true;
            winner = currentPlayer;
        } else if (isBoardFull()) {
            gameOver = true;
            winner = null;
        } else {
            switchTurn();
        }
        return true;
    }

    private synchronized void switchTurn() {
        currentPlayer = currentPlayer.equals(player1) ? player2 : player1;
    }

    public synchronized char[][] getBoardCopy() {
        char[][] copy = new char[ROWS][COLS];
        for (int i = 0; i < ROWS; i++) System.arraycopy(board[i], 0, copy[i], 0, COLS);
        return copy;
    }

    public synchronized String getCurrentPlayer() {
        return currentPlayer;
    }

    public synchronized boolean isGameOver() {
        return gameOver;
    }

    public synchronized String getWinner() {
        return winner;
    }

    private synchronized boolean isBoardFull() {
        for (int i = 0; i < ROWS; i++)
            for (int j = 0; j < COLS; j++)
                if (board[i][j] == ' ')
                    return false;
        return true;
    }

    private synchronized boolean checkWin(int row, int col) {
        char symbol = board[row][col];
        return checkDirection(row, col, symbol, 1, 0)  // vertical
                || checkDirection(row, col, symbol, 0, 1)  // horizontal
                || checkDirection(row, col, symbol, 1, 1)  // diagonal /
                || checkDirection(row, col, symbol, 1, -1); // diagonal \
    }

    private synchronized boolean checkDirection(int row, int col, char symbol, int dRow, int dCol) {
        int count = 1;
        count += countDirection(row, col, symbol, dRow, dCol);
        count += countDirection(row, col, symbol, -dRow, -dCol);
        return count >= WIN_COUNT;
    }

    private synchronized int countDirection(int row, int col, char symbol, int dRow, int dCol) {
        int r = row + dRow, c = col + dCol, count = 0;
        while (r >= 0 && r < ROWS && c >= 0 && c < COLS && board[r][c] == symbol) {
            count++;
            r += dRow;
            c += dCol;
        }
        return count;
    }

    public synchronized void forceEnd(String winner) {
        if (gameOver) return;
        this.gameOver = true;
        this.winner = winner;
    }

    public static int getWinCount() {
        return WIN_COUNT;
    }
}
