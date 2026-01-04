package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record GameQuitResponse(boolean isGameQuitReqAccepted) implements Serializable {
}
