package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record InviteResponse(boolean delivered, String reason) implements Serializable {}
