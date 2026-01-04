package org.example.bot;

import org.example.MyConfiguration;
import org.example.dao.DatabaseManager;
import org.example.model.Drug;
import org.example.model.Recall;
import org.example.service.OpenFdaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * Bot Telegram che interagisce con l'API OpenFDA.
 * Gestione comandi e database SQLite.
 */
public class MedBot implements LongPollingSingleThreadUpdateConsumer {
    private static final Logger logger = LoggerFactory.getLogger(MedBot.class);
    private final TelegramClient telegramClient;
    private final OpenFdaService fdaService;
    private final DatabaseManager dbManager;

    // Disclaimer medico obbligatorio
    private static final String DISCLAIMER = "\n\n⚠️ <i>Disclaimer: Info a scopo informativo. " +
            "Non è un parere medico. In caso di dubbi, contatta un medico.</i>";

    public MedBot() {
        // Carico la configurazione
        String botToken = MyConfiguration.getInstance().getProperty("BOT_TOKEN");
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.fdaService = new OpenFdaService();
        this.dbManager = DatabaseManager.getInstance();
        logger.info("MedBot avviato correttamente");
    }

    @Override
    public void consume(Update update) {
        // Controllo se è un messaggio di testo
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText().trim();
            long chatId = update.getMessage().getChatId();
            String username = update.getMessage().getFrom().getUserName();

            // Salviamo l'utente nel db se è nuovo
            registerUser(chatId, username);

            logger.info("Messaggio da {}: {}", chatId, messageText);

            // Gestione dei comandi
            if (messageText.startsWith("/")) {
                handleCommand(chatId, messageText, username);
            } else {
                sendMessage(chatId, "❓ Non capisco. Usa /help per vedere cosa posso fare.");
            }
        } else if (update.hasCallbackQuery()) {
            // Gestione dei pulsanti
            handleCallback(update);
        }
    }

    private void handleCommand(long chatId, String message, String username) {
        // Divido il comando dagli argomenti
        String[] parts = message.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        switch (command) {
            case "/start" -> handleStart(chatId, username);
            case "/help" -> handleHelp(chatId);
            case "/cerca" -> handleSearchDrug(chatId, args);
            case "/richiami" -> handleRecalls(chatId, args); // Solo richiami ora
            case "/mystats" -> handleMyStats(chatId);
            default -> sendMessage(chatId, "❓ Comando non valido. Prova /help.");
        }
    }

    private void handleStart(long chatId, String username) {
        String welcome = String.format(
                "👋 Ciao <b>%s</b>! Benvenuto su OpenFDA MedBot.\n\n" +
                        "Sono qui per aiutarti a trovare informazioni sui farmaci usando i dati ufficiali FDA.\n" +
                        "Ricorda che i dati sono in <b>inglese</b>, quindi prova a cercare i nomi in inglese (es. 'aspirin').\n\n"
                        +
                        "Digita /help per iniziare.",
                username != null ? username : "utente");
        sendMessage(chatId, welcome + DISCLAIMER);
    }

    private void handleHelp(long chatId) {
        String help = """
                <b>📋 Comandi Disponibili</b>

                /start - Riavvia il bot
                /help - Mostra questo messaggio

                <b>🔍 Ricerca</b>
                /cerca <nome> - Cerca informazioni su un farmaco
                Esempio: <code>/cerca aspirin</code>

                <b>⚠️ Richiami</b>
                /richiami <nome> - Controlla richiami per un farmaco
                Esempio: <code>/richiami ibuprofen</code>
                Usa <code>/richiami all</code> per vedere gli ultimi 10.

                <b>📊 Statistiche</b>
                /mystats - Le tue statistiche d'uso
                """;

        sendMessage(chatId, help);
    }

    private void handleSearchDrug(long chatId, String drugName) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Inserisci il nome del farmaco.\nEsempio: <code>/cerca paracetamol</code>");
            return;
        }

        sendMessage(chatId, "🔍 Cerco \"" + drugName + "\"...");

        // Salviamo la ricerca nel db
        recordSearch(chatId, drugName);

        try {
            List<Drug> drugs = fdaService.searchDrug(drugName);

            if (drugs.isEmpty()) {
                sendMessage(chatId,
                        "❌ Nessun farmaco trovato per \"" + escapeHtml(drugName) + "\".\nProva in inglese.");
                return;
            }

            // Prendiamo solo i primi 3 per non intasare la chat
            int count = Math.min(3, drugs.size());
            StringBuilder response = new StringBuilder();
            response.append(String.format("✅ Trovati <b>%d risultati</b>:\n\n", drugs.size()));

            for (int i = 0; i < count; i++) {
                response.append(formatDrugInfo(drugs.get(i), i + 1));
                if (i < count - 1)
                    response.append("\n➖➖➖\n\n");
            }

            // Aggiungiamo il bottone per i richiami se serve approfondire
            InlineKeyboardMarkup keyboard = createRecallsKeyboard(drugName);
            sendMessageWithKeyboard(chatId, response.toString(), keyboard);

        } catch (Exception e) {
            logger.error("Errore ricerca farmaco: " + drugName, e);
            sendMessage(chatId, "❌ Errore durante la ricerca. Riprova più tardi.");
        }
    }

    private void handleRecalls(long chatId, String drugName) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Specifica un farmaco o usa 'all'.");
            return;
        }

        sendMessage(chatId, "🔍 Controllo richiami...");

        try {
            // Se chiede "all" mostriamo gli ultimi, altrimenti cerchiamo specifico
            List<Recall> recalls = drugName.equalsIgnoreCase("all")
                    ? fdaService.getRecentRecalls(10)
                    : fdaService.searchRecalls(drugName);

            if (recalls.isEmpty()) {
                sendMessage(chatId, "✅ Nessun richiamo trovato per \"" + escapeHtml(drugName) + "\". Ottimo!");
                return;
            }

            StringBuilder response = new StringBuilder();
            response.append(String.format("⚠️ Trovati <b>%d richiami FDA</b>:\n\n", recalls.size()));

            int count = Math.min(5, recalls.size());
            for (int i = 0; i < count; i++) {
                response.append(formatRecallInfo(recalls.get(i), i + 1));
                if (i < count - 1)
                    response.append("\n➖➖➖\n\n");
            }

            sendMessage(chatId, response.toString());

        } catch (Exception e) {
            logger.error("Errore richiami: " + drugName, e);
            sendMessage(chatId, "❌ Errore nel controllo richiami.");
        }
    }

    private void handleMyStats(long chatId) {
        try {
            int count = getSearchCount(chatId);
            String msg = String.format("📊 <b>Le tue Statistiche</b>\n\nHai effettuato <b>%d</b> ricerche.", count);
            sendMessage(chatId, msg);
        } catch (Exception e) {
            sendMessage(chatId, "❌ Impossibile recuperare le statistiche.");
        }
    }

    // ---------------- METODI DI UTILITÀ ----------------

    private void registerUser(long chatId, String username) {
        // Query per inserire utenti o aggiornare l'ultimo accesso
        String sql = "INSERT INTO users (telegram_id, username, last_active) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT(telegram_id) DO UPDATE SET username = excluded.username, last_active = CURRENT_TIMESTAMP";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, chatId);
            pstmt.setString(2, username);
            pstmt.executeUpdate();

        } catch (Exception e) {
            logger.warn("Errore registrazione utente: " + e.getMessage());
        }
    }

    private void recordSearch(long chatId, String query) {
        try (Connection conn = dbManager.getConnection()) {
            // Salvo la query
            try (PreparedStatement pstmt = conn
                    .prepareStatement("INSERT INTO searches (telegram_id, query_text) VALUES (?, ?)")) {
                pstmt.setLong(1, chatId);
                pstmt.setString(2, query);
                pstmt.executeUpdate();
            }

            // Aggiorno il contatore utente
            try (PreparedStatement pstmt = conn
                    .prepareStatement("UPDATE users SET search_count = search_count + 1 WHERE telegram_id = ?")) {
                pstmt.setLong(1, chatId);
                pstmt.executeUpdate();
            }

        } catch (Exception e) {
            logger.warn("Errore salvataggio ricerca: " + e.getMessage());
        }
    }

    private int getSearchCount(long chatId) {
        try (Connection conn = dbManager.getConnection();
                PreparedStatement pstmt = conn
                        .prepareStatement("SELECT search_count FROM users WHERE telegram_id = ?")) {

            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next())
                return rs.getInt("search_count");

        } catch (Exception e) {
            logger.error("Errore lettura statistiche", e);
        }
        return 0;
    }

    private String formatDrugInfo(Drug drug, int index) {
        // Formattazione semplice del risultato
        String info = String.format("<b>%d. %s</b>\n", index, escapeHtml(drug.getBrandName()));

        if (drug.getGenericName() != null)
            info += "   📋 <i>Principio:</i> " + escapeHtml(drug.getGenericName()) + "\n";

        if (drug.getManufacturer() != null)
            info += "   🏭 <i>Produttore:</i> " + escapeHtml(drug.getManufacturer()) + "\n";

        if (drug.getIndications() != null)
            info += "   💊 <i>Uso:</i> " + escapeHtml(truncate(drug.getIndications(), 150)) + "\n";

        return info;
    }

    private String formatRecallInfo(Recall recall, int index) {
        String info = String.format("<b>%d. Data: %s</b>\n", index,
                recall.getRecallDate() != null ? recall.getRecallDate() : "N/D");

        if (recall.getProductDescription() != null)
            info += "   📦 " + escapeHtml(truncate(recall.getProductDescription(), 80)) + "\n";

        if (recall.getReasonForRecall() != null)
            info += "   ⚠️ " + escapeHtml(truncate(recall.getReasonForRecall(), 100)) + "\n";

        return info;
    }

    private InlineKeyboardMarkup createRecallsKeyboard(String drugName) {
        InlineKeyboardButton btn = InlineKeyboardButton.builder()
                .text("🔍 Verifica Richiami")
                .callbackData("recalls:" + drugName)
                .build();

        return InlineKeyboardMarkup.builder().keyboardRow(new InlineKeyboardRow(btn)).build();
    }

    private void handleCallback(Update update) {
        String data = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (data.startsWith("recalls:")) {
            handleRecalls(chatId, data.substring(8));
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage msg = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .build();
        try {
            telegramClient.execute(msg);
        } catch (TelegramApiException e) {
            logger.error("Impossibile inviare messaggio a " + chatId, e);
        }
    }

    private void sendMessageWithKeyboard(long chatId, String text, InlineKeyboardMarkup kb) {
        SendMessage msg = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(kb)
                .build();
        try {
            telegramClient.execute(msg);
        } catch (TelegramApiException e) {
            logger.error("Errore invio keyboard", e);
        }
    }

    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max)
            return text;
        return text.substring(0, max - 3) + "...";
    }
}
