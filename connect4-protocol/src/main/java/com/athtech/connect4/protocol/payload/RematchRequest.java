package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record RematchRequest(String opponentUsername) implements Serializable {}
