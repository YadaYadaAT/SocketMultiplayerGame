package com.athtech.connect4.protocol.messaging;

public interface NetPacket {
    PacketType getType();
    String getSender();
}
