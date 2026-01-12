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
    private final CommandDispatcher commandDispatcher;
    private final DatabaseManager dbManager;
    
    /*Mappa che memorizza gli utenti in attesa di completare un comando
    Chiave: chatId dell'utente, Valore: comando in attesa (es. "cerca", "richiami")*/
    private final Map<Long, String> waitingForInput = new HashMap<>();

    //Costruttore: inizializza il bot e tutti i suoi componenti
    public MedBot() {
        //Legge il token del bot dal file di configurazione
        String botToken = MyConfiguration.getInstance().getProperty("BOT_TOKEN");
        
        //Crea il client Telegram usando OkHttp per le comunicazioni HTTP
        this.telegramClient = new OkHttpTelegramClient(botToken);
        
        //Inizializza l'helper per l'invio dei messaggi
        this.messageSender = new MessageSender(telegramClient);
        
        //Inizializza il dispatcher che gestisce il routing dei comandi agli handler
        this.commandDispatcher = new CommandDispatcher(messageSender);
        
        //Ottiene l'istanza del database manager
        this.dbManager = DatabaseManager.getInstance();
        
        System.out.println("Bot avviato");
    }

    /*Metodo chiamato automaticamente ogni volta che arriva un aggiornamento da Telegram
    Può essere un messaggio di testo, un callback da un bottone, ecc.*/
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

            /*Controlla se l'utente sta rispondendo a una richiesta di completamento comando
            Ad esempio: ha scritto /cerca senza parametri e ora sta inviando il nome del farmaco*/
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

    /*Gestisce la risposta dell'utente quando è in attesa di completare un comando
    Questo sistema permette un'interazione più fluida: l'utente può scrivere /cerca
    e poi inviare il nome del farmaco in un messaggio separato*/
    private void handlePendingCommand(long chatId, String input) {
        //Recupera quale comando era in attesa e lo rimuove dalla mappa
        String pendingCommand = waitingForInput.remove(chatId);
        
        //Se l'utente nel frattempo ha inviato un altro comando, annulla l'operazione
        if (input.startsWith("/")) {
            messageSender.sendMessage(chatId, "❌ Operazione annullata.");
            return;
        }

        //Esegue il comando originale usando l'input fornito come parametro
        commandDispatcher.handleCommand("/" + pendingCommand, chatId, input, null, telegramClient);
    }

    /*Elabora i comandi inviati dall'utente
    Separa il comando dai suoi argomenti e lo smista al dispatcher*/
    private void handleCommand(long chatId, String message, String username) {
        //Divide il messaggio in comando e argomenti (massimo 2 parti)
        //Esempio: "/cerca aspirin" -> parts[0]="/cerca", parts[1]="aspirin"
        String[] parts = message.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        //Gestisce i comandi speciali che richiedono input interattivo
        if (args.isEmpty() && requiresArgs(command)) {
            //Mette l'utente in attesa e chiede l'input necessario
            waitingForInput.put(chatId, command.replace("/", ""));
            sendInputPrompt(chatId, command);
            return;
        }

        //Gestisce il comando /statistiche che è un alias di /mystats con args "global"
        if (command.equals("/statistiche")) {
            commandDispatcher.handleCommand("/mystats", chatId, "global", username, telegramClient);
            return;
        }

        //Delega al dispatcher che instrada al handler appropriato
        boolean handled = commandDispatcher.handleCommand(command, chatId, args, username, telegramClient);
        
        //Se il comando non esiste, informa l'utente
        if (!handled) {
            messageSender.sendMessage(chatId, "❓ Comando sconosciuto. Usa /help");
        }
    }

    //Verifica se un comando richiede argomenti obbligatori
    private boolean requiresArgs(String command) {
        return command.equals("/cerca") || 
               command.equals("/richiami") || 
               command.equals("/effetticollaterali") || 
               command.equals("/interazioni");
    }

    //Invia il messaggio appropriato per richiedere l'input necessario
    private void sendInputPrompt(long chatId, String command) {
        String prompt = switch (command) {
            case "/cerca" -> "📝 <b>Inserisci il nome del farmaco da cercare:</b>\n\n💡 Esempio: aspirin, ibuprofen";
            case "/richiami" -> "📝 <b>Inserisci il nome del farmaco o 'all':</b>\n\n💡 Esempi:\n• aspirin (per un farmaco specifico)\n• all (per tutti gli ultimi richiami)";
            case "/effetticollaterali" -> "📝 <b>Inserisci il nome del farmaco:</b>\n\n💡 Esempio: aspirin, ibuprofen";
            case "/interazioni" -> "📝 <b>Inserisci i farmaci separati da +:</b>\n\n💡 Esempio: aspirin + ibuprofen";
            default -> "📝 Inserisci i parametri richiesti:";
        };
        messageSender.sendMessage(chatId, prompt);
    }

    /*Gestisce i callback query, cioè i click sui bottoni inline
    I bottoni inline sono quelli che appaiono sotto i messaggi del bot*/
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
                    org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery.builder()
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

        //Delega la gestione del callback al dispatcher
        commandDispatcher.handleCallback(callbackData, chatId, telegramClient);
    }

    /*Registra un utente nel database o aggiorna la sua ultima attività
    Questo permette di tenere traccia di chi usa il bot*/
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