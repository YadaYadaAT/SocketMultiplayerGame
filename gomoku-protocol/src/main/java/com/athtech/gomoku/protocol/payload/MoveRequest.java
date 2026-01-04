package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record MoveRequest(int row, int col) implements Serializable {}
