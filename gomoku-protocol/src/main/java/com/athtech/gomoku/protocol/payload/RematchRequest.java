package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record RematchRequest(Boolean decision) implements Serializable {}
