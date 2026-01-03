package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record RematchDecisionRequest(String opponentUsername, boolean accepted) implements Serializable {
}
