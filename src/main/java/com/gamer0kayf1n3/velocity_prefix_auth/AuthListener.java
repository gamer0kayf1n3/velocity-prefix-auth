package com.gamer0kayf1n3.velocity_prefix_auth;

import com.gamer0kayf1n3.velocity_prefix_auth.MojangApiClient.PlayerInfo;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;

import static com.gamer0kayf1n3.velocity_prefix_auth.MojangApiClient.PlayerInfo;

import org.slf4j.Logger;
import java.sql.SQLException;


public class AuthListener {

    private final NameCache nameCache = new NameCache();
    private final NameDatabase nameDatabase;
    private final Logger logger;
    
    @SuppressWarnings({"UseSpecificCatch", "CallToPrintStackTrace"})
    public AuthListener(Logger logger) {
        this.logger = logger;
        try {
        this.nameDatabase = new NameDatabase("player_names.db");
        } catch (SQLException e) {
            logger.error("Failed to initialize name database: {}", e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize name database", e);
        }
    }

    @SuppressWarnings({"UseSpecificCatch", "CallToPrintStackTrace"})
    public PlayerInfo checkPremium(String username) {
        // Ask name cache, then database, then Mojang's API to check if the username is premium or not

        PlayerInfo cachedResult = nameCache.get(username);
        if (cachedResult != null) {
            return cachedResult.withDataSource("cache");
            
        }

        try {
            PlayerInfo dbResult = nameDatabase.getPlayer(username);
            if (dbResult != null) {
                nameCache.put(username, dbResult);
                return dbResult.withDataSource("database");
            }
        } catch (Exception e) {
            logger.error("Failed to query database for player {}: {}", username, e.getMessage());
            e.printStackTrace();
        }

        PlayerInfo apiResult = MojangApiClient.fetchPlayerData(username);
        
        if (apiResult != null) {
            nameCache.put(username, apiResult);
            try {
                nameDatabase.savePlayerName(apiResult);
            } catch (Exception e) {
                logger.error("Failed to save player {} to database: {}", username, e.getMessage());
                e.printStackTrace();
            }
            return apiResult.withDataSource("api");
        }

        PlayerInfo notPremium = new PlayerInfo(null, username, System.currentTimeMillis() + /* TTL */ 3600_000L);
        notPremium = notPremium.withDataSource("api");
        nameCache.put(username, notPremium);
        try {
            nameDatabase.savePlayerName(notPremium);
        } catch (Exception e) {
            logger.error("Failed to save non-premium player {} to database: {}", username, e.getMessage());
        }
        return null;
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {

        String username = event.getUsername();
        PlayerInfo premiumInfo = checkPremium(username);

        boolean isPremium = premiumInfo != null && premiumInfo.uuid != null;

        logger.info("Checked premium status for player {}: {} {}", 
            username, 
            isPremium ? "premium" : "not premium", 
            premiumInfo != null ? "(source: " + premiumInfo.dataSource + ")" : ""
        );
    }
}
