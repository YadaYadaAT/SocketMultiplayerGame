package com.athtech.gomoku.client.gui;

import com.athtech.gomoku.protocol.payload.*;
import java.util.*;

public class GomokuFXCommonToAllControllersData {

    private volatile long lastServerActivity = System.currentTimeMillis();
    private volatile String username;
    private volatile String nickname;
    private volatile String relogCode;
    private volatile PlayerStatsResponse myStats;
    private volatile List<InviteNotificationResponse> invites = Collections.emptyList();

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    private volatile boolean loggedIn = false;


    public long getLastServerActivity() {
        return lastServerActivity;
    }

    public void setLastServerActivity(long lastServerActivity) {
        this.lastServerActivity = lastServerActivity;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
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

    public PlayerStatsResponse getMyStats() {
        return myStats;
    }

    public void setMyStats(PlayerStatsResponse myStats) {
        this.myStats = myStats;
    }


    public List<InviteNotificationResponse> getInvites() {
        return invites;
    }

    public void setInvites(InviteNotificationResponse[] invitesArray) {
        // Immutable copy to avoid race conditions on array
        if (invitesArray == null || invitesArray.length == 0) {
            this.invites = Collections.emptyList();
        } else {
            this.invites = Collections.unmodifiableList(Arrays.asList(invitesArray.clone()));
        }
    }

}
