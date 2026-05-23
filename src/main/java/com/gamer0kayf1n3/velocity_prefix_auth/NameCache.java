package com.gamer0kayf1n3.velocity_prefix_auth;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class NameCache {
    private final ConcurrentMap<String, Boolean> cache = new ConcurrentHashMap<>();

    public void put(String username, boolean isPremium) {
        cache.put(username.toLowerCase(), isPremium);
    }

    public Boolean get(String username) {
        return cache.get(username.toLowerCase());
    }

    public boolean contains(String username) {
        return cache.containsKey(username.toLowerCase());
    }
}
