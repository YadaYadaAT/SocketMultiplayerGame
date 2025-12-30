package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record ResyncRequest(String username, String relogCode) implements Serializable { }
