package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record InviteNotificationResponse(String fromUsername) implements Serializable {}
