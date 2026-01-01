package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record PlayerDisconnectedNotificationResponse(String message) implements Serializable {
}
