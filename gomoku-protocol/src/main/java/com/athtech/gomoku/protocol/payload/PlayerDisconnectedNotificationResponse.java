package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record PlayerDisconnectedNotificationResponse(String message) implements Serializable {
}
