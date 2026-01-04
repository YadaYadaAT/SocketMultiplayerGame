package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record RematchResponse( boolean midGame, String message) implements Serializable {}
