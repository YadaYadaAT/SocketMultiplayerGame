package com.athtech.connect4.protocol.payload;

import java.io.Serializable;
import java.util.List;

public record LobbyPlayers(List<String> players) implements Serializable {}
