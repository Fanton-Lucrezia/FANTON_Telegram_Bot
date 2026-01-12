package org.medBot;

import org.medBot.bot.MedBot;
import org.medBot.dao.DatabaseManager;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

public class Main {

    public static void main(String[] args) {
        try {
            //Carica la configurazione
            MyConfiguration config = MyConfiguration.getInstance();
            String botToken = config.getProperty("BOT_TOKEN");

            if (botToken == null || botToken.trim().isEmpty() || botToken.contains("inserisci")) {
                System.err.println("ERRORE: BOT_TOKEN non configurato in config.properties");
                System.exit(-1);
            }

            //Inizializza il database
            DatabaseManager.getInstance().initializeDatabase();

            //Avvia il bot
            try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
                botsApplication.registerBot(botToken, new MedBot());
                System.out.println("\u2713 MedBot avviato. Premi Ctrl+C per interrompere.");

                //Mantiene l'applicazione in esecuzione
                Thread.currentThread().join();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Errore critico: " + e.getMessage());
            System.exit(-1);
        }
    }
}