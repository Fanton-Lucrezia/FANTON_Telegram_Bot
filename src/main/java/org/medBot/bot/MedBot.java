package org.medBot.bot;

import org.medBot.MyConfiguration;
import org.medBot.dao.DatabaseManager;
import org.medBot.handler.*;
import org.medBot.service.OpenFdaService;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

/**
 * Bot Telegram per informazioni su farmaci.
 * Usa un sistema di handler per gestire i comandi in modo ordinato.
 */
public class MedBot implements LongPollingSingleThreadUpdateConsumer {
    private final TelegramClient telegramClient;
    private final OpenFdaService fdaService;
    private final DatabaseManager dbManager;
    
    //Mappa che associa ogni comando al suo handler
    private final Map<String, CommandHandler> handlers;
    
    //Handler speciali per callback e ricerche paginate
    private final SearchHandler searchHandler;
    private final RecallsHandler recallsHandler;
    private final BookmarksHandler bookmarksHandler;

    public MedBot() {
        String botToken = MyConfiguration.getInstance().getProperty("BOT_TOKEN");
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.fdaService = new OpenFdaService();
        this.dbManager = DatabaseManager.getInstance();
        
        //Inizializza gli handler
        this.handlers = new HashMap<>();
        this.searchHandler = new SearchHandler(fdaService, dbManager);
        this.recallsHandler = new RecallsHandler(fdaService);
        this.bookmarksHandler = new BookmarksHandler(dbManager);
        
        //Registra tutti i comandi con i relativi handler
        registerHandler(new StartHandler());
        registerHandler(new HelpHandler());
        registerHandler(searchHandler);
        registerHandler(recallsHandler);
        registerHandler(new StatsHandler(dbManager));
        registerHandler(new RecentHandler(dbManager));
        registerHandler(new ControlledSubstanceHandler(fdaService));
        registerHandler(new AdverseEventsHandler(fdaService));
        registerHandler(new InteractionsHandler(fdaService));
        registerHandler(bookmarksHandler);
        
        System.out.println("MedBot inizializzato con " + handlers.size() + " comandi");
    }
    
    /**
     * Registra un handler nella mappa dei comandi.
     */
    private void registerHandler(CommandHandler handler) {
        handlers.put(handler.getCommandName(), handler);
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
                handleCommand(chatId, messageText);
            } else {
                handlers.get("start").handle(chatId, "", telegramClient);
            }
        }
        //Gestisce i callback dai bottoni inline
        else if (update.hasCallbackQuery()) {
            handleCallback(update);
        }
    }

    /**
     * Gestisce i comandi ricevuti dall'utente.
     * Divide il comando e lo passa all'handler appropriato.
     */
    private void handleCommand(long chatId, String message) {
        //Divide il comando dagli argomenti
        String[] parts = message.split("\\s+", 2);
        String command = parts[0].substring(1).toLowerCase(); //Rimuove lo /
        String args = parts.length > 1 ? parts[1].trim() : "";

        //Cerca l'handler per il comando
        CommandHandler handler = handlers.get(command);
        
        if (handler != null) {
            handler.handle(chatId, args, telegramClient);
        } else {
            handlers.get("start").handle(chatId, "", telegramClient);
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
            //Ignora errori di rimozione tastiera
        }

        //Gestisce i diversi tipi di callback
        if (callbackData.startsWith("recalls:")) {
            String drugName = callbackData.substring(8);
            recallsHandler.handleRecalls(chatId, drugName, 0, telegramClient);
            
        } else if (callbackData.startsWith("morerecalls:")) {
            String[] parts = callbackData.split(":", 3);
            recallsHandler.handleRecalls(chatId, parts[1], Integer.parseInt(parts[2]), telegramClient);
            
        } else if (callbackData.startsWith("moredrugs:")) {
            String[] parts = callbackData.split(":", 3);
            searchHandler.handleSearch(chatId, parts[1], Integer.parseInt(parts[2]), telegramClient);
            
        } else if (callbackData.startsWith("bookmark:")) {
            String drugName = callbackData.substring(9);
            bookmarksHandler.addBookmark(chatId, drugName, telegramClient);
        }
    }

    /**
     * Registra un utente nel database.
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