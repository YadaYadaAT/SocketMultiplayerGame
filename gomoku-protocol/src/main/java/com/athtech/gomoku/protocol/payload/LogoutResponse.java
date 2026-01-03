package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record LogoutResponse(boolean success, String message) implements Serializable {}
