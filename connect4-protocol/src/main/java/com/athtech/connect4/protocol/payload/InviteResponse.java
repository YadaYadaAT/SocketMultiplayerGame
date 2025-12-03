package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record InviteResponse(boolean delivered, String reason) implements Serializable {}
