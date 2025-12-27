package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record ReconnectRequest(String username, String relogCode) implements Serializable { }
