package com.example.mcrcon.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration class for the Minecraft RCON Discord Bot
 */
@Data
@NoArgsConstructor
public class BotConfig {
    
    @JsonProperty("discord")
    private DiscordConfig discord;
    
    @JsonProperty("minecraft")
    private MinecraftConfig minecraft;
    
    @JsonProperty("bot")
    private BotSettings bot;
    
    @JsonProperty("status_pages")
    private StatusPagesConfig statusPages;
    
    @Data
    @NoArgsConstructor
    public static class DiscordConfig {
        @JsonProperty("token")
        private String token;
        
        @JsonProperty("guild_id")
        private String guildId;
        
        @JsonProperty("allowed_roles")
        private String[] allowedRoles;
        
        @JsonProperty("admin_role")
        private String adminRole;
    }
    
    @Data
    @NoArgsConstructor
    public static class MinecraftConfig {
        @JsonProperty("host")
        private String host;
        
        @JsonProperty("port")
        private int port;
        
        @JsonProperty("password")
        private String password;
        
        @JsonProperty("timeout_seconds")
        private int timeoutSeconds = 10;
    }
    
    @Data
    @NoArgsConstructor
    public static class BotSettings {
        @JsonProperty("whitelist_cooldown_minutes")
        private int whitelistCooldownMinutes = 60;
        
        @JsonProperty("max_username_length")
        private int maxUsernameLength = 16;
        
        @JsonProperty("enable_duplicate_check")
        private boolean enableDuplicateCheck = true;
    }
    
    @Data
    @NoArgsConstructor
    public static class StatusPagesConfig {
        @JsonProperty("pages")
        private StatusPage[] pages = new StatusPage[0];
        
        @Data
        @NoArgsConstructor
        public static class StatusPage {
            @JsonProperty("channel_id")
            private String channelId;
            
            @JsonProperty("message_id")
            private String messageId;
            
            @JsonProperty("guild_id")
            private String guildId;
            
            @JsonProperty("last_updated")
            private long lastUpdated;
            
            public StatusPage(String channelId, String messageId, String guildId, long lastUpdated) {
                this.channelId = channelId;
                this.messageId = messageId;
                this.guildId = guildId;
                this.lastUpdated = lastUpdated;
            }
        }
    }
}