package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record InviteNotificationResponse(String fromUsername) implements Serializable {}
