package com.gamer0kayf1n3.velocity_prefix_auth;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;

import static com.gamer0kayf1n3.velocity_prefix_auth.MojangApiClient.checkPremium;
import com.gamer0kayf1n3.velocity_prefix_auth.NameCache;

import org.slf4j.Logger;

public class AuthListener {
    private final NameCache nameCache = new NameCache();
    private final Logger logger;

    public AuthListener(Logger logger) {
        this.logger = logger;
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        // Ask Name cache, Mojang's API to check if the username is premium or not

        String username = event.getUsername();

        Boolean cachedResult = nameCache.get(username);

        Boolean isPremium = false;
        String state = "";


        if (cachedResult != null) {
            state = " (cached)";
            isPremium = cachedResult;
        } else {
            isPremium = checkPremium(username);
            nameCache.put(username, isPremium);
        }


        logger.info("Player {} is {}premium{}", username, isPremium ? "" : "not ", state);

    }
}
