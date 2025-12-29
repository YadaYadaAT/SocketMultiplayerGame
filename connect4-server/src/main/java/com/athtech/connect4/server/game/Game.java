package com.athtech.connect4.server.game;

public class Game {

    private final char[][] board;
    private String currentPlayer;
    private boolean gameOver;
    private final String player1;
    private final String player2;
    private String winner; // new field to track winner

    public String getPlayer1() {
        return player1;
    }

    public String getPlayer2() {
        return player2;
    }

    public Game(String p1, String p2) {
        this.board = new char[6][7]; // standard Connect4
        this.currentPlayer = p1;
        this.gameOver = false;
        this.player1 = p1;
        this.player2 = p2;
        this.winner = null;

        for (int i = 0; i < 6; i++)
            for (int j = 0; j < 7; j++)
                board[i][j] = ' ';
    }

    public boolean makeMove(int row, int col) {
        if (gameOver || row < 0 || row >= 6 || col < 0 || col >= 7) return false;
        if (board[row][col] != ' ') return false;

        board[row][col] = currentPlayer.equals(player1) ? 'X' : 'O';

        if (checkWin(row, col)) {
            gameOver = true;
            winner = currentPlayer;
        } else if (isBoardFull()) {
            gameOver = true; // draw
            winner = null;
        } else {
            switchTurn();
        }
        return true;
    }

    private void switchTurn() {
        currentPlayer = currentPlayer.equals(player1) ? player2 : player1;
    }

    public char[][] getBoardCopy() {
        char[][] copy = new char[6][7];
        for (int i = 0; i < 6; i++) System.arraycopy(board[i], 0, copy[i], 0, 7);
        return copy;
    }

    public String getCurrentPlayer() {
        return currentPlayer;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public String getWinner() {
        return winner; // returns null if draw or game not finished
    }

    private boolean isBoardFull() {
        for (int i = 0; i < 6; i++)
            for (int j = 0; j < 7; j++)
                if (board[i][j] == ' ')
                    return false;
        return true;
    }

    private boolean checkWin(int row, int col) {
        char symbol = board[row][col];
        return checkDirection(row, col, symbol, 1, 0)  // vertical
                || checkDirection(row, col, symbol, 0, 1)  // horizontal
                || checkDirection(row, col, symbol, 1, 1)  // diagonal /
                || checkDirection(row, col, symbol, 1, -1); // diagonal \
    }

    private boolean checkDirection(int row, int col, char symbol, int dRow, int dCol) {
        int count = 1;
        count += countDirection(row, col, symbol, dRow, dCol);
        count += countDirection(row, col, symbol, -dRow, -dCol);
        return count >= 4;
    }

    private int countDirection(int row, int col, char symbol, int dRow, int dCol) {
        int r = row + dRow, c = col + dCol, count = 0;
        while (r >= 0 && r < 6 && c >= 0 && c < 7 && board[r][c] == symbol) {
            count++;
            r += dRow;
            c += dCol;
        }
        return count;
    }


}
