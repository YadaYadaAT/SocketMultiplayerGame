package com.athtech.gomoku.protocol.messaging;
//STUDENTS-CODE-NUMBER : CSY-22115
import java.io.Serializable;

// Server and client communicate only through NetPackets. Everything else is rejected.
// This class utilizes:
// Packet Type: lets the client or the server know what type of data to expect
// Sender: Client or Server
// Payload: The actual data that is sent between client-server. Matches the PacketType mentioned above. Needs to be serializable so that it can be transferrable through the network.
public record NetPacket(PacketType type, String sender, Serializable payload) implements Serializable {
    private static final long serialVersionUID = 1L;
}
