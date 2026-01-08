package com.athtech.gomoku.client.gui;

import com.athtech.gomoku.protocol.payload.*;
import java.util.*;

public class GomokuFXCommonToAllControllersData {


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
        username = null;
        nickname = null;
        relogCode = null;
        loggedIn = false;
    }

}
