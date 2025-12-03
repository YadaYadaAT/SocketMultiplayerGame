package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record ErrorMessage(String message) implements Serializable {}
