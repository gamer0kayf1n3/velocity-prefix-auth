package com.gamer0kayf1n3.velocity_prefix_auth.db;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.gamer0kayf1n3.velocity_prefix_auth.MojangApiClient.PlayerInfo;

public class NameCache {
    private final ConcurrentMap<String, PlayerInfo> cache = new ConcurrentHashMap<>();

    public void put(String username, PlayerInfo playerInfo) {
        cache.put(username, playerInfo);
    }

    public PlayerInfo get(String username) {
        return cache.get(username);
    }

    public boolean contains(String username) {
        return cache.containsKey(username);
    }
}
