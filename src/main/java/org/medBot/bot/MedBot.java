package org.medBot.bot;

import org.medBot.MyConfiguration;
import org.medBot.dao.DatabaseManager;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Bot Telegram per informazioni su farmaci tramite API OpenFDA.
 * Classe principale che gestisce gli update e delega i comandi al CommandHandler.
 */
public class MedBot implements LongPollingSingleThreadUpdateConsumer {
    private final TelegramClient telegramClient;
    private final MessageSender messageSender;
    private final CommandHandler commandHandler;
    private final DatabaseManager dbManager;

    public MedBot() {
        String botToken = MyConfiguration.getInstance().getProperty("BOT_TOKEN");
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.messageSender = new MessageSender(telegramClient);
        this.commandHandler = new CommandHandler(messageSender);
        this.dbManager = DatabaseManager.getInstance();
        System.out.println("MedBot inizializzato");
    }

    @Override
    public void consume(Update update) {
        //Gestisce i messaggi di testo
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText().trim();
            long chatId = update.getMessage().getChatId();
            String username = update.getMessage().getFrom().getUserName();

            //Registra l'utente nel database
            registerUser(chatId, username);

            //Gestisce i comandi
            if (messageText.startsWith("/")) {
                handleCommand(chatId, messageText, username);
            } else {
                messageSender.sendMessageWithMenu(chatId, "❓ Comando non riconosciuto. Usa il menù:");
            }
        }
        //Gestisce i callback dai bottoni inline
        else if (update.hasCallbackQuery()) {
            handleCallback(update);
        }
    }

    /**
     * Distribuisce i comandi al CommandHandler appropriato.
     */
    private void handleCommand(long chatId, String message, String username) {
        //Divide il comando dagli argomenti
        String[] parts = message.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        //Esegue il comando corrispondente
        switch (command) {
            case "/start" -> commandHandler.handleStart(chatId, username);
            case "/help" -> commandHandler.handleHelp(chatId);
            case "/cerca" -> commandHandler.handleSearchDrug(chatId, args, 0);
            case "/richiami" -> commandHandler.handleRecalls(chatId, args, 0);
            case "/mystats" -> commandHandler.handleMyStats(chatId);
            case "/recenti" -> commandHandler.handleRecentSearches(chatId);
            case "/farmacolegale" -> commandHandler.handleControlledSubstance(chatId, args);
            case "/effetticollaterali" -> commandHandler.handleAdverseEvents(chatId, args);
            case "/interazioni" -> commandHandler.handleDrugInteractions(chatId, args);
            case "/bookmarks" -> commandHandler.handleBookmarks(chatId, args);
            default -> messageSender.sendMessageWithMenu(chatId, "❓ Comando sconosciuto. Usa /help:");
        }
    }

    /**
     * Gestisce i callback dai bottoni inline.
     */
    private void handleCallback(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();

        //Rimuove la tastiera inline dal messaggio precedente
        try {
            telegramClient.execute(
                    org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .replyMarkup(null)
                            .build());
        } catch (TelegramApiException e) {
            //Ignora errori (es. messaggio troppo vecchio)
        }

        //Gestisce i diversi tipi di callback
        if (callbackData.startsWith("recalls:")) {
            String drugName = callbackData.substring(8);
            commandHandler.handleRecalls(chatId, drugName, 0);
        } else if (callbackData.startsWith("morerecalls:")) {
            String[] parts = callbackData.split(":", 3);
            commandHandler.handleRecalls(chatId, parts[1], Integer.parseInt(parts[2]));
        } else if (callbackData.startsWith("moredrugs:")) {
            String[] parts = callbackData.split(":", 3);
            commandHandler.handleSearchDrug(chatId, parts[1], Integer.parseInt(parts[2]));
        } else if (callbackData.startsWith("bookmark:")) {
            String drugName = callbackData.substring(9);
            commandHandler.handleBookmarks(chatId, "add " + drugName);
        }
    }

    /**
     * Registra un utente nel database (o aggiorna last_active).
     */
    private void registerUser(long chatId, String username) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO users (telegram_id, username, last_active) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                             "ON CONFLICT(telegram_id) DO UPDATE SET username = excluded.username, last_active = CURRENT_TIMESTAMP")) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.out.println("Errore registrazione utente: " + e.getMessage());
        }
    }
}