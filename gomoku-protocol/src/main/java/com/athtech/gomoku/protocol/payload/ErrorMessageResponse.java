package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record ErrorMessageResponse(String message) implements Serializable {}
