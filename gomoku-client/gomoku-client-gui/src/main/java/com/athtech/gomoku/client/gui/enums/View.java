package com.athtech.gomoku.client.gui.enums;

public enum View {
    LOGIN("/fxml/Login.fxml"),
    SIGNUP("/fxml/SignUp.fxml"),
    LOBBY("/fxml/Lobby.fxml"),
    GAME("/fxml/Game.fxml"),
    SCENEWRAPPER("/fxml/SceneWrapper.fxml");

    private final String fxmlPath;

    View(String fxmlPath) {
        this.fxmlPath = fxmlPath;
    }

    public String fxmlPath() {
        return fxmlPath;
    }
}