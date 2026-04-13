package com.example.flight_booking_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class KeycloakAdminService {

    @Value("${keycloak.admin.url}")
    private String keycloakUrl;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.client-secret}")
    private String clientSecret;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Get admin access token
    private String getAdminToken() throws Exception {
        String body = "grant_type=client_credentials" +
                "&client_id=" + clientId +
                "&client_secret=" + clientSecret;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        // Extract access_token from response
        int start = responseBody.indexOf("\"access_token\":\"") + 16;
        int end = responseBody.indexOf("\"", start);
        return responseBody.substring(start, end);
    }

    // Get Keycloak user ID by email
    private String getUserId(String adminToken, String email) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users?email=" + email + "&exact=true"))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        // Extract id from first user in array
        int start = responseBody.indexOf("\"id\":\"") + 6;
        int end = responseBody.indexOf("\"", start);
        return responseBody.substring(start, end);
    }

    // Update password in Keycloak
    public void updatePassword(String email, String newPassword) throws Exception {
        String adminToken = getAdminToken();
        String userId = getUserId(adminToken, email);

        String body = """
                {
                    "type": "password",
                    "value": "%s",
                    "temporary": false
                }
                """.formatted(newPassword.replace("\"", "\\\""));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/reset-password"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new Exception("Keycloak password update failed: " + response.body());
        }
    }
}