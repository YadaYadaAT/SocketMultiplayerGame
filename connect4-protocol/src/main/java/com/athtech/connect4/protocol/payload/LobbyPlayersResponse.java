package com.athtech.connect4.protocol.payload;

import java.io.Serializable;
import java.util.List;

public record LobbyPlayersResponse(List<String> players) implements Serializable {}
