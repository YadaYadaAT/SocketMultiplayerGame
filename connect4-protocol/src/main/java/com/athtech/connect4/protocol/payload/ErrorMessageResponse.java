package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record ErrorMessageResponse(String message) implements Serializable {}
