package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record LoginResponse(boolean success, String message) implements Serializable {}
