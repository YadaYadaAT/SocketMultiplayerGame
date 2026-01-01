package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record PlayerInactivityWarningResponse(String message) implements Serializable {
}
