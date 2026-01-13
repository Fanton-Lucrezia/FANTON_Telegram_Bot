package org.medBot;

import org.medBot.bot.MedBot;
import org.medBot.dao.DatabaseManager;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

//Classe principale che avvia l'applicazione MedBot
public class Main {

    public static void main(String[] args) {
        try {
            //Carica il file di configurazione per leggere il token del bot
            MyConfiguration config = MyConfiguration.getInstance();
            String botToken = config.getProperty("BOT_TOKEN");

            //Verifica che il token sia stato configurato correttamente
            //Se il token è vuoto o contiene il testo di default, esce con errore
            if (botToken == null || botToken.trim().isEmpty() || botToken.contains("inserisci")) {
                System.err.println("ERRORE: BOT_TOKEN non configurato in config.properties");
                System.exit(-1);
            }

            //Inizializza il database SQLite creando le tabelle necessarie
            DatabaseManager.getInstance().initializeDatabase();

            //Crea l'applicazione per gestire le richieste long polling di Telegram
            //Long polling permette al bot di ricevere aggiornamenti in tempo reale
            try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
                //Registra il bot con il token e lo collega all'istanza MedBot
                botsApplication.registerBot(botToken, new MedBot());
                System.out.println("MedBot avviato. Premi Ctrl+C per interrompere.");

                //Mantiene il thread principale attivo in modo che il bot continui a funzionare
                //Senza questa riga, il programma si chiuderebbe immediatamente
                Thread.currentThread().join();
            }

        } catch (InterruptedException e) {
            //Gestisce l'interruzione del thread (ad esempio quando si preme Ctrl+C)
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            //Cattura qualsiasi errore critico e termina l'applicazione
            System.err.println("Errore critico: " + e.getMessage());
            System.exit(-1);
        }
    }
}