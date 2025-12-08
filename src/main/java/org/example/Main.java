package org.example;

import org.example.bot.MedBot;
import org.example.dao.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

/**
 * Main entry point for the OpenFDA MedBot application.
 * Initializes database, configuration, and starts the Telegram bot.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Starting OpenFDA MedBot...");

        try {
            // Load configuration
            MyConfiguration config = MyConfiguration.getInstance();
            String botToken = config.getProperty("BOT_TOKEN");

            if (botToken == null || botToken.trim().isEmpty() || botToken.contains("inserisci")) {
                logger.error("BOT_TOKEN not configured! Please set it in config.properties");
                System.err.println("ERROR: BOT_TOKEN not configured in config.properties");
                System.exit(1);
            }

            // Initialize database
            logger.info("Initializing database...");
            DatabaseManager.getInstance().initializeDatabase();
            logger.info("Database initialized successfully");

            // Start bot
            try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
                botsApplication.registerBot(botToken, new MedBot());
                logger.info("MedBot successfully started!");
                System.out.println("✓ MedBot is running. Press Ctrl+C to stop.");

                // Keep the application running
                Thread.currentThread().join();
            }

        } catch (InterruptedException e) {
            logger.info("Bot stopped by user");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Fatal error starting bot", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
}