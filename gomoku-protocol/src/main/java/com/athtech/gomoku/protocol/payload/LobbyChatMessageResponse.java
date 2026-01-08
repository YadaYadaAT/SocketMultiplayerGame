package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record LobbyChatMessageResponse(long timestamp, String username, String message) implements Serializable {}