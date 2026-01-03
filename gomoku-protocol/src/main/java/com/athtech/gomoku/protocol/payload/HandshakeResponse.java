package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record HandshakeResponse(String msg) implements Serializable {
}
