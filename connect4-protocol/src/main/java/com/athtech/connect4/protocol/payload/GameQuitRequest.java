package com.athtech.connect4.protocol.payload;

import java.io.Serializable;

public record GameQuitRequest(boolean isUnstuckProcess) implements Serializable {
}
