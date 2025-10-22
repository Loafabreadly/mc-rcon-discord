package com.example.mcrcon.service;

import com.example.mcrcon.commands.UtilityCommand;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    // Store active status pages: channelId -> StatusPage
    private final ConcurrentHashMap<String, StatusPage> activeStatusPages = new ConcurrentHashMap<>();
    
    public StatusPageManager(UtilityCommand utilityCommand, ScheduledExecutorService scheduler) {
        this.utilityCommand = utilityCommand;
        this.scheduler = scheduler;
        
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
        scheduler.scheduleAtFixedRate(() -> {
            try {
                updateAllStatusPages();
            } catch (Exception e) {
                logger.error("Error during periodic status page updates", e);
            }
        }, 5, 5, TimeUnit.MINUTES);
        
        logger.info("Started periodic status page updates (every 5 minutes)");
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
                    logger.info("Removed unreachable status page from channel {}", statusPage.getChannelId());
                }
            );
        });
    }
    
    /**
     * Get the number of active status pages
     */
    public int getActiveStatusPageCount() {
        return activeStatusPages.size();
    }
    
    /**
     * Check if a channel has an active status page
     */
    public boolean hasStatusPage(String channelId) {
        return activeStatusPages.containsKey(channelId);
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