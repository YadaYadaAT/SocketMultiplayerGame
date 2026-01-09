package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

// boolean isGameQuitReqAccepted is used to inform the client whether the action of quitting the game has gone through
// boolean wasItUnstuckProcess is used to inform the client whether the user quitting the game is caused by a blocked process or manually exiting. Used to present the user with the appropriate message
public record GameQuitResponse(boolean isGameQuitReqAccepted , boolean wasItUnstuckProcess , String msg) implements Serializable {
}
