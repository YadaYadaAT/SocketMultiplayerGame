package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record BoardState(char[][] cells) implements Serializable {}
