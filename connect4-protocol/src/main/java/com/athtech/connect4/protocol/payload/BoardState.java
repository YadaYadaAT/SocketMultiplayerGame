package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record BoardState(char[][] cells) implements Serializable {}
