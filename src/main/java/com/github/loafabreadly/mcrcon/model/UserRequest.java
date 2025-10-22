package com.github.loafabreadly.mcrcon.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Model class for tracking user whitelist requests
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {
    private String userId;
    private String username;
    private String discordUsername;
    private Instant requestTime;
    private boolean successful;
    private String errorMessage;
    
    /**
     * Create a successful request
     */
    public static UserRequest success(String userId, String username, String discordUsername) {
        return new UserRequest(userId, username, discordUsername, Instant.now(), true, null);
    }
    
    /**
     * Create a failed request
     */
    public static UserRequest failure(String userId, String username, String discordUsername, String errorMessage) {
        return new UserRequest(userId, username, discordUsername, Instant.now(), false, errorMessage);
    }
}