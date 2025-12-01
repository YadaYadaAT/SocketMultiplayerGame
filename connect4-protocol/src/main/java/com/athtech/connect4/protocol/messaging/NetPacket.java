package com.athtech.connect4.protocol.messaging;

public record NetPacket(PacketType type, String sender, String data) {
}
