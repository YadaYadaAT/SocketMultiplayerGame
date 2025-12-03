package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record Move(int row, int col) implements Serializable {}
