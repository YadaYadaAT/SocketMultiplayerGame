package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record ResyncRequest(String username, String relogCode) implements Serializable { }
