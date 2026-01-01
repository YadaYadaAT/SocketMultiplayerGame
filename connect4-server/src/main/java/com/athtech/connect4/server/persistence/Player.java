package com.athtech.connect4.server.persistence;

import java.time.Instant;

public class Player {
    private final String id;
    private final String username;
    private String password;
    private String nickname;
    private int wins;
    private int losses;
    private int draws;
    private int gamesPlayed;
    private String relogCode;
    private final Instant createdAt;  // new field

    public Player(String id, String username, String password, String nickname,
                  int wins, int losses, int draws, int gamesPlayed, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
        this.gamesPlayed = gamesPlayed;
        this.relogCode = null;
        this.createdAt = createdAt;
    }

    // getters & setters
    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }
    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }
    public int getDraws() { return draws; }
    public void setDraws(int draws) { this.draws = draws; }
    public int getGamesPlayed() { return gamesPlayed; }
    public void setGamesPlayed(int gamesPlayed) { this.gamesPlayed = gamesPlayed; }
    public void incrementWins() { wins++; gamesPlayed++; }
    public void incrementLosses() { losses++; gamesPlayed++; }
    public void incrementDraws() { draws++; gamesPlayed++; }
    public String getRelogCode() { return relogCode; }
    public void setRelogCode(String relogCode) { this.relogCode = relogCode; }
    public void invalidateRelogCode() { this.relogCode = null; }
    public Instant getCreatedAt() { return createdAt; } // getter
}
