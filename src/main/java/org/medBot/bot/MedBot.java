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

//Classe principale del bot Telegram per informazioni sui farmaci tramite API OpenFDA
//Implementa LongPollingSingleThreadUpdateConsumer per ricevere aggiornamenti da Telegram
public class MedBot implements LongPollingSingleThreadUpdateConsumer {
    private final TelegramClient telegramClient;
    private final MessageSender messageSender;
    private final CommandHandler commandHandler;
    private final DatabaseManager dbManager;
    
    //Mappa che memorizza gli utenti in attesa di completare un comando
    //Chiave: chatId dell'utente, Valore: comando in attesa (es. "cerca", "richiami")
    private final Map<Long, String> waitingForInput = new HashMap<>();

    //Costruttore: inizializza il bot e tutti i suoi componenti
    public MedBot() {
        //Legge il token del bot dal file di configurazione
        String botToken = MyConfiguration.getInstance().getProperty("BOT_TOKEN");
        
        //Crea il client Telegram usando OkHttp per le comunicazioni HTTP
        this.telegramClient = new OkHttpTelegramClient(botToken);
        
        //Inizializza l'helper per l'invio dei messaggi
        this.messageSender = new MessageSender(telegramClient);
        
        //Inizializza il gestore dei comandi che contiene la logica dei vari comandi
        this.commandHandler = new CommandHandler(messageSender);
        
        //Ottiene l'istanza del database manager
        this.dbManager = DatabaseManager.getInstance();
        
        System.out.println("Bot avviato");
    }

    //Metodo chiamato automaticamente ogni volta che arriva un aggiornamento da Telegram
    //Può essere un messaggio di testo, un callback da un bottone, ecc.
    @Override
    public void consume(Update update) {
        //Controlla se l'aggiornamento contiene un messaggio di testo
        if (update.hasMessage() && update.getMessage().hasText()) {
            //Estrae le informazioni dal messaggio
            String messageText = update.getMessage().getText().trim();
            long chatId = update.getMessage().getChatId();
            String username = update.getMessage().getFrom().getUserName();

            //Registra l'utente nel database (o aggiorna la sua ultima attività)
            registerUser(chatId, username);

            //Controlla se l'utente sta rispondendo a una richiesta di completamento comando
            //Ad esempio: ha scritto /cerca senza parametri e ora sta inviando il nome del farmaco
            if (waitingForInput.containsKey(chatId)) {
                handlePendingCommand(chatId, messageText);
                return;
            }

            //Se il messaggio inizia con "/" è un comando, altrimenti è testo non riconosciuto
            if (messageText.startsWith("/")) {
                handleCommand(chatId, messageText, username);
            } else {
                messageSender.sendMessage(chatId, "❓ Comando non riconosciuto. Usa /help per la lista comandi.");
            }
        } 
        //Controlla se l'aggiornamento è un callback query (click su un bottone inline)
        else if (update.hasCallbackQuery()) {
            handleCallback(update);
        }
    }

    //Gestisce la risposta dell'utente quando è in attesa di completare un comando
    //Questo sistema permette un'interazione più fluida: l'utente può scrivere /cerca
    //e poi inviare il nome del farmaco in un messaggio separato
    private void handlePendingCommand(long chatId, String input) {
        //Recupera quale comando era in attesa e lo rimuove dalla mappa
        String pendingCommand = waitingForInput.remove(chatId);
        
        //Se l'utente nel frattempo ha inviato un altro comando, annulla l'operazione
        if (input.startsWith("/")) {
            messageSender.sendMessage(chatId, "❌ Operazione annullata.");
            return;
        }

        //Esegue il comando originale usando l'input fornito come parametro
        switch (pendingCommand) {
            case "cerca" -> commandHandler.handleSearchDrug(chatId, input, 0);
            case "richiami" -> commandHandler.handleRecalls(chatId, input, 0);
            case "effetticollaterali" -> commandHandler.handleAdverseEvents(chatId, input);
            case "interazioni" -> commandHandler.handleDrugInteractions(chatId, input);
            case "bookmarks_add" -> commandHandler.handleBookmarks(chatId, "add " + input);
            case "bookmarks_remove" -> commandHandler.handleBookmarks(chatId, "remove " + input);
        }
    }

    //Elabora i comandi inviati dall'utente
    //Separa il comando dai suoi argomenti e chiama il metodo appropriato
    private void handleCommand(long chatId, String message, String username) {
        //Divide il messaggio in comando e argomenti (massimo 2 parti)
        //Esempio: "/cerca aspirin" -> parts[0]="/cerca", parts[1]="aspirin"
        String[] parts = message.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        //Switch che gestisce ogni comando supportato dal bot
        switch (command) {
            case "/start" -> commandHandler.handleStart(chatId, username);
            case "/help" -> commandHandler.handleHelp(chatId);
            
            //Per /cerca: se non ci sono argomenti, chiede il nome del farmaco
            //altrimenti esegue subito la ricerca
            case "/cerca" -> {
                if (args.isEmpty()) {
                    //Mette l'utente in attesa e chiede il nome del farmaco
                    waitingForInput.put(chatId, "cerca");
                    messageSender.sendMessage(chatId, "📝 <b>Inserisci il nome del farmaco da cercare:</b>\n\n" +
                            "💡 Esempio: aspirin, ibuprofen");
                } else {
                    commandHandler.handleSearchDrug(chatId, args, 0);
                }
            }
            
            //Per /richiami: stessa logica di /cerca
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
            
            //Per /effetticollaterali: chiede il farmaco se non specificato
            case "/effetticollaterali" -> {
                if (args.isEmpty()) {
                    waitingForInput.put(chatId, "effetticollaterali");
                    messageSender.sendMessage(chatId, "📝 <b>Inserisci il nome del farmaco:</b>\n\n" +
                            "💡 Esempio: aspirin, ibuprofen");
                } else {
                    commandHandler.handleAdverseEvents(chatId, args);
                }
            }
            
            //Per /interazioni: chiede i farmaci se non specificati
            case "/interazioni" -> {
                if (args.isEmpty()) {
                    waitingForInput.put(chatId, "interazioni");
                    messageSender.sendMessage(chatId, "📝 <b>Inserisci i farmaci separati da +:</b>\n\n" +
                            "💡 Esempio: aspirin + ibuprofen");
                } else {
                    commandHandler.handleDrugInteractions(chatId, args);
                }
            }
            
            //Comandi semplici che non richiedono parametri aggiuntivi
            case "/mystats" -> commandHandler.handleMyStats(chatId);
            case "/recenti" -> commandHandler.handleRecentSearches(chatId);
            case "/bookmarks" -> commandHandler.handleBookmarks(chatId, args);
            case "/statistiche" -> commandHandler.handleGlobalStats(chatId);
            
            //Se il comando non è riconosciuto, informa l'utente
            default -> messageSender.sendMessage(chatId, "❓ Comando sconosciuto. Usa /help");
        }
    }

    //Gestisce i callback query, cioè i click sui bottoni inline
    //I bottoni inline sono quelli che appaiono sotto i messaggi del bot
    private void handleCallback(Update update) {
        //Estrae i dati dal callback
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();

        //Caso speciale: bottone "Già salvato" che è solo informativo
        if (callbackData.equals("already_saved")) {
            //Mostra un popup informativo senza rimuovere i bottoni
            try {
                telegramClient.execute(
                    org.telegram.telegrambots.meta.api.methods.answerCallbackQuery.AnswerCallbackQuery.builder()
                        .callbackQueryId(update.getCallbackQuery().getId())
                        .text("ℹ️ Già nei preferiti")
                        .showAlert(false)
                        .build());
            } catch (TelegramApiException e) {
                //Ignora errori (ad esempio se il callback è scaduto)
            }
            return;
        }

        //Per tutti gli altri callback, rimuove i bottoni dal messaggio
        //Questo evita che l'utente possa cliccare più volte sullo stesso bottone
        try {
            telegramClient.execute(
                    org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .replyMarkup(null)
                            .build());
        } catch (TelegramApiException e) {
            //Ignora errori (messaggio troppo vecchio per essere modificato)
        }

        //Gestisce i diversi tipi di callback in base al prefisso del callbackData
        if (callbackData.startsWith("recalls:")) {
            //Estrae il nome del farmaco dopo "recalls:"
            String drugName = callbackData.substring(8);
            commandHandler.handleRecalls(chatId, drugName, 0);
        } else if (callbackData.startsWith("morerecalls:")) {
            //Per vedere più richiami (paginazione)
            String[] parts = callbackData.split(":", 3);
            commandHandler.handleRecalls(chatId, parts[1], Integer.parseInt(parts[2]));
        } else if (callbackData.startsWith("moredrugs:")) {
            //Per vedere più risultati di ricerca (paginazione)
            String[] parts = callbackData.split(":", 3);
            commandHandler.handleSearchDrug(chatId, parts[1], Integer.parseInt(parts[2]));
        } else if (callbackData.startsWith("bookmark:")) {
            //Per salvare un farmaco nei preferiti
            String drugName = callbackData.substring(9);
            commandHandler.handleBookmarkAdd(chatId, drugName);
        }
    }

    //Registra un utente nel database o aggiorna la sua ultima attività
    //Questo permette di tenere traccia di chi usa il bot
    private void registerUser(long chatId, String username) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO users (telegram_id, username, last_active) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                             "ON CONFLICT(telegram_id) DO UPDATE SET username = excluded.username, last_active = CURRENT_TIMESTAMP")) {
            //ON CONFLICT: se l'utente esiste già, aggiorna solo username e last_active
            pstmt.setLong(1, chatId);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.out.println("Errore registrazione utente: " + e.getMessage());
        }
    }
}