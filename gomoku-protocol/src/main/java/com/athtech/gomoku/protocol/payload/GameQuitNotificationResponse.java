package com.athtech.gomoku.protocol.payload;

import java.io.Serializable;

public record GameQuitNotificationResponse(String quitter) implements Serializable {}
