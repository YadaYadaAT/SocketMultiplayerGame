package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record PlayerInactivityWarningResponse(String message) implements Serializable {
}
