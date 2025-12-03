package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record InviteNotification(String fromUsername) implements Serializable {}
