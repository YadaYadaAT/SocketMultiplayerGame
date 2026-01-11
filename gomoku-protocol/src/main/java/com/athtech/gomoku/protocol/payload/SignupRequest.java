package com.athtech.gomoku.protocol.payload;
//STUDENTS-CODE-NUMBER : CSY-22115
import java.io.Serializable;

public record SignupRequest(String username, String password, String nickname) implements Serializable {}
