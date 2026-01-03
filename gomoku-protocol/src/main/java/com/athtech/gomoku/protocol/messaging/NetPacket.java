package com.athtech.gomoku.protocol.messaging;

import java.io.Serializable;

public record NetPacket(PacketType type, String sender, Serializable payload) implements Serializable {
    private static final long serialVersionUID = 1L;
}
