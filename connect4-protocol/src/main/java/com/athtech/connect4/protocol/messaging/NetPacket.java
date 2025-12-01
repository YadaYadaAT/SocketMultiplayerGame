package com.athtech.connect4.protocol.messaging;

public class NetPacket {
    private final PacketType type;
    private final String sender;
    private final String data;

    public NetPacket(PacketType type, String sender, String data) {
        this.type = type;
        this.sender = sender;
        this.data = data;
    }

    public PacketType getType() { return type; }
    public String getSender() { return sender; }
    public String getData() { return data; }
}
