package com.athtech.gomoku.protocol.payload;
//STUDENTS-CODE-NUMBER : CSY-22115
import java.io.Serializable;

// boolean midGame is set to true if the rematch request was sent on a running game
// boolean midGame is set to false if the rematch request was sent after a game has ended
// if midGame is set to true and the request is sent after game end, it is not accepted
public record RematchResponse( boolean midGame, String message) implements Serializable {}
