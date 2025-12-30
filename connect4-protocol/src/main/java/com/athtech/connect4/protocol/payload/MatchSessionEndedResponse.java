package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record MatchSessionEndedResponse(Boolean isRematchOn) implements Serializable {}
