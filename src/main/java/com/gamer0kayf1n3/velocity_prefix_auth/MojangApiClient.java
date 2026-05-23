package com.gamer0kayf1n3.velocity_prefix_auth;


import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.google.gson.Gson;

public class MojangApiClient {

    public static class PlayerInfo {

        public final String uuid;
        public final String username;
        public final Long expiresAt;
        public final String dataSource;

        public PlayerInfo(String uuid, String username, Long expiresAt) {
            this.uuid = uuid;
            this.username = username;
            this.expiresAt = expiresAt;
            this.dataSource = null;
        }

        private PlayerInfo(String uuid, String username, Long expiresAt, String dataSource) {
            this.uuid = uuid;
            this.username = username;
            this.expiresAt = expiresAt;
            this.dataSource = dataSource;
        }

        public PlayerInfo withDataSource(String dataSource) {
            return new PlayerInfo(this.uuid, this.username, this.expiresAt, dataSource);
        }
    }   

    public static class MojangResponseJSON {

        public String id;
        public String name;

        public MojangResponseJSON(String id, String name) {
            this.id = id;
            this.name = name;
        }

    }
    @SuppressWarnings({"UseSpecificCatch", "CallToPrintStackTrace"})
    public static PlayerInfo fetchPlayerData(String username) {

        try {

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new java.net.URI("https://api.mojang.com/users/profiles/minecraft/" + username))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());


            if (response.statusCode() != 200) return null;

            long expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000;

            Gson gson = new Gson();
             MojangResponseJSON mojangResponse = gson.fromJson(response.body(),  MojangResponseJSON.class);
            
            if (mojangResponse == null || mojangResponse.id == null) return null;
            
            return new PlayerInfo(mojangResponse.id, mojangResponse.name, expiresAt);

        
        } catch (Exception e) {
            e.printStackTrace();
            return null; // Assume not premium if there's an error
        }
    }
}
