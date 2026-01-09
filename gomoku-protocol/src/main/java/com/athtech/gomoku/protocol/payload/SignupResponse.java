package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

// If username already exists or does not follow guidelines, success is set to false
public record SignupResponse(boolean success, String message) implements Serializable {}
