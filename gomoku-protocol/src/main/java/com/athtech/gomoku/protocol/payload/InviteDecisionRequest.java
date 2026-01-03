package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record InviteDecisionRequest(String inviterUsername, boolean accepted) implements Serializable {}
