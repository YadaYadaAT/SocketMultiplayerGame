package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record RematchNotificationResponse(String requester) implements Serializable {}
