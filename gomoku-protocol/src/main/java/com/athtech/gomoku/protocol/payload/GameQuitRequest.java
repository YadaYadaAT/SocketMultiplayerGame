package com.athtech.gomoku.protocol.payload;
//STUDENTS-CODE-NUMBER : CSY-22115
import java.io.Serializable;

// CLI version does not support automatic reconnection in case of game crashes. This may cause a process to be blocked.
// The boolean isUnstuckProcess is used by the server to keep track of blocked processes in one such case.
public record GameQuitRequest(boolean isUnstuckProcess) implements Serializable {
}
