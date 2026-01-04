package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record GameQuitResponse(boolean isGameQuitReqAccepted , boolean wasItUnstuckProcess , String msg) implements Serializable {
}
