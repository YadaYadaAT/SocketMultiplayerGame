package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record InviteRequest(String targetUsername) implements Serializable {}
