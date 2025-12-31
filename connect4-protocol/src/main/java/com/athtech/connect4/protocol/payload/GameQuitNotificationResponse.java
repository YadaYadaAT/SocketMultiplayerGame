package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record GameQuitNotificationResponse(String quitter) implements Serializable {}
