package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record MoveRequest(int row, int col) implements Serializable {}
