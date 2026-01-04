package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record InviteRequest(String targetUsername) implements Serializable {}
