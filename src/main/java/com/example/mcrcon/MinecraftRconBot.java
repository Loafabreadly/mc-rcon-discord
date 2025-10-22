package com.example.mcrcon;

import com.example.mcrcon.config.BotConfig;
import com.example.mcrcon.config.ConfigManager;
import com.example.mcrcon.service.MinecraftRconService;
import com.example.mcrcon.service.StatusPageManager;
import com.example.mcrcon.commands.WhitelistCommand;
import com.example.mcrcon.commands.AdminCommand;
import com.example.mcrcon.commands.UtilityCommand;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
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
    private StatusPageManager statusPageManager;
    private WhitelistCommand whitelistCommand;
    private AdminCommand adminCommand;
    private UtilityCommand utilityCommand;
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
        adminCommand = new AdminCommand(rconService, config);
        utilityCommand = new UtilityCommand(rconService);
        statusPageManager = new StatusPageManager(utilityCommand, scheduler);
        
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
                case "server-status" -> utilityCommand.handleServerStatusCommand(event);
                case "players" -> utilityCommand.handlePlayersCommand(event);
                case "help" -> utilityCommand.handleHelpCommand(event);
                case "ping" -> handlePingCommand(event);
                case "status-page" -> handleStatusPageCommands(event);
                // Admin commands
                case "admin" -> handleAdminCommands(event);
                default -> event.reply("Unknown command!").setEphemeral(true).queue();
            }
        } catch (Exception e) {
            logger.error("Error handling command: {}", commandName, e);
            event.reply("An error occurred while processing your command. Please try again later.")
                    .setEphemeral(true).queue();
        }
    }
    
    /**
     * Handle admin sub-commands
     */
    private void handleAdminCommands(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("Invalid admin command!").setEphemeral(true).queue();
            return;
        }
        
        switch (subcommand) {
            case "whitelist-info" -> adminCommand.handleWhitelistInfoCommand(event);
            case "whitelist-remove" -> adminCommand.handleWhitelistRemoveCommand(event);
            case "console" -> adminCommand.handleConsoleCommand(event);
            case "performance" -> adminCommand.handlePerformanceCommand(event);
            default -> event.reply("Unknown admin command!").setEphemeral(true).queue();
        }
    }
    
    /**
     * Handle status page sub-commands
     */
    private void handleStatusPageCommands(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("Invalid status page command!").setEphemeral(true).queue();
            return;
        }
        
        switch (subcommand) {
            case "create" -> statusPageManager.createStatusPage(event);
            case "remove" -> statusPageManager.removeStatusPage(event);
            default -> event.reply("Unknown status page command!").setEphemeral(true).queue();
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
                        Commands.slash("players", "View online players list"),
                        Commands.slash("help", "Show help information"),
                        Commands.slash("ping", "Test bot responsiveness"),
                        Commands.slash("status-page", "Manage persistent status pages")
                                .addSubcommands(
                                        new SubcommandData("create", "Create a persistent status page that updates every 5 minutes"),
                                        new SubcommandData("remove", "Remove the persistent status page from this channel")
                                ),
                        Commands.slash("admin", "Admin commands")
                                .addSubcommands(
                                        new SubcommandData("whitelist-info", "View complete whitelist"),
                                        new SubcommandData("whitelist-remove", "Remove player from whitelist")
                                                .addOption(OptionType.STRING, "username", "Username to remove", true),
                                        new SubcommandData("console", "Execute console command")
                                                .addOption(OptionType.STRING, "command", "Command to execute", true),
                                        new SubcommandData("performance", "View server performance metrics")
                                )
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
                        Commands.slash("players", "View online players list"),
                        Commands.slash("help", "Show help information"),
                        Commands.slash("ping", "Test bot responsiveness"),
                        Commands.slash("status-page", "Manage persistent status pages")
                                .addSubcommands(
                                        new SubcommandData("create", "Create a persistent status page that updates every 5 minutes"),
                                        new SubcommandData("remove", "Remove the persistent status page from this channel")
                                ),
                        Commands.slash("admin", "Admin commands")
                                .addSubcommands(
                                        new SubcommandData("whitelist-info", "View complete whitelist"),
                                        new SubcommandData("whitelist-remove", "Remove player from whitelist")
                                                .addOption(OptionType.STRING, "username", "Username to remove", true),
                                        new SubcommandData("console", "Execute console command")
                                                .addOption(OptionType.STRING, "command", "Command to execute", true),
                                        new SubcommandData("performance", "View server performance metrics")
                                )
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