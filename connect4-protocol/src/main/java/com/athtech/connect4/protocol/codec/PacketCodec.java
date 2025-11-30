package com.athtech.connect4.protocol.codec;

import com.athtech.connect4.protocol.messaging.NetPacket;

public interface PacketCodec {
    byte[] encode(NetPacket packet);
    NetPacket decode(byte[] data);
}
