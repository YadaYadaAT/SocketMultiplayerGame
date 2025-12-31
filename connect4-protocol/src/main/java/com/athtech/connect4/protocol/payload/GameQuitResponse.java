package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record GameQuitResponse(boolean isGameQuitReqAccepted) implements Serializable {
}
