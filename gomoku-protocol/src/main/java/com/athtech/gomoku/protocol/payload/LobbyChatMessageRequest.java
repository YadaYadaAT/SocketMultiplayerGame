package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record LobbyChatMessageRequest(String message) implements Serializable {}
