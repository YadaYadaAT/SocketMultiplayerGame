package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record GameQuitRequest(boolean isUnstuckProcess) implements Serializable {
}
