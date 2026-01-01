package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record PlayerReconnectedNotificationResponse(String message) implements Serializable {
}
