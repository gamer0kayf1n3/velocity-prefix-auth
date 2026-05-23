package com.gamer0kayf1n3.velocity_prefix_auth;

import com.velocitypowered.api.event.Subscribe;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MojangApiClient {
    public static boolean checkPremium(String username) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new java.net.URI("https://api.mojang.com/users/profiles/minecraft/" + username))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false; // Assume not premium if there's an error
        }
    }
}
