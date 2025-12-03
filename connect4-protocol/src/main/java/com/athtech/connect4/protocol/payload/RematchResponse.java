package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record RematchResponse(boolean accepted, String message) implements Serializable {}
