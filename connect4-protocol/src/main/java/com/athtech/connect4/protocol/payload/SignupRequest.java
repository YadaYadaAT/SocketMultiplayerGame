package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record SignupRequest(String username, String password) implements Serializable {}
