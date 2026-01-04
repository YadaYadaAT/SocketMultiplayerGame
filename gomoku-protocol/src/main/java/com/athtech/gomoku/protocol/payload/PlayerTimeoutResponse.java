package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record PlayerTimeoutResponse(String username) implements Serializable {}
