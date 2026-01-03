package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record PlayerReconnectedResponse(String msg) implements Serializable {}
