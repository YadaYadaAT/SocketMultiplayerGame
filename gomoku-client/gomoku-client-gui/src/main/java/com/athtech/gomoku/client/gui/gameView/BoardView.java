package com.athtech.gomoku.client.gui.gameView;

import javafx.scene.layout.GridPane;

import java.util.function.BiConsumer;

// This is the game board. Extends a JavaFX element
public class BoardView extends GridPane {
    private final int rows;
    private final int cols;
    private final CellView[][] cells;

    public BoardView(int rows, int cols, double cellSize) {
        this.rows = rows;
        this.cols = cols;
        this.cells = new CellView[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                CellView cell = new CellView(r, c, cellSize);
                add(cell, c, r);
                cells[r][c] = cell;
            }
        }
    }

    public void setCellClickListener(BiConsumer<Integer, Integer> listener) {
        for (CellView[] row : cells) {
            for (CellView cell : row) {
                cell.setClickListener(listener);
            }
        }
    }

    public void updateBoard(String[][] board, char mySymbol) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                String val = board[r][c];
                cells[r][c].setStone(val, mySymbol);
            }
        }
    }
}
