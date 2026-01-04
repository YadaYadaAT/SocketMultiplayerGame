package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record PlayerReconnectedNotificationResponse(String message) implements Serializable {
}
