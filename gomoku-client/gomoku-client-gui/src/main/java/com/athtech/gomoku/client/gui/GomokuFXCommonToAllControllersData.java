package com.athtech.gomoku.client.gui;

import com.athtech.gomoku.protocol.payload.*;
import java.util.*;

public class GomokuFXCommonToAllControllersData {

    private volatile long lastServerActivity = System.currentTimeMillis();
    private volatile String username;
    private volatile String nickname;
    private volatile String relogCode;

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    private volatile boolean loggedIn = false;


    public void setLastServerActivity(long lastServerActivity) {
        this.lastServerActivity = lastServerActivity;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRelogCode() {
        return relogCode;
    }

    public void setRelogCode(String relogCode) {
        this.relogCode = relogCode;
    }


    public void reset() {
        lastServerActivity = System.currentTimeMillis();
        username = null;
        nickname = null;
        relogCode = null;
        loggedIn = false;
    }

}
