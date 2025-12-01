package com.athtech.connect4.server.persistence;

public class Player {
    private final String id;
    private final String username;
    private String password; // optionally mutable
    private int wins;
    private int losses;
    private int draws;
    private int gamesPlayed;

    public Player(String id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getDraws() { return draws; }
    public int getGamesPlayed() { return gamesPlayed; }
    public void setPassword(String password) { this.password = password; }
    public void incrementWins() { wins++; gamesPlayed++; }
    public void incrementLosses() { losses++; gamesPlayed++; }
    public void incrementDraws() { draws++; gamesPlayed++; }

}

