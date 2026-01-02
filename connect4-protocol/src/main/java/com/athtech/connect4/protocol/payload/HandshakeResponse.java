package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record HandshakeResponse(String msg) implements Serializable {
}
