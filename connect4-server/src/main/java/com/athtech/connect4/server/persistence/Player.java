package com.athtech.connect4.server.persistence;

public class Player {
    private final String id;
    private final String username;
    private String password; // optionally mutable
    private int wins;
    private int losses;
    private int draws;
    private int gamesPlayed;
    private String relogCode; // <-- new field for reconnect

    public Player(String id, String username, String password, int wins, int losses, int draws, int gamesPlayed) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
        this.gamesPlayed = gamesPlayed;
        this.relogCode = null;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

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

    // --- Relog code methods ---
    public String getRelogCode() { return relogCode; }
    public void setRelogCode(String relogCode) { this.relogCode = relogCode; }
    public void invalidateRelogCode() { this.relogCode = null; }
}
