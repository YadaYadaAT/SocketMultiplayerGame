package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record GameEnd(String reason, String opponent) implements Serializable { }
