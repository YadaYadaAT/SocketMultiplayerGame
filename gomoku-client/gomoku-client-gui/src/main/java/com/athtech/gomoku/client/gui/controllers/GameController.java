package com.athtech.gomoku.client.gui.controllers;

import com.athtech.gomoku.client.gui.enums.View;
import com.athtech.gomoku.client.gui.gameView.BoardView;
import com.athtech.gomoku.protocol.messaging.MatchEndReason;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

public class GameController extends BaseController {

    /* ---------------- UI ---------------- */
    @FXML private Label lblGameStatus;
    @FXML private Label lblTurnInfo;

    @FXML private ToggleButton btnMidgameRematch;
    @FXML private Button btnQuitGame;

    @FXML private VBox boardContainer;
    @FXML private VBox midgameBox;   // holds midgame buttons
    @FXML private VBox endgameBox;   // holds endgame rematch buttons

    /* ---------------- State ---------------- */
    private static final int CELL_SIZE = 48;

    private boolean myTurn = false;
    private boolean inGame = false;
    private boolean rematchPhase = false;
    private boolean midgameRematchRequested = false;

    private BoardView boardView;

    /* ---------------- Init ---------------- */
    @FXML
    private void initialize() {
        // start hidden
        if (endgameBox != null) {
            endgameBox.setVisible(false);
            endgameBox.setManaged(false);
        }
    }

    /* ---------------- Actions ---------------- */
    private void sendMove(int row, int col) {
        if (!myTurn || !inGame) return;
        myTurn = false;
        clientNetwork.sendPacket(new NetPacket(
                PacketType.MOVE_REQUEST,
                data.getUsername(),
                new MoveRequest(row, col)
        ));
    }

    @FXML
    private void onMidgameRematchToggle() {
        if (!inGame) return;

        midgameRematchRequested = btnMidgameRematch.isSelected();
        clientNetwork.sendPacket(new NetPacket(
                PacketType.REMATCH_REQUEST,
                data.getUsername(),
                new RematchRequest(midgameRematchRequested)
        ));
        lblGameStatus.setText(midgameRematchRequested ? "Rematch requested" : "Rematch request cancelled");
    }

    @FXML
    private void quitGame() {
        if (inGame) {
            clientNetwork.sendPacket(new NetPacket(
                    PacketType.GAME_QUIT_REQUEST,
                    data.getUsername(),
                    new GameQuitRequest(false)
            ));
        } else {
            navigator.goTo(View.LOBBY);
        }
    }

    @FXML
    private void onEndGameRematchYes() {
        sendEndGameRematch(true);
    }

    @FXML
    private void onEndGameRematchNo() {
        sendEndGameRematch(false);
        navigator.goTo(View.LOBBY);
    }

    private void sendEndGameRematch(boolean want) {
        if (!rematchPhase) return;

        endgameBox.setDisable(true);
        lblGameStatus.setText("Rematch decision sent...");
        clientNetwork.sendPacket(new NetPacket(
                PacketType.REMATCH_REQUEST,
                data.getUsername(),
                new RematchRequest(want)
        ));
    }

    /* ---------------- Server → UI ---------------- */
    public void onGameStartResponse(NetPacket packet) {
        GameStateResponse gs = (GameStateResponse) packet.payload();
        Platform.runLater(() -> {
            inGame = true;
            rematchPhase = false;
            midgameRematchRequested = false;

            // show midgame buttons
            if (btnMidgameRematch != null) btnMidgameRematch.setDisable(false);
            if (btnQuitGame != null) btnQuitGame.setDisable(false);
            if (btnMidgameRematch != null) btnMidgameRematch.setVisible(true);
            if (btnQuitGame != null) btnQuitGame.setVisible(true);

            hideEndgameBox();

            // Board setup
            int rows = gs.board().cells().length;
            int cols = gs.board().cells()[0].length;
            boardView = new BoardView(rows, cols, CELL_SIZE);
            boardView.setCellClickListener(this::sendMove);
            boardContainer.getChildren().setAll(boardView);

            char[][] charBoard = gs.board().cells();
            String[][] strBoard = new String[rows][cols];
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    strBoard[r][c] = (charBoard[r][c] == '\0' || charBoard[r][c] == ' ') ? null : String.valueOf(charBoard[r][c]);
                }
            }

            char mySymbol = data.getUsername().equals(gs.player1()) ? 'X' : 'O';
            boardView.updateBoard(strBoard, mySymbol);

            myTurn = gs.currentPlayer().equals(data.getUsername());
            lblTurnInfo.setText(myTurn ? "Your turn" : "Opponent's turn");
            lblGameStatus.setText("Connect " + gs.winCount() + " to win");
        });
    }

    public void onGameStateResponse(NetPacket packet) {
        GameStateResponse gs = (GameStateResponse) packet.payload();
        Platform.runLater(() -> {
            char[][] charBoard = gs.board().cells();
            String[][] strBoard = new String[charBoard.length][charBoard[0].length];
            for (int r = 0; r < charBoard.length; r++)
                for (int c = 0; c < charBoard[0].length; c++)
                    strBoard[r][c] = (charBoard[r][c] == '\0' || charBoard[r][c] == ' ') ? null : String.valueOf(charBoard[r][c]);

            char mySymbol = data.getUsername().equals(gs.player1()) ? 'X' : 'O';
            boardView.updateBoard(strBoard, mySymbol);

            myTurn = gs.currentPlayer().equals(data.getUsername());
            lblTurnInfo.setText(myTurn ? "Your turn" : "Opponent's turn");
        });
    }

    public void onMoveRejectedResponse(NetPacket packet) {
        MoveRejectedResponse rej = (MoveRejectedResponse) packet.payload();
        Platform.runLater(() -> {
            lblGameStatus.setText("Move rejected: " + rej.reason());
            if (rej.currentPlayer() == null) {
                inGame = false;
                lblTurnInfo.setText("Game session lost");
            }
        });
    }

    public void onGameEndResponse(NetPacket packet) {
        GameEndResponse end = (GameEndResponse) packet.payload();
        Platform.runLater(() -> {
            inGame = false;
            rematchPhase = true;

            // hide midgame UI
            if (midgameBox != null) midgameBox.setVisible(false);
            if (midgameBox != null) midgameBox.setManaged(false);

            // show endgame rematch UI
            showEndgameBox();

            char[][] charBoard = end.finalBoard().cells();
            String[][] strBoard = new String[charBoard.length][charBoard[0].length];
            for (int r = 0; r < charBoard.length; r++)
                for (int c = 0; c < charBoard[0].length; c++)
                    strBoard[r][c] = (charBoard[r][c] == '\0' || charBoard[r][c] == ' ') ? null : String.valueOf(charBoard[r][c]);

            char mySymbol = data.getUsername().equals(end.player1()) ? 'X' : 'O';
            if (end.finalBoard() != null) boardView.updateBoard(strBoard, mySymbol);

            lblGameStatus.setText(getEndMessage(end.reason()));
        });
    }

    @FXML
    private void backToLobby() {
        navigator.goTo(View.LOBBY);
    }

    public void onMatchSessionEndedResponse(NetPacket packet) {
        MatchSessionEndedResponse resp = (MatchSessionEndedResponse) packet.payload();
        Platform.runLater(() -> {
            rematchPhase = false;
            hideEndgameBox();
            if (resp.isRematchOn()) lblGameStatus.setText("Rematch starting...");
        });
    }

    public void onRematchResponse(NetPacket packet) {
        RematchResponse resp = (RematchResponse) packet.payload();
        Platform.runLater(() -> lblGameStatus.setText(resp.message()));
    }

    public void onPlayerInactivityWarningResponse(NetPacket packet) {
        PlayerInactivityWarningResponse pck = (PlayerInactivityWarningResponse) packet.payload();
        Platform.runLater(() -> lblGameStatus.setText(pck.message()));
    }

    public void onPlayerDisconnectedNotificationResponse(NetPacket packet) {
        PlayerDisconnectedNotificationResponse msg = (PlayerDisconnectedNotificationResponse) packet.payload();
        Platform.runLater(() -> lblGameStatus.setText(msg.message()));
    }

    public void onPlayerReconnectedNotificationResponse(NetPacket packet) {
        PlayerReconnectedNotificationResponse msg = (PlayerReconnectedNotificationResponse) packet.payload();
        Platform.runLater(() -> lblGameStatus.setText(msg.message()));
    }

    public void onPlayerReconnectedResponse(NetPacket packet) {
        PlayerReconnectedResponse res = (PlayerReconnectedResponse) packet.payload();
        Platform.runLater(() -> lblGameStatus.setText(res.msg()));
    }

    public void onGameQuitNotification(NetPacket packet) {
        GameQuitNotificationResponse resp = (GameQuitNotificationResponse) packet.payload();
        Platform.runLater(() -> {
            lblGameStatus.setText("Opponent " + resp.quitter() + " quit the game.");
            inGame = false;
            rematchPhase = false;
        });
    }

    public void onGameQuitResponse(NetPacket packet) {
        GameQuitResponse gameQuitResponse = (GameQuitResponse) packet.payload();
        if (gameQuitResponse.wasItUnstuckProcess()) return;

        Platform.runLater(() -> {
            inGame = false;
            rematchPhase = false;
            navigator.goTo(View.LOBBY);
        });
    }

    /* ---------------- Helpers ---------------- */
    private void showEndgameBox() {
        if (endgameBox != null) {
            endgameBox.setVisible(true);
            endgameBox.setManaged(true);
            endgameBox.setDisable(false);
        }
    }

    private void hideEndgameBox() {
        if (endgameBox != null) {
            endgameBox.setVisible(false);
            endgameBox.setManaged(false);
        }
    }

    private String getEndMessage(MatchEndReason reason) {
        return switch (reason) {
            case MID_GAME_REMATCH ->  "Good luck at your rematch!";
            case WIN_NORMAL -> "You won! 🎉";
            case WIN_QUIT -> "Opponent quit the game. You win by default!";
            case WIN_TIMEOUT -> "Opponent was AFK. You win!";
            case WIN_DISCONNECT -> "Opponent disconnected. You win!";
            case LOSS_NORMAL -> "You lost. 😢";
            case LOSS_QUIT -> "You quit the game. 😢";
            case LOSS_TIMEOUT -> "You were AFK. You lost!";
            case LOSS_DISCONNECT -> "You disconnected. You lost!";
            case DRAW -> "It's a draw.";
            case UNKNOWN -> "Game ended unexpectedly.";
            default -> "Game ended";
        };
    }

    @Override
    public void showInfo(InfoResponse infoResponse) {
        Platform.runLater(() -> lblGameStatus.setText(infoResponse.msg()));
    }
}
