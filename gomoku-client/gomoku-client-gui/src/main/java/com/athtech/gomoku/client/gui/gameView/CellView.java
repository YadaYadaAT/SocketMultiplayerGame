package com.athtech.gomoku.client.gui.gameView;

import javafx.geometry.Pos;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.function.BiConsumer;

public class CellView extends StackPane {
    private final int row;
    private final int col;
    private Circle stone;
    private BiConsumer<Integer, Integer> clickListener;

    public CellView(int row, int col, double size) {
        this.row = row;
        this.col = col;

        Rectangle bg = new Rectangle(size, size);
        bg.getStyleClass().add("board-cell");
        getChildren().add(bg);
        setAlignment(Pos.CENTER);

        setOnMouseClicked(this::handleClick);
    }

    private void handleClick(MouseEvent event) {
        if (clickListener != null) {
            clickListener.accept(row, col);
        }
    }

    public void setClickListener(BiConsumer<Integer, Integer> listener) {
        this.clickListener = listener;
    }

    public void setStone(String symbol, char mySymbol) {
        getChildren().remove(stone);
        stone = null;

        if (symbol == null || symbol.isBlank()) return;

        Color color;
        char c = symbol.charAt(0);
        if (c == mySymbol) {
            color = Color.BLUE;   // your symbol
        } else {
            color = Color.RED;    // opponent
        }

        stone = new Circle(20, color);
        getChildren().add(stone);
    }

    public void clear() {
        getChildren().remove(stone);
        stone = null;
    }
}
