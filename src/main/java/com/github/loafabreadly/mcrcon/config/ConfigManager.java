package com.github.loafabreadly.mcrcon.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration manager for loading bot configuration
 */
public class ConfigManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE_NAME = "config.json";
    
    private final ObjectMapper objectMapper;
    private BotConfig config;
    
    public ConfigManager() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Load configuration from file or environment variables
     */
    public BotConfig loadConfig() {
        try {
            // First try to load from file
            Path configPath = Paths.get(CONFIG_FILE_NAME);
            if (Files.exists(configPath)) {
                logger.info("Loading configuration from {}", CONFIG_FILE_NAME);
                config = objectMapper.readValue(configPath.toFile(), BotConfig.class);
            } else {
                logger.info("Config file not found, creating from environment variables");
                config = createConfigFromEnvironment();
            }
            
            // Override with environment variables if present
            overrideWithEnvironmentVariables();
            
            validateConfig();
            return config;
            
        } catch (IOException e) {
            logger.error("Failed to load configuration", e);
            throw new RuntimeException("Configuration loading failed", e);
        }
    }
    
    /**
     * Create configuration from environment variables
     */
    private BotConfig createConfigFromEnvironment() {
        BotConfig config = new BotConfig();
        
        // Discord configuration
        BotConfig.DiscordConfig discordConfig = new BotConfig.DiscordConfig();
        discordConfig.setToken(getEnvVar("DISCORD_TOKEN"));
        discordConfig.setGuildId(getEnvVar("DISCORD_GUILD_ID", null));
        
        String allowedRoles = getEnvVar("DISCORD_ALLOWED_ROLES", "");
        if (!allowedRoles.isEmpty()) {
            discordConfig.setAllowedRoles(allowedRoles.split(","));
        } else {
            discordConfig.setAllowedRoles(new String[0]);
        }
        
        discordConfig.setAdminRole(getEnvVar("DISCORD_ADMIN_ROLE", null));
        config.setDiscord(discordConfig);
        
        // Minecraft configuration
        BotConfig.MinecraftConfig minecraftConfig = new BotConfig.MinecraftConfig();
        minecraftConfig.setHost(getEnvVar("MINECRAFT_RCON_HOST", "localhost"));
        minecraftConfig.setPort(Integer.parseInt(getEnvVar("MINECRAFT_RCON_PORT", "25575")));
        minecraftConfig.setPassword(getEnvVar("MINECRAFT_RCON_PASSWORD"));
        minecraftConfig.setTimeoutSeconds(Integer.parseInt(getEnvVar("MINECRAFT_RCON_TIMEOUT", "10")));
        config.setMinecraft(minecraftConfig);
        
        // Bot settings
        BotConfig.BotSettings botSettings = new BotConfig.BotSettings();
        botSettings.setWhitelistCooldownMinutes(Integer.parseInt(getEnvVar("BOT_COOLDOWN_MINUTES", "60")));
        botSettings.setMaxUsernameLength(Integer.parseInt(getEnvVar("BOT_MAX_USERNAME_LENGTH", "16")));
        botSettings.setEnableDuplicateCheck(Boolean.parseBoolean(getEnvVar("BOT_ENABLE_DUPLICATE_CHECK", "true")));
        config.setBot(botSettings);
        
        // Status pages configuration (initialize empty)
        BotConfig.StatusPagesConfig statusPagesConfig = new BotConfig.StatusPagesConfig();
        statusPagesConfig.setDefaultChannelId(getEnvVar("STATUS_PAGE_CHANNEL_ID", null));
        statusPagesConfig.setDefaultMessageId(getEnvVar("STATUS_PAGE_MESSAGE_ID", null));
        config.setStatusPages(statusPagesConfig);
        
        return config;
    }
    
    /**
     * Override configuration with environment variables if present
     */
    private void overrideWithEnvironmentVariables() {
        if (config == null) return;
        
        // Discord overrides
        String token = System.getenv("DISCORD_TOKEN");
        if (token != null && !token.isEmpty()) {
            config.getDiscord().setToken(token);
        }
        
        String guildId = System.getenv("DISCORD_GUILD_ID");
        if (guildId != null && !guildId.isEmpty()) {
            config.getDiscord().setGuildId(guildId);
        }
        
        String adminRole = System.getenv("DISCORD_ADMIN_ROLE");
        if (adminRole != null && !adminRole.isEmpty()) {
            config.getDiscord().setAdminRole(adminRole);
        }
        
        // Minecraft overrides
        String host = System.getenv("MINECRAFT_RCON_HOST");
        if (host != null && !host.isEmpty()) {
            config.getMinecraft().setHost(host);
        }
        
        String port = System.getenv("MINECRAFT_RCON_PORT");
        if (port != null && !port.isEmpty()) {
            config.getMinecraft().setPort(Integer.parseInt(port));
        }
        
        String password = System.getenv("MINECRAFT_RCON_PASSWORD");
        if (password != null && !password.isEmpty()) {
            config.getMinecraft().setPassword(password);
        }
        
        // Status pages overrides
        String statusChannelId = System.getenv("STATUS_PAGE_CHANNEL_ID");
        if (statusChannelId != null && !statusChannelId.isEmpty()) {
            config.getStatusPages().setDefaultChannelId(statusChannelId);
        }
        
        String statusMessageId = System.getenv("STATUS_PAGE_MESSAGE_ID");
        if (statusMessageId != null && !statusMessageId.isEmpty()) {
            config.getStatusPages().setDefaultMessageId(statusMessageId);
        }
    }
    
    /**
     * Validate required configuration fields
     */
    private void validateConfig() {
        if (config.getDiscord().getToken() == null || config.getDiscord().getToken().isEmpty()) {
            throw new IllegalStateException("Discord token is required");
        }
        
        if (config.getMinecraft().getPassword() == null || config.getMinecraft().getPassword().isEmpty()) {
            throw new IllegalStateException("Minecraft RCON password is required");
        }
        
        if (config.getMinecraft().getHost() == null || config.getMinecraft().getHost().isEmpty()) {
            throw new IllegalStateException("Minecraft RCON host is required");
        }
        
        logger.info("Configuration validated successfully");
    }
    
    /**
     * Get environment variable with error handling
     */
    private String getEnvVar(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Required environment variable not set: " + name);
        }
        return value;
    }
    
    /**
     * Get environment variable with default value
     */
    private String getEnvVar(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
    
    /**
     * Create a sample configuration file
     */
    public void createSampleConfig() {
        try {
            BotConfig sampleConfig = new BotConfig();
            
            BotConfig.DiscordConfig discordConfig = new BotConfig.DiscordConfig();
            discordConfig.setToken("YOUR_DISCORD_BOT_TOKEN");
            discordConfig.setGuildId("YOUR_GUILD_ID");
            discordConfig.setAllowedRoles(new String[]{"Admin", "Moderator"});
            discordConfig.setAdminRole("Admin");
            sampleConfig.setDiscord(discordConfig);
            
            BotConfig.MinecraftConfig minecraftConfig = new BotConfig.MinecraftConfig();
            minecraftConfig.setHost("minecraft-server");
            minecraftConfig.setPort(25575);
            minecraftConfig.setPassword("your_rcon_password");
            minecraftConfig.setTimeoutSeconds(10);
            sampleConfig.setMinecraft(minecraftConfig);
            
            BotConfig.BotSettings botSettings = new BotConfig.BotSettings();
            botSettings.setWhitelistCooldownMinutes(60);
            botSettings.setMaxUsernameLength(16);
            botSettings.setEnableDuplicateCheck(true);
            sampleConfig.setBot(botSettings);
            
            BotConfig.StatusPagesConfig statusPagesConfig = new BotConfig.StatusPagesConfig();
            statusPagesConfig.setDefaultChannelId("YOUR_STATUS_CHANNEL_ID");
            statusPagesConfig.setDefaultMessageId("YOUR_STATUS_MESSAGE_ID");
            sampleConfig.setStatusPages(statusPagesConfig);
            
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File("config.sample.json"), sampleConfig);
            
            logger.info("Sample configuration file created: config.sample.json");
            
        } catch (IOException e) {
            logger.error("Failed to create sample configuration", e);
        }
    }
    
    /**
     * Save configuration to file
     */
    public void saveConfig() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(CONFIG_FILE_NAME), config);
            logger.debug("Configuration saved to {}", CONFIG_FILE_NAME);
        } catch (IOException e) {
            logger.error("Failed to save configuration", e);
        }
    }
    
    public BotConfig getConfig() {
        return config;
    }
}