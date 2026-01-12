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
import java.util.HashMap;
import java.util.Map;

/**
 * Bot Telegram per informazioni su farmaci tramite API OpenFDA.
 */
public class MedBot implements LongPollingSingleThreadUpdateConsumer {
    private final TelegramClient telegramClient;
    private final MessageSender messageSender;
    private final CommandHandler commandHandler;
    private final DatabaseManager dbManager;
    
    //Memorizza gli utenti in attesa di completare un comando
    private final Map<Long, String> waitingForInput = new HashMap<>();

    public MedBot() {
        String botToken = MyConfiguration.getInstance().getProperty("BOT_TOKEN");
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.messageSender = new MessageSender(telegramClient);
        this.commandHandler = new CommandHandler(messageSender);
        this.dbManager = DatabaseManager.getInstance();
        System.out.println("Bot avviato");
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText().trim();
            long chatId = update.getMessage().getChatId();
            String username = update.getMessage().getFrom().getUserName();

            registerUser(chatId, username);

            //Controlla se l'utente sta rispondendo a una richiesta di input
            if (waitingForInput.containsKey(chatId)) {
                handlePendingCommand(chatId, messageText);
                return;
            }

            if (messageText.startsWith("/")) {
                handleCommand(chatId, messageText, username);
            } else {
                messageSender.sendMessage(chatId, "❓ Comando non riconosciuto. Usa /help per la lista comandi.");
            }
        } else if (update.hasCallbackQuery()) {
            handleCallback(update);
        }
    }

    /**
     * Gestisce la risposta dell'utente quando è in attesa di un parametro.
     */
    private void handlePendingCommand(long chatId, String input) {
        String pendingCommand = waitingForInput.remove(chatId);
        
        //Se l'utente invia un altro comando, annulla l'operazione
        if (input.startsWith("/")) {
            messageSender.sendMessage(chatId, "❌ Operazione annullata.");
            return;
        }

        //Esegue il comando con il parametro fornito
        switch (pendingCommand) {
            case "cerca" -> commandHandler.handleSearchDrug(chatId, input, 0);
            case "richiami" -> commandHandler.handleRecalls(chatId, input, 0);
            case "effetticollaterali" -> commandHandler.handleAdverseEvents(chatId, input);
            case "interazioni" -> commandHandler.handleDrugInteractions(chatId, input);
            case "bookmarks_add" -> commandHandler.handleBookmarks(chatId, "add " + input);
            case "bookmarks_remove" -> commandHandler.handleBookmarks(chatId, "remove " + input);
        }
    }

    private void handleCommand(long chatId, String message, String username) {
        String[] parts = message.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        switch (command) {
            case "/start" -> commandHandler.handleStart(chatId, username);
            case "/help" -> commandHandler.handleHelp(chatId);
            
            case "/cerca" -> {
                if (args.isEmpty()) {
                    waitingForInput.put(chatId, "cerca");
                    messageSender.sendMessage(chatId, "📝 <b>Inserisci il nome del farmaco da cercare:</b>\n\n" +
                            "💡 Esempio: aspirin, ibuprofen, paracetamol");
                } else {
                    commandHandler.handleSearchDrug(chatId, args, 0);
                }
            }
            
            case "/richiami" -> {
                if (args.isEmpty()) {
                    waitingForInput.put(chatId, "richiami");
                    messageSender.sendMessage(chatId, "📝 <b>Inserisci il nome del farmaco o 'all':</b>\n\n" +
                            "💡 Esempi:\n" +
                            "• aspirin (per un farmaco specifico)\n" +
                            "• all (per tutti gli ultimi richiami)");
                } else {
                    commandHandler.handleRecalls(chatId, args, 0);
                }
            }
            
            case "/effetticollaterali" -> {
                if (args.isEmpty()) {
                    waitingForInput.put(chatId, "effetticollaterali");
                    messageSender.sendMessage(chatId, "📝 <b>Inserisci il nome del farmaco:</b>\n\n" +
                            "💡 Esempio: aspirin, ibuprofen");
                } else {
                    commandHandler.handleAdverseEvents(chatId, args);
                }
            }
            
            case "/interazioni" -> {
                if (args.isEmpty()) {
                    waitingForInput.put(chatId, "interazioni");
                    messageSender.sendMessage(chatId, "📝 <b>Inserisci i farmaci separati da +:</b>\n\n" +
                            "💡 Esempio: aspirin + ibuprofen");
                } else {
                    commandHandler.handleDrugInteractions(chatId, args);
                }
            }
            
            case "/mystats" -> commandHandler.handleMyStats(chatId);
            case "/recenti" -> commandHandler.handleRecentSearches(chatId);
            case "/bookmarks" -> commandHandler.handleBookmarks(chatId, args);
            
            default -> messageSender.sendMessage(chatId, "❓ Comando sconosciuto. Usa /help");
        }
    }

    private void handleCallback(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();

        try {
            telegramClient.execute(
                    org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .replyMarkup(null)
                            .build());
        } catch (TelegramApiException e) {
            //Ignora errori (messaggio troppo vecchio)
        }

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
            //Salva e mostra messaggio con bottone richiami
            commandHandler.handleBookmarkAdd(chatId, drugName);
        }
    }

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