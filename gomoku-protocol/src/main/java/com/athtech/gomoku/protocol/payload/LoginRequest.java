package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record LoginRequest(String username, String password) implements Serializable {}
