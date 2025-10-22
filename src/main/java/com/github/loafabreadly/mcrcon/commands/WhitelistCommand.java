package com.github.loafabreadly.mcrcon.commands;

import com.github.loafabreadly.mcrcon.config.BotConfig;
import com.github.loafabreadly.mcrcon.service.MinecraftRconService;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Handles whitelist-related Discord commands
 */
public class WhitelistCommand {
    
    private static final Logger logger = LoggerFactory.getLogger(WhitelistCommand.class);
    
    // Minecraft username validation pattern
    private static final Pattern MINECRAFT_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");
    
    private final MinecraftRconService rconService;
    private final BotConfig config;
    private final Map<String, Instant> userCooldowns = new ConcurrentHashMap<>();
    
    public WhitelistCommand(MinecraftRconService rconService, BotConfig config) {
        this.rconService = rconService;
        this.config = config;
    }
    
    /**
     * Handle the whitelist slash command
     */
    public void handleWhitelistCommand(SlashCommandInteractionEvent event) {
        String username = event.getOption("username").getAsString().trim();
        Member member = event.getMember();
        String userId = event.getUser().getId();
        
        // Defer reply for longer processing time
        event.deferReply(true).queue();
        
        try {
            // Validate user permissions
            if (!hasPermission(member)) {
                event.getHook().editOriginal("❌ You don't have permission to use this command.").queue();
                return;
            }
            
            // Check cooldown
            if (isOnCooldown(userId)) {
                long remainingMinutes = getCooldownRemainingMinutes(userId);
                event.getHook().editOriginal(
                    String.format("⏱️ You must wait %d more minutes before requesting another whitelist.", 
                                remainingMinutes)
                ).queue();
                return;
            }
            
            // Validate username
            if (!isValidMinecraftUsername(username)) {
                event.getHook().editOriginal(
                    "❌ Invalid Minecraft username. Usernames must be 1-16 characters and contain only letters, numbers, and underscores."
                ).queue();
                return;
            }
            
            // Process whitelist request
            processWhitelistRequest(event, username, userId);
            
        } catch (Exception e) {
            logger.error("Error processing whitelist command", e);
            event.getHook().editOriginal("❌ An error occurred while processing your request.").queue();
        }
    }
    
    /**
     * Process the whitelist request
     */
    private void processWhitelistRequest(SlashCommandInteractionEvent event, String username, String userId) {
        event.getHook().editOriginal("🔄 Processing your whitelist request...").queue();
        
        // First check if user is already whitelisted (if duplicate check is enabled)
        if (config.getBot().isEnableDuplicateCheck()) {
            rconService.isPlayerWhitelisted(username).whenComplete((isWhitelisted, throwable) -> {
                if (throwable != null) {
                    logger.error("Error checking whitelist status for {}", username, throwable);
                    event.getHook().editOriginal(
                        "❌ Failed to check current whitelist status. Please try again later."
                    ).queue();
                    return;
                }
                
                if (isWhitelisted) {
                    event.getHook().editOriginal(
                        String.format("ℹ️ Player **%s** is already whitelisted!", username)
                    ).queue();
                    return;
                }
                
                // User is not whitelisted, proceed to add them
                addPlayerToWhitelist(event, username, userId);
            });
        } else {
            // Skip duplicate check and add directly
            addPlayerToWhitelist(event, username, userId);
        }
    }
    
    /**
     * Add player to whitelist
     */
    private void addPlayerToWhitelist(SlashCommandInteractionEvent event, String username, String userId) {
        rconService.addPlayerToWhitelist(username).whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.error("Error adding player {} to whitelist", username, throwable);
                event.getHook().editOriginal(
                    "❌ Failed to add you to the whitelist. The server may be unreachable."
                ).queue();
                return;
            }
            
            if (result.isSuccess()) {
                // Success - add cooldown and send success message
                setCooldown(userId);
                
                String successMessage = String.format(
                    "✅ **Success!** Player **%s** has been added to the whitelist!\n\n" +
                    "🎮 You can now join the Minecraft server.\n" +
                    "⏱️ Next whitelist request available in %d minutes.",
                    username,
                    config.getBot().getWhitelistCooldownMinutes()
                );
                
                event.getHook().editOriginal(successMessage).queue();
                
                logger.info("Successfully added player {} to whitelist (requested by user {})", username, userId);
                
            } else {
                // Failed - send error message
                String errorMessage;
                if (result.getMessage().contains("already whitelisted")) {
                    errorMessage = String.format("ℹ️ Player **%s** is already whitelisted!", username);
                } else if (result.getMessage().contains("does not exist")) {
                    errorMessage = String.format(
                        "❌ Player **%s** does not exist. Please check your username spelling.\n\n" +
                        "💡 **Tip:** Minecraft usernames are case-sensitive and must be valid Java Edition usernames.",
                        username
                    );
                } else {
                    errorMessage = String.format("❌ Failed to add **%s** to whitelist: %s", 
                                                username, result.getMessage());
                }
                
                event.getHook().editOriginal(errorMessage).queue();
                logger.warn("Failed to add player {} to whitelist: {}", username, result.getMessage());
            }
        });
    }
    
    /**
     * Check if user has permission to use whitelist commands
     */
    private boolean hasPermission(Member member) {
        if (member == null) return false;
        
        // If no roles are configured, allow everyone
        String[] allowedRoles = config.getDiscord().getAllowedRoles();
        if (allowedRoles == null || allowedRoles.length == 0) {
            return true;
        }
        
        // Check if user has any of the allowed roles
        return member.getRoles().stream()
                .map(Role::getName)
                .anyMatch(roleName -> Arrays.stream(allowedRoles)
                        .anyMatch(allowedRole -> allowedRole.equalsIgnoreCase(roleName)));
    }
    
    /**
     * Check if user is on cooldown
     */
    private boolean isOnCooldown(String userId) {
        Instant lastRequest = userCooldowns.get(userId);
        if (lastRequest == null) return false;
        
        Instant cooldownEnd = lastRequest.plus(config.getBot().getWhitelistCooldownMinutes(), ChronoUnit.MINUTES);
        return Instant.now().isBefore(cooldownEnd);
    }
    
    /**
     * Get remaining cooldown time in minutes
     */
    private long getCooldownRemainingMinutes(String userId) {
        Instant lastRequest = userCooldowns.get(userId);
        if (lastRequest == null) return 0;
        
        Instant cooldownEnd = lastRequest.plus(config.getBot().getWhitelistCooldownMinutes(), ChronoUnit.MINUTES);
        return ChronoUnit.MINUTES.between(Instant.now(), cooldownEnd);
    }
    
    /**
     * Set cooldown for user
     */
    private void setCooldown(String userId) {
        userCooldowns.put(userId, Instant.now());
        
        // Clean up old cooldowns periodically (keep only last hour of data)
        if (userCooldowns.size() > 1000) {
            Instant hourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
            userCooldowns.entrySet().removeIf(entry -> entry.getValue().isBefore(hourAgo));
        }
    }
    
    /**
     * Validate Minecraft username format
     */
    private boolean isValidMinecraftUsername(String username) {
        if (username == null || username.isEmpty()) return false;
        if (username.length() > config.getBot().getMaxUsernameLength()) return false;
        
        return MINECRAFT_USERNAME_PATTERN.matcher(username).matches();
    }
    
    /**
     * Get current whitelist info (admin command)
     */
    public void handleWhitelistInfoCommand(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        
        if (!hasPermission(member)) {
            event.reply("❌ You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        event.deferReply(true).queue();
        
        rconService.getWhitelist().whenComplete((whitelist, throwable) -> {
            if (throwable != null) {
                event.getHook().editOriginal("❌ Failed to get whitelist information.").queue();
                return;
            }
            
            String whitelistInfo = String.format(
                "📋 **Minecraft Server Whitelist**\n\n" +
                "👥 **Total Players:** %d\n" +
                "📝 **Players:** %s",
                whitelist.getCount(),
                whitelist.getCount() > 0 ? String.join(", ", whitelist.getPlayers()) : "None"
            );
            
            event.getHook().editOriginal(whitelistInfo).queue();
        });
    }
}