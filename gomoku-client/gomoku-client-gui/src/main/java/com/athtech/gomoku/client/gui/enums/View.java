package com.athtech.gomoku.client.gui.enums;

public enum View {
    INTRO("/com/athtech/gomoku/client/gui/fxml/Intro.fxml"),
    LOGIN("/com/athtech/gomoku/client/gui/fxml/Login.fxml"),
    SIGNUP("/com/athtech/gomoku/client/gui/fxml/Signup.fxml"),
    LOBBY("/com/athtech/gomoku/client/gui/fxml/Lobby.fxml"),
    GAME("/com/athtech/gomoku/client/gui/fxml/Game.fxml"),
    SCENEWRAPPER("/com/athtech/gomoku/client/gui/fxml/SceneWrapper.fxml");

    private final String fxmlPath;

    View(String fxmlPath) {
        this.fxmlPath = fxmlPath;
    }

    public String fxmlPath() {
        return fxmlPath;
    }
}