package com.athtech.gomoku.client.gui.controllers;
//STUDENTS-CODE-NUMBER : CSY-22117
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
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

// Endpoint for Game Scene
// All FXML components are annotated with @FXML
// Handle all game processes, including:
// 1. Entering & Leaving
// 2. Start & End
// 3. Moves
// 4. Rematches
// 5. Music
// 6. Reconnection
public class GameController extends BaseController {

    /* ---------------- UI ---------------- */
    @FXML private Label lblGameStatus;
    @FXML private Label lblRematchStatus;

    @FXML private Label lblTurnInfo;
    @FXML private ImageView waitForYourTurn;
    @FXML private ImageView playYourTurn;

    @FXML private VBox boardContainer;

    @FXML private ToggleButton btnMidgameRematch; //in action -> onMidGameRematchToggle
    @FXML private Button btnQuitGame; //in action -> quitGame
    @FXML private VBox endgameBox;   // holds endgame rematch buttons
    @FXML private Button btnBackToLobby; // -> in action backToLobby   fail-safe button

    /* ---------------- State ---------------- */
    private static final int CELL_SIZE = 48;

    private volatile long  gameVersion = 0;

    private volatile boolean inGame = false;
    private volatile boolean rematchPhase = false;
    private BoardView boardView;

    private volatile boolean reconnecting = false;

    // ---------------- Music ----------------
    private MediaPlayer backgroundMusic;
    @FXML private Button muteBtn;

    @Override
    public void onEnter() {
        Platform.runLater(() -> setupMusic());
    }

    @Override
    public void onLeave() {
         Platform.runLater( () -> {
             if (backgroundMusic != null) backgroundMusic.setMute(true);
            if (reconnecting){
                return;
            }
            gameVersion =0;
            inGame = false;
            rematchPhase = false;
            lblGameStatus.setText("");
             setRematchStatus("");
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

             playYourTurn.setVisible(false);
             waitForYourTurn.setVisible(false);
         });

    }



    /* ---------------- Actions ---------------- */

    // Send move request packet
    private void sendMove(int row, int col) {

        if ( !inGame){
            return;
        }
        Platform.runLater(() -> {    lblRematchStatus.setText("");});

        clientNetwork.sendPacket(new NetPacket(
                PacketType.MOVE_REQUEST,
                data.getUsername(),
                new MoveRequest(row, col)
        ));
    }

    // Send rematch request packet
    @FXML
    private void onMidgameRematchToggle() {
        if (!inGame) return;
        boolean selected = btnMidgameRematch.isSelected();

        // Update text
        btnMidgameRematch.setText(selected ? "Rematch On" : "Rematch Off");

        // Change background color based on selection
        if (selected) {
            btnMidgameRematch.setStyle("-fx-background-color: green; -fx-text-fill: white;");
        } else {
            btnMidgameRematch.setStyle("-fx-background-color: #4b6cb7; -fx-text-fill: white;");
        }
        clientNetwork.sendPacket(new NetPacket(
                PacketType.REMATCH_REQUEST,
                data.getUsername(),
                new RematchRequest(selected)
        ));
        lblGameStatus.setText(selected ? "Rematch requested" : "Rematch request cancelled");
    }

    // Send quit request packet
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
        gameVersion = 0;
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
            if (btnMidgameRematch.isSelected()) {
                btnMidgameRematch.setStyle("-fx-background-color: green; -fx-text-fill: white;");
            } else {
                btnMidgameRematch.setStyle("-fx-background-color: #4b6cb7; -fx-text-fill: white;");
            }

            //Quit btn
            btnQuitGame.setVisible(true);
            btnQuitGame.setDisable(false);

            //End game rematch states
            endgameBox.setVisible(false);
            endgameBox.setDisable(true);

            //back to lobbySafety
            btnBackToLobby.setVisible(false);
            btnBackToLobby.setDisable(true);

            playYourTurn.setVisible(true);
            waitForYourTurn.setVisible(true);


//----------------------------------------------------------

            int rows = gs.board().cells().length;
            int cols = gs.board().cells()[0].length;

            boardView = new BoardView(rows, cols, CELL_SIZE);
            boardView.setCellClickListener(this::sendMove);
            boardContainer.getChildren().setAll(boardView);

            // Populate board
            char[][] charBoard = gs.board().cells();
            String[][] strBoard = new String[rows][cols];
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    strBoard[r][c] = (charBoard[r][c] == '\0' || charBoard[r][c] == ' ') ? null : String.valueOf(charBoard[r][c]);
                }
            }

            char mySymbol = data.getUsername().equals(gs.player1()) ? 'X' : 'O';
            boardView.updateBoard(strBoard, mySymbol);

            boolean myTurn = gs.currentPlayer().equals(data.getUsername()); // Turn tracking
            lblTurnInfo.setText(myTurn ? "Its Your turn" : "Opponent's turn");
            updateTurnImages(myTurn);
            lblGameStatus.setText("Connect " + gs.winCount() + " to win the game");
        });
    }

    // Accept Game State Payload
    public void onGameStateResponse(NetPacket packet) {
        GameStateResponse gs = (GameStateResponse) packet.payload();
        onGameStateResponseFromPayload(gs);
    }

    // Helper method to accept game state payload
    public void onGameStateResponseFromPayload(GameStateResponse gs) {
        if (gs.version() < gameVersion) {
            // old packet, ignore
            return;
        }

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
            updateTurnImages(myTurn);
        });
    }

    // Accept game end response
    public void onGameEndResponse(NetPacket packet) {
        GameEndResponse end = (GameEndResponse) packet.payload();

        Platform.runLater(() -> {
            inGame = false;
            rematchPhase = true;

            //Ui states
            playYourTurn.setVisible(false);
            waitForYourTurn.setVisible(false);
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

            char[][] charBoard = end.finalBoard().cells();
            String[][] strBoard = new String[charBoard.length][charBoard[0].length];
            for (int r = 0; r < charBoard.length; r++)
                for (int c = 0; c < charBoard[0].length; c++)
                    strBoard[r][c] = (charBoard[r][c] == '\0' || charBoard[r][c] == ' ') ? null : String.valueOf(charBoard[r][c]);

            char mySymbol = data.getUsername().equals(end.player1()) ? 'X' : 'O';
            if (end.finalBoard() != null) boardView.updateBoard(strBoard, mySymbol);

            lblTurnInfo.setText(getEndMessage(end.reason()));
        });
    }

//      ---- REMATCH ----       //

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

//      ----        ----        //

    // Accept Server Response on Match End
    public void onMatchSessionEndedResponse(NetPacket packet) {
        MatchSessionEndedResponse match = (MatchSessionEndedResponse) packet.payload();
        Platform.runLater(() -> {
            rematchPhase = false;
            gameVersion =0;
            if(!match.isRematchOn()) {

//                navigator.goTo(View.LOBBY);
                endgameBox.setVisible(false);
                endgameBox.setDisable(true);
                lblGameStatus.setText("Game is Over");
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


    /* ---------------- Music & Controls ---------------- */
    private void setupMusic() {
        try {
            if (backgroundMusic != null) return; // already setup

            Media music1 = new Media(getClass().getResource("/com/athtech/gomoku/client/gui/music/here.mp3").toExternalForm());
            backgroundMusic = new MediaPlayer(music1);
            backgroundMusic.setCycleCount(MediaPlayer.INDEFINITE);
            backgroundMusic.setVolume(0.5);
            backgroundMusic.play();

            // Set initial button text based on mute
            updateMuteButtonText();

        } catch (Exception e) {
            System.err.println("Failed to load background music: " + e.getMessage());
        }
    }

    @FXML
    private void handleMute() {
        if (backgroundMusic == null) return;
        boolean muted = backgroundMusic.isMute();
        backgroundMusic.setMute(!muted);
        updateMuteButtonText();
    }

    private void updateMuteButtonText() {
        if (backgroundMusic == null) return;
        boolean muted = backgroundMusic.isMute();
        // Shows the action that will happen when pressed
        muteBtn.setText(muted ? "Unmute 🔇" : "Mute 🔊");
    }

/*                      */

    public void onResyncResponse(NetPacket packet){
        ResyncResponse resp =(ResyncResponse) packet.payload();
        onLoginResponseFromExtractedGamestate( resp.currentGameState());
    }

    public void onLoginResponse(NetPacket packet){
        LoginResponse loginR = (LoginResponse) packet.payload();
        GameStateResponse gs = loginR.currentGameState();
        onLoginResponseFromExtractedGamestate(gs);
    }

    // Used to populate Game State after Login or after Resync
    // Must be synchronized to guarantee that only one login/resync happens at a time
    // --> This method is called by the network listener thread (via network handler)
    // --> but UI updates happen later on the JavaFX Application Thread (via Platform.runLater())
    // synchronization protects shared controller fields from being mutated by the different threads
    public synchronized void onLoginResponseFromExtractedGamestate(GameStateResponse gs){

        if(gs != null){
            reconnecting = true;

            // ----------------------
            // Board
            // ----------------------
            int rows = gs.board().cells().length;
            int cols = gs.board().cells()[0].length;

            if(boardView == null){
                boardView = new BoardView(rows, cols, CELL_SIZE);
                boardView.setCellClickListener(this::sendMove);
            }

            Platform.runLater(() -> {
                boardContainer.getChildren().setAll(boardView);

                // ----------------------
                // Flags
                // ----------------------
                inGame = true;
                rematchPhase = false;

                // ----------------------
                // UI states
                // ----------------------
                boolean myTurn = gs.currentPlayer().equals(data.getUsername());

                lblTurnInfo.setText(myTurn ? "Your turn" : "Opponent's turn");
                lblGameStatus.setText("Connect " + gs.winCount() + " to win the game");

                // Midgame rematch
                btnMidgameRematch.setVisible(true);
                btnMidgameRematch.setDisable(false);
                btnMidgameRematch.setSelected(false);

                // Quit button
                btnQuitGame.setVisible(true);
                btnQuitGame.setDisable(false);

                // Endgame box
                endgameBox.setVisible(false);
                endgameBox.setDisable(true);

                // Back to lobby
                btnBackToLobby.setVisible(false);
                btnBackToLobby.setDisable(true);

                // ----------------------
                // Populate board
                // ----------------------
                char[][] charBoard = gs.board().cells();
                String[][] strBoard = new String[rows][cols];
                for (int r = 0; r < rows; r++)
                    for (int c = 0; c < cols; c++)
                        strBoard[r][c] = (charBoard[r][c] == '\0' || charBoard[r][c] == ' ') ? null : String.valueOf(charBoard[r][c]);

                char mySymbol = data.getUsername().equals(gs.player1()) ? 'X' : 'O';
                boardView.updateBoard(strBoard, mySymbol);
            });

            // ----------------------
            // Done reconnecting
            // ----------------------
            reconnecting = false;
        }
    }

    // Accept Game Quit Response
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

    // Accept Move Rejected Response
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

    // Accept Rematch Response
    public void onRematchResponse(NetPacket packet) {
        RematchResponse resp = (RematchResponse) packet.payload();
        Platform.runLater(() -> setRematchStatus(resp.message()));
    }

    // Accept Player Inactivity Warning Response
    public void onPlayerInactivityWarningResponse(NetPacket packet) {
        PlayerInactivityWarningResponse pck = (PlayerInactivityWarningResponse) packet.payload();
        Platform.runLater(() -> lblGameStatus.setText(pck.message()));
    }

    // Accept Player Disconnected Notification Response
    public void onPlayerDisconnectedNotificationResponse(NetPacket packet) {
        PlayerDisconnectedNotificationResponse msg =
                (PlayerDisconnectedNotificationResponse) packet.payload();

        Platform.runLater(() -> {
            lblGameStatus.setText(msg.message());
        });
    }

    // Accept Player Reconnected Notification Response
    public void onPlayerReconnectedNotificationResponse(NetPacket packet) {
        PlayerReconnectedNotificationResponse msg =
                (PlayerReconnectedNotificationResponse) packet.payload();
        Platform.runLater(() -> lblGameStatus.setText(msg.message()));
    }

    // Accept Player Reconnected Response
    public void onPlayerReconnectedResponse(NetPacket packet) {
        PlayerReconnectedResponse res = (PlayerReconnectedResponse) packet.payload();
        Platform.runLater(() -> lblGameStatus.setText(res.msg()));
    }

    // Accept Game Quit Notification Response
    public void onGameQuitNotification(NetPacket packet) {
        GameQuitNotificationResponse resp = (GameQuitNotificationResponse) packet.payload();
        Platform.runLater(() -> {
            lblGameStatus.setText("Opponent " + resp.quitter() + " quit the game.");
            inGame = false;
            rematchPhase = false;
        });
    }

    // Handle displayed messages at game end
    private String getEndMessage(MatchEndReason reason) {
        return switch (reason) {
            case MID_GAME_REMATCH -> "Good luck at your rematch!";
            case WIN_NORMAL -> "You won! 🎉";
            case WIN_QUIT -> "Opponent quit. You win!";
            case WIN_TIMEOUT -> "Opponent AFK. You win!";
            case WIN_DISCONNECT -> "Opponent disconnected. You win!";
            case LOSS_NORMAL -> "You lost ";
            case LOSS_QUIT -> "You quit ";
            case LOSS_TIMEOUT -> "You were AFK ";
            case LOSS_DISCONNECT -> "You disconnected ";
            case DRAW -> "It's a draw.";
            default -> "Game ended.";
        };
    }

    // Handle rematch status
    private void setRematchStatus(String text){
        Platform.runLater(() -> {
            boolean hasText = text != null && !text.isBlank();
            lblRematchStatus.setVisible(hasText);
            lblRematchStatus.setText(text);
        });
    }

    @FXML
    private void backToLobby() {
        navigator.goTo(View.LOBBY);
    }

    @Override
    public void showInfo(InfoResponse infoResponse) {
        Platform.runLater(() -> lblGameStatus.setText(infoResponse.msg()));
    }

    private void updateTurnImages(boolean myTurn) {
        playYourTurn.setVisible(myTurn);
        waitForYourTurn.setVisible(!myTurn);
    }
}
