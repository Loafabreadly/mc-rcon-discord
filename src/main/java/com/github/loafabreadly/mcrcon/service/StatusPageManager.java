package com.github.loafabreadly.mcrcon.service;

import com.github.loafabreadly.mcrcon.commands.UtilityCommand;
import com.github.loafabreadly.mcrcon.config.BotConfig;
import com.github.loafabreadly.mcrcon.config.ConfigManager;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages persistent status page messages that update automatically
 */
public class StatusPageManager {
    
    private static final Logger logger = LoggerFactory.getLogger(StatusPageManager.class);
    
    private final UtilityCommand utilityCommand;
    private final ScheduledExecutorService scheduler;
    private final ConfigManager configManager;
    private final JDA jda;
    
    // Store active status pages: channelId -> StatusPage
    private final ConcurrentHashMap<String, StatusPage> activeStatusPages = new ConcurrentHashMap<>();
    
    public StatusPageManager(UtilityCommand utilityCommand, ScheduledExecutorService scheduler, ConfigManager configManager, JDA jda) {
        this.utilityCommand = utilityCommand;
        this.scheduler = scheduler;
        this.configManager = configManager;
        this.jda = jda;
        
        // Load existing status pages from config
        loadStatusPagesFromConfig();
        
        // Start the periodic update task
        startPeriodicUpdates();
    }
    
    /**
     * Create or update a status page in the given channel
     */
    public void createStatusPage(SlashCommandInteractionEvent event) {
        String channelId = event.getChannel().getId();
        
        // Check if there's already a status page in this channel
        StatusPage existingPage = activeStatusPages.get(channelId);
        if (existingPage != null) {
            // Update the existing status page instead of creating a new one
            event.reply("✅ Status page already exists in this channel and will be updated!").setEphemeral(true).queue();
            updateStatusPage(existingPage);
            return;
        }
        
        // Create a new status page
        event.deferReply().queue();
        
        utilityCommand.createStatusEmbed().whenComplete((embed, throwable) -> {
            if (throwable != null) {
                logger.error("Failed to create initial status embed", throwable);
                event.getHook().editOriginal("❌ Failed to create status page.").queue();
                return;
            }
            
            event.getHook().editOriginalEmbeds(embed).queue(message -> {
                // Store the status page information
                StatusPage statusPage = new StatusPage(
                    channelId,
                    message.getId(),
                    (TextChannel) event.getChannel(),
                    System.currentTimeMillis()
                );
                
                activeStatusPages.put(channelId, statusPage);
                saveStatusPagesToConfig();
                logger.info("Created status page in channel {} with message ID {}", channelId, message.getId());
            }, error -> {
                logger.error("Failed to send status page message", error);
                event.getHook().editOriginal("❌ Failed to create status page.").queue();
            });
        });
    }
    
    /**
     * Remove a status page from the given channel
     */
    public void removeStatusPage(SlashCommandInteractionEvent event) {
        String channelId = event.getChannel().getId();
        StatusPage removed = activeStatusPages.remove(channelId);
        
        if (removed != null) {
            saveStatusPagesToConfig();
            event.reply("✅ Status page removed from this channel.").setEphemeral(true).queue();
            logger.info("Removed status page from channel {}", channelId);
        } else {
            event.reply("❌ No status page found in this channel.").setEphemeral(true).queue();
        }
    }
    
    /**
     * Start the periodic update task
     */
    private void startPeriodicUpdates() {
        int updateInterval = configManager.getConfig().getBot().getStatusPageUpdateIntervalMin();
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                updateAllStatusPages();
            } catch (Exception e) {
                logger.error("Error during periodic status page updates", e);
            }
        }, updateInterval, updateInterval, TimeUnit.MINUTES);
        
        logger.info("Started periodic status page updates (every {} minutes)", updateInterval);
    }
    
    /**
     * Update all active status pages
     */
    private void updateAllStatusPages() {
        if (activeStatusPages.isEmpty()) {
            return;
        }
        
        logger.debug("Updating {} active status pages", activeStatusPages.size());
        
        activeStatusPages.values().forEach(this::updateStatusPage);
    }
    
    /**
     * Update a single status page
     */
    private void updateStatusPage(StatusPage statusPage) {
        utilityCommand.createStatusEmbed().whenComplete((embed, throwable) -> {
            if (throwable != null) {
                logger.error("Failed to create status embed for update", throwable);
                return;
            }
            
            // Try to update the message
            statusPage.getChannel().retrieveMessageById(statusPage.getMessageId()).queue(
                message -> {
                    message.editMessageEmbeds(embed).queue(
                        success -> {
                            statusPage.setLastUpdated(System.currentTimeMillis());
                            logger.debug("Updated status page in channel {}", statusPage.getChannelId());
                        },
                        error -> {
                            logger.warn("Failed to update status page message in channel {}: {}", 
                                       statusPage.getChannelId(), error.getMessage());
                            
                            // If the message no longer exists, remove this status page
                            if (error.getMessage().contains("Unknown Message")) {
                                activeStatusPages.remove(statusPage.getChannelId());
                                saveStatusPagesToConfig();
                                logger.info("Removed stale status page from channel {}", statusPage.getChannelId());
                            }
                        }
                    );
                },
                error -> {
                    logger.warn("Failed to retrieve status page message in channel {}: {}", 
                               statusPage.getChannelId(), error.getMessage());
                    
                    // Remove the status page if we can't retrieve the message
                    activeStatusPages.remove(statusPage.getChannelId());
                    saveStatusPagesToConfig();
                    logger.info("Removed unreachable status page from channel {}", statusPage.getChannelId());
                }
            );
        });
    }
    
    /**
     * Check if a channel has an active status page
     */
    public boolean hasStatusPage(String channelId) {
        return activeStatusPages.containsKey(channelId);
    }
    
    /**
     * Load status pages from config on startup
     */
    private void loadStatusPagesFromConfig() {
        BotConfig.StatusPagesConfig statusPagesConfig = configManager.getConfig().getStatusPages();
        if (statusPagesConfig == null) {
            logger.info("No status pages to restore from config");
            return;
        }
        
        // First, try to restore from environment variables if configured
        String defaultChannelId = statusPagesConfig.getDefaultChannelId();
        String defaultMessageId = statusPagesConfig.getDefaultMessageId();
        
        if (defaultChannelId != null && !defaultChannelId.isEmpty() && 
            defaultMessageId != null && !defaultMessageId.isEmpty()) {
            
            logger.info("Attempting to restore status page from environment variables: channel={}, message={}", 
                       defaultChannelId, defaultMessageId);
            
            try {
                TextChannel channel = jda.getTextChannelById(defaultChannelId);
                if (channel != null) {
                    // Verify the message still exists
                    channel.retrieveMessageById(defaultMessageId).queue(
                        message -> {
                            StatusPage statusPage = new StatusPage(
                                defaultChannelId,
                                defaultMessageId,
                                channel,
                                System.currentTimeMillis()
                            );
                            activeStatusPages.put(defaultChannelId, statusPage);
                            logger.info("Successfully restored environment-configured status page in channel {} with message ID {}", 
                                       defaultChannelId, defaultMessageId);
                        },
                        error -> {
                            logger.warn("Failed to restore environment-configured status page: message may have been deleted. " +
                                       "Channel: {}, Message: {}", defaultChannelId, defaultMessageId);
                        }
                    );
                } else {
                    logger.warn("Failed to restore environment-configured status page: channel {} not found", defaultChannelId);
                }
            } catch (Exception e) {
                logger.error("Error restoring environment-configured status page", e);
            }
        }
        
        // Then restore from saved config pages
        if (statusPagesConfig.getPages() == null) {
            logger.info("No saved status pages to restore from config");
            return;
        }
        
        int restored = 0;
        for (BotConfig.StatusPagesConfig.StatusPage configPage : statusPagesConfig.getPages()) {
            try {
                // Skip if this channel already has a status page from environment variables
                if (activeStatusPages.containsKey(configPage.getChannelId())) {
                    logger.debug("Skipping config page restoration for channel {} - already has environment-configured page", 
                               configPage.getChannelId());
                    continue;
                }
                
                TextChannel channel = jda.getTextChannelById(configPage.getChannelId());
                if (channel != null) {
                    // Verify the message still exists
                    channel.retrieveMessageById(configPage.getMessageId()).queue(
                        message -> {
                            StatusPage statusPage = new StatusPage(
                                configPage.getChannelId(),
                                configPage.getMessageId(),
                                channel,
                                configPage.getLastUpdated()
                            );
                            activeStatusPages.put(configPage.getChannelId(), statusPage);
                            logger.info("Restored saved status page in channel {} with message ID {}", 
                                       configPage.getChannelId(), configPage.getMessageId());
                        },
                        error -> {
                            logger.warn("Failed to restore saved status page in channel {}: message may have been deleted", 
                                       configPage.getChannelId());
                        }
                    );
                    restored++;
                } else {
                    logger.warn("Failed to restore saved status page: channel {} not found", configPage.getChannelId());
                }
            } catch (Exception e) {
                logger.error("Error restoring saved status page for channel {}", configPage.getChannelId(), e);
            }
        }
        
        logger.info("Attempted to restore {} saved status pages from config", restored);
    }
    
    /**
     * Save current status pages to config
     */
    private void saveStatusPagesToConfig() {
        try {
            List<BotConfig.StatusPagesConfig.StatusPage> configPages = new ArrayList<>();
            
            for (StatusPage statusPage : activeStatusPages.values()) {
                BotConfig.StatusPagesConfig.StatusPage configPage = new BotConfig.StatusPagesConfig.StatusPage(
                    statusPage.getChannelId(),
                    statusPage.getMessageId(),
                    statusPage.getChannel().getGuild().getId(),
                    statusPage.getLastUpdated()
                );
                configPages.add(configPage);
            }
            
            BotConfig.StatusPagesConfig statusPagesConfig = new BotConfig.StatusPagesConfig();
            statusPagesConfig.setPages(configPages.toArray(new BotConfig.StatusPagesConfig.StatusPage[0]));
            configManager.getConfig().setStatusPages(statusPagesConfig);
            configManager.saveConfig();
            
            logger.debug("Saved {} status pages to config", configPages.size());
        } catch (Exception e) {
            logger.error("Failed to save status pages to config", e);
        }
    }
    
    /**
     * Get the number of active status pages
     */
    public int getActiveStatusPageCount() {
        return activeStatusPages.size();
    }
    
    /**
     * Data class for storing status page information
     */
    @AllArgsConstructor
    @Getter
    private static class StatusPage {
        private final String channelId;
        private final String messageId;
        private final TextChannel channel;
        @Setter
        private long lastUpdated;
    }
}