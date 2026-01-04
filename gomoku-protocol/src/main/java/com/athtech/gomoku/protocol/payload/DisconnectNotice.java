package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record DisconnectNotice(String username, String reason) implements Serializable {}
