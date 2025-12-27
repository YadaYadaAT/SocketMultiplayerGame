package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record MoveRejectedResponse(String reason) implements Serializable {}
