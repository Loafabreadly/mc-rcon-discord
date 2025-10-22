package com.example.mcrcon;

import com.example.mcrcon.config.BotConfig;
import com.example.mcrcon.config.ConfigManager;
import com.example.mcrcon.service.MinecraftRconService;
import com.example.mcrcon.commands.WhitelistCommand;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main class for the Minecraft RCON Discord Bot
 */
public class MinecraftRconBot extends ListenerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(MinecraftRconBot.class);
    
    @Getter
    private JDA jda;
    @Getter
    private BotConfig config;
    @Getter
    private MinecraftRconService rconService;
    private WhitelistCommand whitelistCommand;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    public static void main(String[] args) {
        logger.info("Starting Minecraft RCON Discord Bot...");
        
        try {
            MinecraftRconBot bot = new MinecraftRconBot();
            bot.start();
        } catch (Exception e) {
            logger.error("Failed to start bot", e);
            System.exit(1);
        }
    }
    
    /**
     * Start the bot
     */
    public void start() throws Exception {
        // Load configuration
        ConfigManager configManager = new ConfigManager();
        config = configManager.loadConfig();
        
        // Create sample config if needed
        configManager.createSampleConfig();
        
        // Initialize services
        rconService = new MinecraftRconService(config.getMinecraft());
        whitelistCommand = new WhitelistCommand(rconService, config);
        
        // Test RCON connection
        testRconConnection();
        
        // Build and start JDA
        jda = JDABuilder.createDefault(config.getDiscord().getToken())
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .setActivity(Activity.watching("Minecraft Server"))
                .addEventListeners(this)
                .build();
        
        // Wait for JDA to be ready
        jda.awaitReady();
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        
        logger.info("Bot started successfully!");
        
        // Start periodic tasks
        startPeriodicTasks();
    }
    
    @Override
    public void onReady(ReadyEvent event) {
        logger.info("Bot is ready! Logged in as: {}", event.getJDA().getSelfUser().getName());
        
        // Register slash commands
        registerSlashCommands();
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        
        try {
            switch (commandName) {
                case "whitelist" -> whitelistCommand.handleWhitelistCommand(event);
                case "server-status" -> handleServerStatusCommand(event);
                case "ping" -> handlePingCommand(event);
                default -> event.reply("Unknown command!").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            logger.error("Error handling command: {}", commandName, e);
            event.reply("An error occurred while processing your command. Please try again later.")
                    .setEphemeral(true).queue();
        }
    }
    
    /**
     * Register slash commands with Discord
     */
    private void registerSlashCommands() {
        try {
            // Get guild for command registration
            Guild guild = null;
            if (config.getDiscord().getGuildId() != null) {
                guild = jda.getGuildById(config.getDiscord().getGuildId());
            }
            
            if (guild != null) {
                // Register guild-specific commands (faster updates)
                guild.updateCommands().addCommands(
                        Commands.slash("whitelist", "Request to be added to the Minecraft server whitelist")
                                .addOption(OptionType.STRING, "username", "Your Minecraft username", true),
                        Commands.slash("server-status", "Check the Minecraft server status"),
                        Commands.slash("ping", "Test bot responsiveness")
                ).queue(
                        commands -> logger.info("Successfully registered {} guild commands", commands.size()),
                        error -> logger.error("Failed to register guild commands", error)
                );
            } else {
                // Register global commands (slower updates)
                jda.updateCommands().addCommands(
                        Commands.slash("whitelist", "Request to be added to the Minecraft server whitelist")
                                .addOption(OptionType.STRING, "username", "Your Minecraft username", true),
                        Commands.slash("server-status", "Check the Minecraft server status"),
                        Commands.slash("ping", "Test bot responsiveness")
                ).queue(
                        commands -> logger.info("Successfully registered {} global commands", commands.size()),
                        error -> logger.error("Failed to register global commands", error)
                );
            }
            
        } catch (Exception e) {
            logger.error("Failed to register slash commands", e);
        }
    }
    
    /**
     * Handle server status command
     */
    private void handleServerStatusCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        
        rconService.getServerInfo().whenComplete((serverInfo, throwable) -> {
            if (throwable != null) {
                event.getHook().editOriginal("❌ Failed to get server status: " + throwable.getMessage()).queue();
            } else {
                String status = String.format(
                        "🟢 **Minecraft Server Status**\n" +
                        "📊 Players Online: %d/%d\n" +
                        "⚡ TPS: %.1f\n" +
                        "👥 Online Players: %s",
                        serverInfo.getOnlinePlayers(),
                        serverInfo.getMaxPlayers(),
                        serverInfo.getTps(),
                        serverInfo.getPlayerList().length > 0 ? 
                                String.join(", ", serverInfo.getPlayerList()) : "None"
                );
                
                event.getHook().editOriginal(status).queue();
            }
        });
    }
    
    /**
     * Handle ping command
     */
    private void handlePingCommand(SlashCommandInteractionEvent event) {
        long time = System.currentTimeMillis();
        event.reply("Pong!").setEphemeral(true).queue(response -> 
            response.editOriginal(String.format("🏓 Pong! Latency: %dms", 
                System.currentTimeMillis() - time)).queue()
        );
    }
    
    /**
     * Test RCON connection on startup
     */
    private void testRconConnection() {
        logger.info("Testing RCON connection...");
        
        rconService.testConnection().whenComplete((success, throwable) -> {
            if (success && throwable == null) {
                logger.info("✅ RCON connection test successful");
            } else {
                logger.error("❌ RCON connection test failed", throwable);
                logger.warn("Bot will continue to run, but RCON commands may not work");
            }
        });
    }
    
    /**
     * Start periodic maintenance tasks
     */
    private void startPeriodicTasks() {
        // Test RCON connection every 5 minutes
        scheduler.scheduleAtFixedRate(() -> {
            rconService.testConnection().whenComplete((success, throwable) -> {
                if (!success || throwable != null) {
                    logger.warn("Periodic RCON connection check failed", throwable);
                }
            });
        }, 5, 5, TimeUnit.MINUTES);
        
        // Update bot activity every hour
        scheduler.scheduleAtFixedRate(() -> {
            rconService.getServerInfo().whenComplete((serverInfo, throwable) -> {
                if (throwable == null) {
                    String activity = String.format("MC Server (%d/%d)", 
                            serverInfo.getOnlinePlayers(), serverInfo.getMaxPlayers());
                    jda.getPresence().setActivity(Activity.watching(activity));
                }
            });
        }, 1, 1, TimeUnit.HOURS);
    }
    
    /**
     * Shutdown the bot gracefully
     */
    public void shutdown() {
        logger.info("Shutting down bot...");
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        if (jda != null) {
            jda.shutdown();
            try {
                if (!jda.awaitShutdown(10, TimeUnit.SECONDS)) {
                    jda.shutdownNow();
                }
            } catch (InterruptedException e) {
                jda.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("Bot shut down complete");
    }
}