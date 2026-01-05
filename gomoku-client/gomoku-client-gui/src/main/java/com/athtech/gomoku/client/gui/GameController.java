package com.athtech.gomoku.client.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import com.athtech.gomoku.client.net.ClientNetworkAdapter;
import com.athtech.gomoku.protocol.messaging.NetPacket;
import com.athtech.gomoku.protocol.messaging.PacketType;
import com.athtech.gomoku.protocol.payload.BoardState;
import com.athtech.gomoku.protocol.payload.GameQuitRequest;
import com.athtech.gomoku.protocol.payload.GameStateResponse;
import com.athtech.gomoku.protocol.payload.MoveRequest;

import java.util.function.Consumer;

public class GameController {

    @FXML private TextArea gameBoardArea;
    @FXML private TextArea chatArea;
    @FXML private TextField moveInput;
    @FXML private Button btnSendMove;
    @FXML private Button btnQuit;

    private Stage stage;
    private ClientNetworkAdapter network;
    private Runnable onExitGame;
    private Consumer<NetPacket> packetHandler;

    public void init(Stage stage, GomokuFXApp app, Runnable onExitGame) {
        this.stage = stage;
        this.network = app.getNetwork();
        this.onExitGame = onExitGame;

        packetHandler = this::handleServerPacket;
        app.registerPacketHandler(packetHandler);
    }

    @FXML
    private void sendMove() {
        String input = moveInput.getText().trim();
        if (input.equalsIgnoreCase("q")) {
            network.sendPacket(new NetPacket(PacketType.GAME_QUIT_REQUEST, "", new GameQuitRequest(false)));
        } else {
            String[] parts = input.split(",");
            if (parts.length == 2) {
                try {
                    int row = Integer.parseInt(parts[0].trim()) - 1;
                    int col = Integer.parseInt(parts[1].trim()) - 1;
                    network.sendPacket(new NetPacket(PacketType.MOVE_REQUEST, "", new MoveRequest(row, col)));
                } catch (NumberFormatException e) {
                    appendChat("Invalid move numbers.");
                }
            } else appendChat("Invalid move format.");
        }
        moveInput.clear();
    }

    @FXML
    private void quitGame() {
        onExitGame.run();
    }

    private void handleServerPacket(NetPacket packet) {
        if (packet.type() == PacketType.GAME_STATE_RESPONSE) {
            GameStateResponse gs = (GameStateResponse) packet.payload();
            Platform.runLater(() -> updateBoard(gs.board()));
        }
    }

    private void updateBoard(BoardState board) {
        StringBuilder sb = new StringBuilder();
        for (char[] row : board.cells()) {
            for (char cell : row) sb.append(cell == '\0' ? "." : cell).append(" ");
            sb.append("\n");
        }
        gameBoardArea.setText(sb.toString());
    }

    private void appendChat(String msg) {
        chatArea.appendText(msg + "\n");
    }

    public void cleanup(GomokuFXApp app) {
        if (packetHandler != null) {
            app.unregisterPacketHandler(packetHandler);
            packetHandler = null;
        }
    }
}
