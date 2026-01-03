package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record MatchSessionEndedResponse(Boolean isRematchOn) implements Serializable {}
