package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record SignupResponse(boolean success, String message) implements Serializable {}
