package com.github.loafabreadly.mcrcon.commands;

import com.github.loafabreadly.mcrcon.config.BotConfig;
import com.github.loafabreadly.mcrcon.service.MinecraftRconService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Instant;
import java.util.Arrays;

/**
 * Handles admin-level Discord commands
 */
public class AdminCommand {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminCommand.class);
    
    private final MinecraftRconService rconService;
    private final BotConfig config;
    
    public AdminCommand(MinecraftRconService rconService, BotConfig config) {
        this.rconService = rconService;
        this.config = config;
    }
    
    /**
     * Handle whitelist info command
     */
    public void handleWhitelistInfoCommand(SlashCommandInteractionEvent event) {
        if (!hasAdminPermission(event.getMember())) {
            event.reply("❌ You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        event.deferReply().queue();
        
        rconService.getWhitelist().whenComplete((whitelist, throwable) -> {
            if (throwable != null) {
                logger.error("Error getting whitelist info", throwable);
                event.getHook().editOriginal("❌ Failed to get whitelist information.").queue();
                return;
            }
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📋 Minecraft Server Whitelist")
                    .setColor(Color.GREEN)
                    .addField("Total Players", String.valueOf(whitelist.getCount()), true)
                    .addField("Players", 
                            whitelist.getCount() > 0 ? 
                                    String.join(", ", whitelist.getPlayers()) : "None", 
                            false)
                    .setTimestamp(Instant.now())
                    .setFooter("Minecraft RCON Bot");
            
            event.getHook().editOriginalEmbeds(embed.build()).queue();
        });
    }
    
    /**
     * Handle whitelist remove command
     */
    public void handleWhitelistRemoveCommand(SlashCommandInteractionEvent event) {
        if (!hasAdminPermission(event.getMember())) {
            event.reply("❌ You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        String username = event.getOption("username").getAsString().trim();
        event.deferReply().queue();
        
        rconService.removePlayerFromWhitelist(username).whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.error("Error removing player from whitelist", throwable);
                event.getHook().editOriginal("❌ Failed to remove player from whitelist.").queue();
                return;
            }
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTimestamp(Instant.now())
                    .setFooter("Minecraft RCON Bot");
            
            if (result.isSuccess()) {
                embed.setTitle("✅ Player Removed")
                        .setDescription(String.format("Player **%s** has been removed from the whitelist.", username))
                        .setColor(Color.GREEN);
                logger.info("Admin removed player {} from whitelist (requested by user {})", 
                           username, event.getUser().getId());
            } else {
                embed.setTitle("❌ Removal Failed")
                        .setDescription(result.getMessage())
                        .setColor(Color.RED);
            }
            
            event.getHook().editOriginalEmbeds(embed.build()).queue();
        });
    }
    
    /**
     * Handle server console command
     */
    public void handleConsoleCommand(SlashCommandInteractionEvent event) {
        if (!hasAdminPermission(event.getMember())) {
            event.reply("❌ You don't have permission to use this command.").setEphemeral(true).queue();
            return;
        }
        
        String command = event.getOption("command").getAsString().trim();
        
        // Sanitize dangerous commands
        if (isDangerousCommand(command)) {
            event.reply("❌ That command is not allowed for security reasons.").setEphemeral(true).queue();
            return;
        }
        
        event.deferReply().queue();
        
        rconService.executeCommand(command).whenComplete((response, throwable) -> {
            if (throwable != null) {
                logger.error("Error executing console command: {}", command, throwable);
                event.getHook().editOriginal("❌ Failed to execute command.").queue();
                return;
            }
            
            String truncatedResponse = response.length() > 1900 ? 
                    response.substring(0, 1900) + "..." : response;
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🖥️ Console Command")
                    .addField("Command", "```" + command + "```", false)
                    .addField("Response", "```" + truncatedResponse + "```", false)
                    .setColor(Color.BLUE)
                    .setTimestamp(Instant.now())
                    .setFooter("Minecraft RCON Bot");
            
            event.getHook().editOriginalEmbeds(embed.build()).queue();
            logger.info("Admin executed console command: {} (requested by user {})", 
                       command, event.getUser().getId());
        });
    }

    /**
     * Check if user has admin permissions
     */
    private boolean hasAdminPermission(Member member) {
        if (member == null) return false;
        
        // Check for specific admin role first
        String adminRole = config.getDiscord().getAdminRole();
        if (adminRole != null && !adminRole.isEmpty()) {
            return member.getRoles().stream()
                    .map(Role::getName)
                    .anyMatch(roleName -> roleName.equalsIgnoreCase(adminRole));
        }
        
        // Fallback to allowed roles if no admin role is configured
        String[] allowedRoles = config.getDiscord().getAllowedRoles();
        if (allowedRoles == null || allowedRoles.length == 0) {
            return true; // If no roles configured, allow everyone
        }
        
        // Check if user has any of the allowed roles
        return member.getRoles().stream()
                .map(Role::getName)
                .anyMatch(roleName -> Arrays.stream(allowedRoles)
                        .anyMatch(allowedRole -> allowedRole.equalsIgnoreCase(roleName)));
    }
    
    /**
     * Check if command is dangerous and should be blocked
     */
    private boolean isDangerousCommand(String command) {
        String lowerCommand = command.toLowerCase().trim();
        
        // Block dangerous commands
        String[] dangerousCommands = {
            "stop", "restart", "shutdown", "kill", "ban", "ban-ip", 
            "pardon", "pardon-ip", "op", "deop", "kick", "whitelist off",
            "whitelist remove", "whitelist add"  // Let these go through dedicated commands instead
        };
        
        for (String dangerous : dangerousCommands) {
            if (lowerCommand.startsWith(dangerous)) {
                return true;
            }
        }
        
        return false;
    }
}