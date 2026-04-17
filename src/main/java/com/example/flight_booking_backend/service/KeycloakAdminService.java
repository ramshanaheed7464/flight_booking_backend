package com.example.flight_booking_backend.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class KeycloakAdminService {

    @Value("${keycloak.admin.url}")
    private String keycloakUrl;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.frontend.client-id}")
    private String frontendClientId;

    @Value("${keycloak.admin.client-secret}")
    private String clientSecret;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String getAdminToken() throws Exception {
        String body = "grant_type=client_credentials" +
                "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("Failed to obtain admin token: " + response.body());
        }
        return new JSONObject(response.body()).getString("access_token");
    }

    private String getUserId(String adminToken, String email) throws Exception {
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(keycloakUrl + "/admin/realms/" + realm + "/users?email=" + encodedEmail + "&exact=true"))
                .header("Authorization", "Bearer " + adminToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("Failed to look up user in Keycloak: " + response.body());
        }
        JSONArray users = new JSONArray(response.body());
        if (users.length() == 0) {
            throw new Exception("User not found in Keycloak: " + email);
        }
        return users.getJSONObject(0).getString("id");
    }

    public void updatePassword(String email, String newPassword) throws Exception {
        String adminToken = getAdminToken();
        String userId = getUserId(adminToken, email);

        // Escape backslash first, then double-quote, to produce valid JSON
        String safePassword = newPassword.replace("\\", "\\\\").replace("\"", "\\\"");
        String body = """
                {
                    "type": "password",
                    "value": "%s",
                    "temporary": false
                }
                """.formatted(safePassword);

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

    public void verifyAndUpdatePassword(String email, String currentPassword, String newPassword) throws Exception {
        String verifyBody = "grant_type=password" +
                "&client_id=" + URLEncoder.encode(frontendClientId, StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                "&username=" + URLEncoder.encode(email, StandardCharsets.UTF_8) +
                "&password=" + URLEncoder.encode(currentPassword, StandardCharsets.UTF_8);

        HttpRequest verifyRequest = HttpRequest.newBuilder()
                .uri(URI.create(keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(verifyBody))
                .build();

        HttpResponse<String> verifyResponse = httpClient.send(verifyRequest, HttpResponse.BodyHandlers.ofString());
        if (verifyResponse.statusCode() != 200) {
            throw new Exception("Current password is incorrect");
        }

        updatePassword(email, newPassword);
    }
}
