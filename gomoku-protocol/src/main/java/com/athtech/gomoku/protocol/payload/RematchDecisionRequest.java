package com.athtech.gomoku.protocol.payload;
//STUDENTS-CODE-NUMBER : CSY-22115
import java.io.Serializable;

public record RematchDecisionRequest(String opponentUsername, boolean accepted) implements Serializable {
}
