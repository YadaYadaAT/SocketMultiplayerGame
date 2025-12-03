package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record GameEnd(String reason) implements Serializable { }
