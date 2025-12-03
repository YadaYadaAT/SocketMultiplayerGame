package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record RematchRequest(String requester) implements Serializable {}
