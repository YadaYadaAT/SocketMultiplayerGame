package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record InviteDecisionResponse(String inviterUsername, String targetUsername, boolean accepted) implements Serializable {}
