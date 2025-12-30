package com.athtech.connect4.server.match;

import com.athtech.connect4.protocol.payload.BoardState;
import com.athtech.connect4.protocol.payload.GameStateResponse;
import com.athtech.connect4.protocol.payload.MoveRequest;
import com.athtech.connect4.server.game.Game;

import java.util.*;

public class MatchImpl implements Match {

    private final String matchId;
    private final Game game;
    private final Set<String> activePlayers =
            Collections.synchronizedSet(new HashSet<>());

    private final Map<String, RematchVote> rematchVotes =
            Collections.synchronizedMap(new HashMap<>());

    private boolean ended = false;
    private long lastActivityTime;

    public MatchImpl(String player1, String player2) {
        this.matchId = UUID.randomUUID().toString();
        this.game = new Game(player1, player2);
        activePlayers.add(player1);
        activePlayers.add(player2);
        rematchVotes.put(player1, RematchVote.PENDING);
        rematchVotes.put(player2, RematchVote.PENDING);

        touch();
    }

    @Override
    public synchronized void requestRematch(String player) {
        if (!ended)
            throw new IllegalStateException("Game is not over yet");
        if (!activePlayers.contains(player))
            throw new IllegalStateException("Player requesting the rematch doesnt exist in the match");

        String opponent = getTheOpponentFromRematchVote(player);
        if (opponent == null){//since opponent is extracted from the rematch list which players are never deleted)
            removePlayer(player);
            throw new IllegalStateException("We are sorry there has been an internal error we cant offer a rematch");
        }
        if (rematchVotes.get(opponent) == RematchVote.DECLINED  ){
            removePlayer(player);
            throw new IllegalStateException("Opponent has already declined the rematch request");
        }
        if (rematchVotes.get(opponent) == RematchVote.UNAVAILABLE ){
            removePlayer(player);
            throw new IllegalStateException("Opponent left without voting for a rematch");
        }
        //any other case like accepted or pending the rematch vote pass
        rematchVotes.put(player, RematchVote.ACCEPTED);
    }

    @Override
    public synchronized void declineRematch(String player) {
        if (!ended || !activePlayers.contains(player)) return;

        rematchVotes.put(player, RematchVote.DECLINED);
        removePlayer(player);
    }

    @Override
    public synchronized void markUnavailable(String player) {
        if (ended || !activePlayers.contains(player)) return;

        rematchVotes.put(player, RematchVote.UNAVAILABLE);
        removePlayer(player);
    }

    @Override
    public synchronized boolean isRematchReady() {
        return rematchVotes.size() == 2 &&
                rematchVotes.values().stream()
                        .allMatch(d -> d == RematchVote.ACCEPTED);
    }

    @Override
    public synchronized RematchVote getRematchOutcome() {
        if (rematchVotes.values().contains(RematchVote.DECLINED))
            return RematchVote.DECLINED;

        if (rematchVotes.values().contains(RematchVote.UNAVAILABLE))
            return RematchVote.UNAVAILABLE;

        return null;
    }

    @Override
    public synchronized void resetRematchRequests() {
        rematchVotes.replaceAll((k, v) -> null);
    }

    // Player / match removal

    private synchronized void removePlayer(String player) {
        activePlayers.remove(player);

        if (activePlayers.isEmpty()) {
            ended = true;
        }
    }

    @Override
    public synchronized Set<String> getActivePlayers() {
        return new HashSet<>(activePlayers);
    }

    @Override
    public synchronized boolean markEnded() {
        ended = true;
        return true;
    }

    @Override
    public synchronized boolean isEnded() {
        return ended;
    }

    // Match / Game info

    @Override
    public void touch() {
        lastActivityTime = System.currentTimeMillis();
    }

    @Override
    public boolean isInactive(long timeoutMs) {
        return System.currentTimeMillis() - lastActivityTime > timeoutMs;
    }

    @Override
    public String getMatchId() {
        return matchId;
    }

    @Override
    public String getPlayer1() {
        return game.getPlayer1();
    }

    @Override
    public String getPlayer2() {
        return game.getPlayer2();
    }

    @Override
    public String getCurrentPlayer() {
        return game.getCurrentPlayer();
    }

    @Override
    public GameStateResponse getCurrentState() {
        return new GameStateResponse(
                new BoardState(game.getBoardCopy()),
                game.getCurrentPlayer(),
                game.isGameOver()
        );
    }

    @Override
    public boolean makeMove(String player, MoveRequest moveRequest) {
        touch();
        if (!player.equals(game.getCurrentPlayer())) return false;
        return game.makeMove(moveRequest.row(), moveRequest.col());
    }

    @Override
    public boolean isFinished() {
        return game.isGameOver();
    }

    @Override
    public String getWinner() {
        if (!game.isGameOver()) return null;
        return game.getWinner();
    }

    @Override
    public String getLoser() {
        if (!game.isGameOver()) return null;
        String winner = game.getWinner();
        if (winner == null) return null;
        return winner.equals(getPlayer1()) ? getPlayer2() : getPlayer1();
    }

    @Override
    public boolean isDraw() {
        return game.isGameOver() && game.getWinner() == null;
    }

    private String getTheOpponentFromRematchVote(String requester) {
        return rematchVotes.keySet().stream()
                .filter(p -> !p.equals(requester))
                .findFirst()
                .orElse(null);
    }
}
