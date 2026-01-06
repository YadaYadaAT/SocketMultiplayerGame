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

    @FXML private VBox boardContainer;

    @FXML private ToggleButton btnMidgameRematch; //in action -> onMidGameRematchToggle
    @FXML private Button btnQuitGame; //in action -> quitGame
    @FXML private VBox endgameBox;   // holds endgame rematch buttons
    @FXML private Button btnBackToLobby; // -> in action backToLobby   fail-safe button

    /* ---------------- State ---------------- */
    private static final int CELL_SIZE = 48;

    private boolean inGame = false;
    private boolean rematchPhase = false;
    private BoardView boardView;


    @Override
    public void onLeave() {
        inGame = false;
        rematchPhase = false;
        Platform.runLater( () -> {
            lblGameStatus.setText("");
            lblTurnInfo.setText("");
            boardContainer.getChildren().clear();

            //MID REMATCH states
            btnMidgameRematch.setSelected(false);
            btnMidgameRematch.setVisible(true);
            btnMidgameRematch.setDisable(false);

            //Quit btn
            btnQuitGame.setVisible(true);
            btnQuitGame.setDisable(false);

            //End game rematch states
            endgameBox.setVisible(false);
            endgameBox.setDisable(true);

            //back to lobbySafety
            btnBackToLobby.setVisible(false);
            btnBackToLobby.setDisable(true);

        });

    }



    /* ---------------- Actions ---------------- */
    private void sendMove(int row, int col) {

        if ( !inGame){
            return;
        }

        clientNetwork.sendPacket(new NetPacket(
                PacketType.MOVE_REQUEST,
                data.getUsername(),
                new MoveRequest(row, col)
        ));
    }

    @FXML
    private void onMidgameRematchToggle() {
        if (!inGame) return;
        btnMidgameRematch.isSelected();
        clientNetwork.sendPacket(new NetPacket(
                PacketType.REMATCH_REQUEST,
                data.getUsername(),
                new RematchRequest(btnMidgameRematch.isSelected())
        ));
        lblGameStatus.setText(btnMidgameRematch.isSelected() ? "Rematch requested" : "Rematch request cancelled");
    }

    @FXML
    private void quitGame() {
        if (inGame) {
            clientNetwork.sendPacket(new NetPacket(
                    PacketType.GAME_QUIT_REQUEST,
                    data.getUsername(),
                    new GameQuitRequest(false)
            ));
        }
    }





    /* ---------------- Server → UI ---------------- */

    public void onGameStartResponse(NetPacket packet) {
        GameStateResponse gs = (GameStateResponse) packet.payload();

        Platform.runLater(() -> {
            inGame = true;
            rematchPhase = false;

    //----------------------------------------------------------
            //Ui states

            //MID REMATCH states
            btnMidgameRematch.setSelected(false);
            btnMidgameRematch.setVisible(true);
            btnMidgameRematch.setDisable(false);

            //Quit btn
            btnQuitGame.setVisible(true);
            btnQuitGame.setDisable(false);

            //End game rematch states
            endgameBox.setVisible(false);
            endgameBox.setDisable(true);

            //back to lobbySafety
            btnBackToLobby.setVisible(false);
            btnBackToLobby.setDisable(true);

//----------------------------------------------------------

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

            boolean myTurn = gs.currentPlayer().equals(data.getUsername());
            lblTurnInfo.setText(myTurn ? "Its Your turn" : "Opponent's turn");
            lblGameStatus.setText("Connect " + gs.winCount() + " to win the game");
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

            boolean myTurn = gs.currentPlayer().equals(data.getUsername());
            lblTurnInfo.setText(myTurn ? "Your turn" : "Opponent's turn");
        });
    }

    public void onGameEndResponse(NetPacket packet) {
        GameEndResponse end = (GameEndResponse) packet.payload();

        Platform.runLater(() -> {
            inGame = false;
            rematchPhase = true;

            //Ui states

                //MID REMATCH states
                btnMidgameRematch.setSelected(false);
                btnMidgameRematch.setVisible(false);
                btnMidgameRematch.setDisable(true);

                //Quit btn
                btnQuitGame.setVisible(false);
                btnQuitGame.setDisable(true);

                //End game rematch states
                endgameBox.setVisible(true);
                endgameBox.setDisable(false);

//                //back to lobbySafety
//                btnBackToLobby.setVisible(false);
//                btnBackToLobby.setDisable(true);



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
        clientNetwork.sendPacket(new NetPacket(
                PacketType.REMATCH_REQUEST,
                data.getUsername(),
                new RematchRequest(want)
        ));
        lblGameStatus.setText("Rematch decision sent...");
    }

    public void onMatchSessionEndedResponse(NetPacket packet) {
        MatchSessionEndedResponse match = (MatchSessionEndedResponse) packet.payload();
        Platform.runLater(() -> {
            rematchPhase = false;
            if(!match.isRematchOn()) {

                navigator.goTo(View.LOBBY);
                //Ui states

//                //MID REMATCH states
//                btnMidgameRematch.setSelected(false);
//                btnMidgameRematch.setVisible(false);
//                btnMidgameRematch.setDisable(true);
//
//                //Quit btn
//                btnQuitGame.setVisible(false);
//                btnQuitGame.setDisable(true);
//
//                //End game rematch states
//                endgameBox.setVisible(false);
//                endgameBox.setDisable(true);
//
                //back to lobbySafety
                btnBackToLobby.setVisible(true);
                btnBackToLobby.setDisable(false);


            }else{// rematch on

                //MID REMATCH states
                btnMidgameRematch.setSelected(false);
                btnMidgameRematch.setVisible(true);
                btnMidgameRematch.setDisable(false);

                //Quit btn
                btnQuitGame.setVisible(true);
                btnQuitGame.setDisable(false);

                //End game rematch states
                endgameBox.setVisible(false);
                endgameBox.setDisable(true);


            }

        });
    }


    public void onGameQuitResponse(NetPacket packet) {
        GameQuitResponse gameQuitResponse = (GameQuitResponse) packet.payload();
        if (gameQuitResponse.wasItUnstuckProcess()) return;
        Platform.runLater(() -> {
            inGame = false;
            rematchPhase = false;
            //check if reset
            navigator.goTo(View.LOBBY);

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


    public void onRematchResponse(NetPacket packet) {
        RematchResponse resp = (RematchResponse) packet.payload();
        Platform.runLater(() -> lblGameStatus.setText(resp.message()));
    }


    public void onPlayerInactivityWarningResponse(NetPacket packet) {
        PlayerInactivityWarningResponse pck = (PlayerInactivityWarningResponse) packet.payload();
        Platform.runLater(() -> lblGameStatus.setText(pck.message()));
    }

    public void onPlayerDisconnectedNotificationResponse(NetPacket packet) {
        PlayerDisconnectedNotificationResponse msg =
                (PlayerDisconnectedNotificationResponse) packet.payload();

        Platform.runLater(() -> {
            lblGameStatus.setText(msg.message());
        });
    }

    public void onPlayerReconnectedNotificationResponse(NetPacket packet) {
        PlayerReconnectedNotificationResponse msg =
                (PlayerReconnectedNotificationResponse) packet.payload();
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





    private String getEndMessage(MatchEndReason reason) {
        return switch (reason) {
            case MID_GAME_REMATCH -> "Good luck at your rematch!";
            case WIN_NORMAL -> "You won! 🎉";
            case WIN_QUIT -> "Opponent quit. You win!";
            case WIN_TIMEOUT -> "Opponent AFK. You win!";
            case WIN_DISCONNECT -> "Opponent disconnected. You win!";
            case LOSS_NORMAL -> "You lost 😢";
            case LOSS_QUIT -> "You quit 😢";
            case LOSS_TIMEOUT -> "You were AFK 😢";
            case LOSS_DISCONNECT -> "You disconnected 😢";
            case DRAW -> "It's a draw.";
            default -> "Game ended.";
        };
    }

    @FXML
    private void backToLobby() {
        navigator.goTo(View.LOBBY);
    }

    @Override
    public void showInfo(InfoResponse infoResponse) {
        Platform.runLater(() -> lblGameStatus.setText(infoResponse.msg()));
    }
}
