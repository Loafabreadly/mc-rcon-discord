package com.example.mcrcon.commands;

import com.example.mcrcon.service.MinecraftRconService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Handles utility Discord commands available to all users
 */
public class UtilityCommand {
    
    private static final Logger logger = LoggerFactory.getLogger(UtilityCommand.class);
    
    private final MinecraftRconService rconService;
    
    public UtilityCommand(MinecraftRconService rconService) {
        this.rconService = rconService;
    }
    
    /**
     * Create a status embed for use in status pages or one-time status commands
     * @return CompletableFuture containing the status embed
     */
    public CompletableFuture<MessageEmbed> createStatusEmbed() {
        return rconService.getServerInfo().handle((serverInfo, throwable) -> {
            if (throwable != null) {
                logger.error("Error getting server status for embed", throwable);
                
                return new EmbedBuilder()
                        .setTitle("❌ Server Status")
                        .setDescription("Failed to connect to the Minecraft server. The server may be offline.")
                        .setColor(Color.RED)
                        .setTimestamp(Instant.now())
                        .setFooter("Minecraft RCON Bot")
                        .build();
            }
            
            // Determine server status color
            Color statusColor;
            String statusEmoji;
            if (serverInfo.getTps() >= 19.0) {
                statusColor = Color.GREEN;
                statusEmoji = "🟢";
            } else if (serverInfo.getTps() >= 15.0) {
                statusColor = Color.YELLOW;
                statusEmoji = "🟡";
            } else {
                statusColor = Color.RED;
                statusEmoji = "🔴";
            }
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(statusEmoji + " Minecraft Server Status")
                    .setColor(statusColor)
                    .addField("📊 Players Online", 
                            String.format("%d/%d", serverInfo.getOnlinePlayers(), serverInfo.getMaxPlayers()), 
                            true)
                    .addField("⚡ Server TPS", String.format("%.1f", serverInfo.getTps()), true)
                    .addField("🎮 Server Load", getLoadDescription(serverInfo.getTps()), true)
                    .setTimestamp(Instant.now())
                    .setFooter("Minecraft RCON Bot | Auto-updates every 5 minutes");
            
            // Add online players list if any
            if (serverInfo.getPlayerList().length > 0) {
                String playerList = String.join(", ", serverInfo.getPlayerList());
                if (playerList.length() > 1000) {
                    playerList = playerList.substring(0, 997) + "...";
                }
                embed.addField("👥 Online Players", playerList, false);
            }
            
            return embed.build();
        });
    }
    
    /**
     * Handle enhanced server status command
     */
    public void handleServerStatusCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        
        createStatusEmbed().whenComplete((embed, throwable) -> {
            if (throwable != null) {
                logger.error("Error creating status embed", throwable);
                
                EmbedBuilder errorEmbed = new EmbedBuilder()
                        .setTitle("❌ Server Status")
                        .setDescription("Failed to get server status.")
                        .setColor(Color.RED)
                        .setTimestamp(Instant.now())
                        .setFooter("Minecraft RCON Bot");
                
                event.getHook().editOriginalEmbeds(errorEmbed.build()).queue();
                return;
            }
            
            event.getHook().editOriginalEmbeds(embed).queue();
        });
    }
    
    /**
     * Handle players list command
     */
    public void handlePlayersCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        
        rconService.getServerInfo().whenComplete((serverInfo, throwable) -> {
            if (throwable != null) {
                logger.error("Error getting players list", throwable);
                event.getHook().editOriginal("❌ Failed to get players list. Server may be offline.").queue();
                return;
            }
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("👥 Online Players")
                    .setColor(Color.BLUE)
                    .addField("Player Count", 
                            String.format("%d/%d", serverInfo.getOnlinePlayers(), serverInfo.getMaxPlayers()), 
                            true)
                    .setTimestamp(Instant.now())
                    .setFooter("Minecraft RCON Bot");
            
            if (serverInfo.getPlayerList().length > 0) {
                String playerList = String.join("\n", serverInfo.getPlayerList());
                if (playerList.length() > 1000) {
                    playerList = playerList.substring(0, 997) + "...";
                }
                embed.addField("Players", playerList, false);
            } else {
                embed.addField("Players", "No players online", false);
            }
            
            event.getHook().editOriginalEmbeds(embed.build()).queue();
        });
    }
    
    /**
     * Handle help command
     */
    public void handleHelpCommand(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤖 Minecraft RCON Bot - Help")
                .setDescription("Available commands for managing your Minecraft server via Discord")
                .setColor(Color.CYAN)
                .addField("**📝 Whitelist Commands**", 
                        "`/whitelist <username>` - Request to add yourself to the server whitelist", false)
                .addField("**📊 Server Commands**", 
                        "`/server-status` - Check server status and performance\n" +
                        "`/players` - View online players list\n" +
                        "`/ping` - Test bot responsiveness", false)
                .addField("**📺 Status Pages**", 
                        "`/status-page create` - Create a persistent status page that updates every 5 minutes\n" +
                        "`/status-page remove` - Remove the status page from this channel", false)
                .addField("**ℹ️ Information**", 
                        "`/help` - Show this help message", false)
                .addField("**🔧 Admin Commands**", 
                        "`/admin whitelist-info` - View complete whitelist\n" +
                        "`/admin whitelist-remove <username>` - Remove player from whitelist\n" +
                        "`/admin console <command>` - Execute server console command", false)
                .addField("**🛡️ Permissions**", 
                        "Admin commands require special Discord roles as configured by the server administrators.", false)
                .setTimestamp(Instant.now())
                .setFooter("Minecraft RCON Bot | Open Source");
        
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
    
    /**
     * Get a user-friendly description of server load based on TPS
     */
    private String getLoadDescription(double tps) {
        if (tps >= 19.5) {
            return "Excellent";
        } else if (tps >= 18.0) {
            return "Good";
        } else if (tps >= 15.0) {
            return "Moderate";
        } else if (tps >= 10.0) {
            return "Heavy";
        } else {
            return "Critical";
        }
    }
}