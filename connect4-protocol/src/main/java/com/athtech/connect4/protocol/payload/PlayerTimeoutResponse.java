package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record PlayerTimeoutResponse(String username) implements Serializable {}
