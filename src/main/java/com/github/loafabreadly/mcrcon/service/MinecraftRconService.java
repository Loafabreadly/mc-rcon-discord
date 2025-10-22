package com.github.loafabreadly.mcrcon.service;

import com.github.loafabreadly.mcrcon.config.BotConfig;
import com.github.loafabreadly.mcrcon.rcon.RconClient;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for handling Minecraft server RCON communication
 */
public class MinecraftRconService {
    
    private static final Logger logger = LoggerFactory.getLogger(MinecraftRconService.class);
    
    // Regex patterns for parsing RCON responses
    private static final Pattern WHITELIST_LIST_PATTERN = Pattern.compile("There are (\\d+) whitelisted players?(?:\\(s\\))?: (.*)");
    private static final Pattern PLAYER_EXISTS_PATTERN = Pattern.compile("Player .+ is already whitelisted");
    private static final Pattern PLAYER_ADDED_PATTERN = Pattern.compile("Added .+ to the whitelist");
    private static final Pattern PLAYER_NOT_FOUND_PATTERN = Pattern.compile("Player .+ does not exist");
    
    private final BotConfig.MinecraftConfig config;
    
    public MinecraftRconService(BotConfig.MinecraftConfig config) {
        this.config = config;
    }
    
    /**
     * Test RCON connection
     */
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try (RconClient client = createRconClient()) {
                client.sendCommand("list");
                logger.info("RCON connection test successful");
                return true;
            } catch (Exception e) {
                logger.error("RCON connection test failed", e);
                return false;
            }
        });
    }
    
    /**
     * Get the current whitelist
     */
    public CompletableFuture<WhitelistInfo> getWhitelist() {
        return CompletableFuture.supplyAsync(() -> {
            try (RconClient client = createRconClient()) {
                String response = client.sendCommand("whitelist list");
                return parseWhitelistResponse(response);
            } catch (Exception e) {
                logger.error("Failed to get whitelist", e);
                throw new RconException("Failed to retrieve whitelist: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Check if a player is whitelisted
     */
    public CompletableFuture<Boolean> isPlayerWhitelisted(String username) {
        return getWhitelist().thenApply(whitelist -> 
            Arrays.stream(whitelist.getPlayers())
                    .anyMatch(player -> player.equalsIgnoreCase(username))
        );
    }
    
    /**
     * Add a player to the whitelist
     */
    public CompletableFuture<WhitelistResult> addPlayerToWhitelist(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try (RconClient client = createRconClient()) {
                String response = client.sendCommand("whitelist add " + username);
                return parseWhitelistAddResponse(response, username);
            } catch (Exception e) {
                logger.error("Failed to add player {} to whitelist", username, e);
                throw new RconException("Failed to add player to whitelist: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Remove a player from the whitelist
     */
    public CompletableFuture<WhitelistResult> removePlayerFromWhitelist(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try (RconClient client = createRconClient()) {
                String response = client.sendCommand("whitelist remove " + username);
                return parseWhitelistRemoveResponse(response, username);
            } catch (Exception e) {
                logger.error("Failed to remove player {} from whitelist", username, e);
                throw new RconException("Failed to remove player from whitelist: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Execute a generic RCON command
     */
    public CompletableFuture<String> executeCommand(String command) {
        return CompletableFuture.supplyAsync(() -> {
            try (RconClient client = createRconClient()) {
                String response = client.sendCommand(command);
                logger.debug("Executed command '{}' with response: {}", command, response);
                return response;
            } catch (Exception e) {
                logger.error("Failed to execute command '{}'", command, e);
                throw new RconException("Failed to execute command: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Get server player list
     */
    public CompletableFuture<ServerInfo> getServerInfo() {
        return CompletableFuture.supplyAsync(() -> {
            try (RconClient client = createRconClient()) {
                String listResponse = client.sendCommand("list");
                String tpsResponse = client.sendCommand("tps");
                String versionResponse = client.sendCommand("version");
                return parseServerInfoResponse(listResponse, tpsResponse, versionResponse);
            } catch (Exception e) {
                logger.error("Failed to get server info", e);
                throw new RconException("Failed to retrieve server info: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Create and connect RCON client
     */
    private RconClient createRconClient() throws IOException {
        try {
            RconClient client = new RconClient(config.getHost(), config.getPort());
            client.connect(config.getPassword(), config.getTimeoutSeconds() * 1000);
            return client;
        } catch (SocketTimeoutException e) {
            throw new IOException("RCON connection timeout - server may be unreachable", e);
        } catch (IOException e) {
            throw new IOException("RCON connection failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse whitelist list response
     */
    private WhitelistInfo parseWhitelistResponse(String response) {
        Matcher matcher = WHITELIST_LIST_PATTERN.matcher(response);
        if (matcher.find()) {
            int count = Integer.parseInt(matcher.group(1));
            String playersStr = matcher.group(2);
            
            String[] players = new String[0];
            if (count > 0 && !playersStr.trim().isEmpty()) {
                // Split by comma and filter out empty/whitespace-only entries
                players = Arrays.stream(playersStr.split(", "))
                        .map(String::trim)
                        .filter(player -> !player.isEmpty())
                        .toArray(String[]::new);
            }
            
            return new WhitelistInfo(count, players);
        } else {
            logger.warn("Unexpected whitelist response format: {}", response);
            return new WhitelistInfo(0, new String[0]);
        }
    }
    
    /**
     * Parse whitelist add response
     */
    private WhitelistResult parseWhitelistAddResponse(String response, String username) {
        if (PLAYER_ADDED_PATTERN.matcher(response).find()) {
            return new WhitelistResult(true, "Player " + username + " successfully added to whitelist");
        } else if (PLAYER_EXISTS_PATTERN.matcher(response).find()) {
            return new WhitelistResult(false, "Player " + username + " is already whitelisted");
        } else if (PLAYER_NOT_FOUND_PATTERN.matcher(response).find()) {
            return new WhitelistResult(false, "Player " + username + " does not exist (invalid username)");
        } else {
            logger.warn("Unexpected whitelist add response: {}", response);
            return new WhitelistResult(false, "Unexpected response: " + response);
        }
    }
    
    /**
     * Parse whitelist remove response
     */
    private WhitelistResult parseWhitelistRemoveResponse(String response, String username) {
        if (response.contains("Removed " + username + " from the whitelist")) {
            return new WhitelistResult(true, "Player " + username + " successfully removed from whitelist");
        } else if (response.contains("Player is not whitelisted")) {
            return new WhitelistResult(false, "Player " + username + " is not whitelisted");
        } else {
            logger.warn("Unexpected whitelist remove response: {}", response);
            return new WhitelistResult(false, "Unexpected response: " + response);
        }
    }
    
    /**
     * Parse server info response
     */
    private ServerInfo parseServerInfoResponse(String listResponse, String tpsResponse, String versionResponse) {
        // Parse player count and list from "list" command
        Pattern listPattern = Pattern.compile("There are (\\d+) of a max of (\\d+) players online:?(.*)");
        Matcher listMatcher = listPattern.matcher(listResponse);
        
        int onlinePlayers = 0;
        int maxPlayers = 0;
        String[] playerList = new String[0];
        
        if (listMatcher.find()) {
            onlinePlayers = Integer.parseInt(listMatcher.group(1));
            maxPlayers = Integer.parseInt(listMatcher.group(2));
            String playersStr = listMatcher.group(3).trim();
            if (!playersStr.isEmpty()) {
                playerList = playersStr.split(", ");
            }
        }
        
        // Parse TPS (if available)
        double tps = 20.0; // Default TPS
        if (tpsResponse != null && !tpsResponse.contains("Unknown command")) {
            Pattern tpsPattern = Pattern.compile("TPS: ([0-9.]+)");
            Matcher tpsMatcher = tpsPattern.matcher(tpsResponse);
            if (tpsMatcher.find()) {
                tps = Double.parseDouble(tpsMatcher.group(1));
            }
        }
        
        // Parse server version
        String serverVersion = "Unknown";
        if (versionResponse != null && !versionResponse.contains("Unknown command")) {
            // Try different version response patterns
            Pattern versionPattern1 = Pattern.compile("This server is running (.+?) version (.+?) \\(");
            Pattern versionPattern2 = Pattern.compile("(.+?) version (.+?)");
            Pattern versionPattern3 = Pattern.compile("(\\S+)\\s+version\\s+(\\S+)");
            
            Matcher matcher1 = versionPattern1.matcher(versionResponse);
            Matcher matcher2 = versionPattern2.matcher(versionResponse);
            Matcher matcher3 = versionPattern3.matcher(versionResponse);
            
            if (matcher1.find()) {
                serverVersion = matcher1.group(1) + " " + matcher1.group(2);
            } else if (matcher2.find()) {
                serverVersion = matcher2.group(1) + " " + matcher2.group(2);
            } else if (matcher3.find()) {
                serverVersion = matcher3.group(1) + " " + matcher3.group(2);
            } else {
                // Fallback to first part of response if patterns don't match
                String[] parts = versionResponse.split("\\s+");
                if (parts.length >= 2) {
                    serverVersion = parts[0] + " " + parts[1];
                }
            }
        }
        
        return new ServerInfo(onlinePlayers, maxPlayers, playerList, tps, serverVersion);
    }
    
    /**
     * Whitelist information data class
     */
    @Data
    @AllArgsConstructor
    public static class WhitelistInfo {
        private final int count;
        private final String[] players;
    }
    
    /**
     * Whitelist operation result
     */
    @Data
    @AllArgsConstructor
    public static class WhitelistResult {
        private final boolean success;
        private final String message;
    }
    
    /**
     * Server information data class
     */
    @Data
    @AllArgsConstructor
    public static class ServerInfo {
        private final int onlinePlayers;
        private final int maxPlayers;
        private final String[] playerList;
        private final double tps;
        private final String serverVersion;
    }
    
    /**
     * Custom exception for RCON operations
     */
    public static class RconException extends RuntimeException {
        public RconException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}