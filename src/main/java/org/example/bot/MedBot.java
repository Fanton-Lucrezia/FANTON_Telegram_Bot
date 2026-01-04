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
 * Bot Telegram semplificato per OpenFDA.
 * Gestisce i comandi principali e interagisce con il database SQLite.
 */
public class MedBot implements LongPollingSingleThreadUpdateConsumer {
    private static final Logger logger = LoggerFactory.getLogger(MedBot.class);
    private final TelegramClient telegramClient;
    private final OpenFdaService fdaService;
    private final DatabaseManager dbManager;

    private static final String DISCLAIMER = "\n\n⚠️ <i>Disclaimer: Queste informazioni sono solo a scopo informativo " +
            "e non costituiscono consulenza medica. In caso di dubbi o emergenze, " +
            "contatta un professionista sanitario.</i>";

    public MedBot() {
        String botToken = MyConfiguration.getInstance().getProperty("BOT_TOKEN");
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.fdaService = new OpenFdaService();
        this.dbManager = DatabaseManager.getInstance();
        logger.info("MedBot initialized");
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText().trim();
            long chatId = update.getMessage().getChatId();
            String username = update.getMessage().getFrom().getUserName();

            // Registra utente nel database
            registerUser(chatId, username);

            logger.info("Message from {}: {}", chatId, messageText);

            // Gestisci comandi
            if (messageText.startsWith("/")) {
                handleCommand(chatId, messageText, username);
            } else {
                sendMessage(chatId, "❓ Non ho capito. Usa /help per vedere i comandi disponibili.");
            }
        } else if (update.hasCallbackQuery()) {
            handleCallback(update);
        }
    }

    private void handleCommand(long chatId, String message, String username) {
        String[] parts = message.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        logger.debug("Command: {}, Args: {}", command, args);

        switch (command) {
            case "/start" -> handleStart(chatId, username);
            case "/help" -> handleHelp(chatId);
            case "/cerca" -> handleSearchDrug(chatId, args);
            case "/richiami" -> handleRecalls(chatId, args);
            case "/mystats" -> handleMyStats(chatId);
            case "/recenti" -> handleRecentSearches(chatId);
            case "/schedadroga" -> handleDrugSchedule(chatId, args);
            default -> sendMessage(chatId, "❓ Comando sconosciuto. Usa /help per la lista dei comandi.");
        }
    }

    private void handleStart(long chatId, String username) {
        String welcome = String.format(
                "👋 Benvenuto <b>%s</b> su OpenFDA MedBot!\n\n" +
                        "🔬 Questo bot utilizza l'<b>API pubblica della FDA</b> (Food and Drug Administration americana) " +
                        "per fornirti informazioni su farmaci e richiami.\n\n" +
                        "🇺🇸 <b>Nota importante:</b> I dati provengono da un database americano, quindi " +
                        "i risultati sono in <b>inglese</b>. Per migliori risultati, cerca i farmaci con il loro " +
                        "nome inglese (es. 'aspirin' invece di 'aspirina').\n\n" +
                        "📖 Usa /help per vedere tutti i comandi disponibili.",
                username != null ? username : "utente"
        );
        sendMessage(chatId, welcome + DISCLAIMER);
    }

    private void handleHelp(long chatId) {
        String help = "<b>📋 Lista Comandi</b>\n\n" +
                "/start - Messaggio di benvenuto\n" +
                "/help - Mostra questa lista\n\n" +
                "<b>🔍 Ricerca Farmaci</b>\n" +
                "/cerca &lt;nome&gt;\n" +
                "Cerca informazioni su un farmaco specifico.\n" +
                "Esempio: <code>/cerca aspirin</code>\n\n" +
                "<b>⚠️ Richiami FDA</b>\n" +
                "/richiami &lt;nome|all&gt;\n" +
                "Verifica se ci sono richiami per un farmaco.\n" +
                "Esempi:\n" +
                "• <code>/richiami aspirin</code> - richiami per aspirina\n" +
                "• <code>/richiami all</code> - ultimi 10 richiami\n\n" +
                "<b>📊 Le Tue Informazioni</b>\n" +
                "/mystats - Statistiche personali\n" +
                "/recenti - Farmaci cercati di recente\n\n" +
                "<b>🚨 Sostanze Controllate</b>\n" +
                "/schedadroga &lt;nome&gt;\n" +
                "Verifica se un farmaco è una sostanza controllata (droga).\n" +
                "Esempio: <code>/schedadroga oxycodone</code>\n\n" +
                "💡 <b>Suggerimento:</b> Usa i nomi dei farmaci in <b>inglese</b> " +
                "per ottenere risultati migliori!\n" +
                "Esempi: 'aspirin', 'ibuprofen', 'amoxicillin'";

        sendMessage(chatId, help);
    }

    private void handleSearchDrug(long chatId, String drugName) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Devi specificare il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/cerca aspirina</code>");
            return;
        }

        sendMessage(chatId, "🔍 Cerco \"" + drugName + "\"...");

        // Registra la ricerca
        recordSearch(chatId, drugName);

        try {
            List<Drug> drugs = fdaService.searchDrug(drugName);

            if (drugs.isEmpty()) {
                sendMessage(chatId,
                        "❌ <b>Nessun risultato</b> per \"" + escapeHtml(drugName) + "\".\n\n" +
                                "💡 <b>Suggerimenti:</b>\n" +
                                "• Controlla l'ortografia\n" +
                                "• Prova con il nome generico (es. 'ibuprofen')\n" +
                                "• Usa il principio attivo del farmaco\n" +
                                "• Prova in inglese (es. 'aspirin' invece di 'aspirina')");
                return;
            }

            // Mostra i primi 3 risultati
            int count = Math.min(3, drugs.size());
            StringBuilder response = new StringBuilder();
            response.append(String.format("✅ <b>%d risultati</b> per \"%s\":\n\n",
                    drugs.size(), escapeHtml(drugName)));

            for (int i = 0; i < count; i++) {
                Drug drug = drugs.get(i);
                response.append(formatDrugInfo(drug, i + 1));
                if (i < count - 1) response.append("\n➖➖➖\n\n");
            }

            if (drugs.size() > 3) {
                response.append(String.format("\n\n<i>... e altri %d risultati</i>", drugs.size() - 3));
            }

            // Aggiungi pulsante per richiami (non inline, usa callback)
            InlineKeyboardMarkup keyboard = createRecallsKeyboard(drugName);
            sendMessageWithKeyboard(chatId, response.toString(), keyboard);

        } catch (Exception e) {
            logger.error("Error searching drug: " + drugName, e);

            String errorMsg = "❌ <b>Errore durante la ricerca</b>\n\n";

            if (e.getMessage().contains("404")) {
                errorMsg += "Il farmaco \"" + escapeHtml(drugName) + "\" non è stato trovato nel database FDA.\n\n" +
                        "💡 Prova con un nome diverso o più generico.";
            } else if (e.getMessage().contains("timeout") || e.getMessage().contains("connect")) {
                errorMsg += "⚠️ Impossibile contattare il server FDA.\n" +
                        "Riprova tra qualche minuto.";
            } else {
                errorMsg += "Si è verificato un errore tecnico.\n" +
                        "Dettaglio: " + e.getMessage();
            }

            sendMessage(chatId, errorMsg);
        }
    }

    private void handleRecalls(long chatId, String drugName) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Specifica il nome del farmaco o scrivi 'all'.\n\n" +
                    "📝 Esempio: <code>/richiami aspirina</code>\n" +
                    "📝 Oppure: <code>/richiami all</code>");
            return;
        }

        sendMessage(chatId, "🔍 Cerco richiami...");

        try {
            List<Recall> recalls = drugName.equalsIgnoreCase("all")
                    ? fdaService.getRecentRecalls(10)
                    : fdaService.searchRecalls(drugName);

            if (recalls.isEmpty()) {
                sendMessage(chatId,
                        "✅ <b>Nessun richiamo trovato</b> per \"" + escapeHtml(drugName) + "\".\n\n" +
                                "🎉 Buone notizie! Non ci sono richiami recenti per questo farmaco.");
                return;
            }

            StringBuilder response = new StringBuilder();
            response.append(String.format("⚠️ <b>%d Richiami FDA</b> trovati", recalls.size()));
            if (!drugName.equalsIgnoreCase("all")) {
                response.append(" per \"").append(escapeHtml(drugName)).append("\"");
            }
            response.append(":\n\n");

            int count = Math.min(5, recalls.size());
            for (int i = 0; i < count; i++) {
                Recall recall = recalls.get(i);
                response.append(formatRecallInfo(recall, i + 1));
                if (i < count - 1) response.append("\n➖➖➖\n\n");
            }

            if (recalls.size() > 5) {
                response.append(String.format("\n\n<i>... e altri %d richiami</i>", recalls.size() - 5));
            }

            sendMessage(chatId, response.toString());

        } catch (Exception e) {
            logger.error("Error searching recalls: " + drugName, e);

            String errorMsg = "❌ <b>Errore durante la ricerca richiami</b>\n\n";

            if (e.getMessage().contains("404")) {
                errorMsg += "✅ Nessun richiamo trovato per \"" + escapeHtml(drugName) + "\".\n" +
                        "Questo è positivo!";
            } else {
                errorMsg += "Si è verificato un errore. Riprova più tardi.\n" +
                        "Dettaglio: " + e.getMessage();
            }

            sendMessage(chatId, errorMsg);
        }
    }

    private void handleMyStats(long chatId) {
        try {
            int searchCount = getSearchCount(chatId);

            // Ottieni i farmaci più cercati
            StringBuilder topDrugs = new StringBuilder();
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "SELECT query_text, COUNT(*) as count FROM searches " +
                                 "WHERE telegram_id = ? " +
                                 "GROUP BY LOWER(query_text) " +
                                 "ORDER BY count DESC LIMIT 5")) {

                pstmt.setLong(1, chatId);
                ResultSet rs = pstmt.executeQuery();

                int rank = 1;
                while (rs.next()) {
                    topDrugs.append(String.format("%d. %s (%d volte)\n",
                            rank++, rs.getString("query_text"), rs.getInt("count")));
                }
            }

            String response = String.format(
                    "📊 <b>Le tue statistiche:</b>\n\n" +
                            "🔍 Ricerche totali: <b>%d</b>\n" +
                            "👤 ID Telegram: <code>%d</code>\n\n",
                    searchCount, chatId);

            if (topDrugs.length() > 0) {
                response += "💊 <b>Farmaci più cercati:</b>\n" + topDrugs + "\n";
            }

            response += "💡 Usa /recenti per vedere le ultime ricerche!";

            sendMessage(chatId, response);

        } catch (Exception e) {
            logger.error("Error getting stats for user: " + chatId, e);
            sendMessage(chatId, "❌ Errore nel recuperare le statistiche.");
        }
    }

    private void handleRecentSearches(long chatId) {
        try {
            StringBuilder recent = new StringBuilder();
            recent.append("📜 <b>Farmaci cercati di recente:</b>\n\n");

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "SELECT DISTINCT query_text, MAX(created_at) as last_search " +
                                 "FROM searches " +
                                 "WHERE telegram_id = ? " +
                                 "GROUP BY LOWER(query_text) " +
                                 "ORDER BY last_search DESC LIMIT 10")) {

                pstmt.setLong(1, chatId);
                ResultSet rs = pstmt.executeQuery();

                int count = 0;
                while (rs.next()) {
                    count++;
                    recent.append(String.format("• %s\n", rs.getString("query_text")));
                }

                if (count == 0) {
                    sendMessage(chatId, "📜 Non hai ancora fatto ricerche.\n\nProva con /cerca aspirin!");
                    return;
                }
            }

            recent.append("\n💡 Clicca su un farmaco per cercarlo di nuovo:\n");
            recent.append("Esempio: <code>/cerca aspirin</code>");

            sendMessage(chatId, recent.toString());

        } catch (Exception e) {
            logger.error("Error getting recent searches", e);
            sendMessage(chatId, "❌ Errore nel recuperare le ricerche recenti.");
        }
    }

    private void handleDrugSchedule(long chatId, String drugName) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Devi specificare il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/schedadroga oxycodone</code>");
            return;
        }

        sendMessage(chatId, "🔍 Verifico lo status legale di \"" + drugName + "\"...");

        try {
            String schedule = fdaService.checkDrugSchedule(drugName);

            if (schedule == null) {
                sendMessage(chatId,
                        "✅ <b>\"" + escapeHtml(drugName) + "\" non è classificato come sostanza controllata</b>\n\n" +
                                "Questo farmaco non risulta essere una droga secondo la classificazione DEA (Drug Enforcement Administration).");
            } else {
                String emoji = getScheduleEmoji(schedule);
                String description = getScheduleDescription(schedule);

                sendMessage(chatId, String.format(
                        "🚨 <b>SOSTANZA CONTROLLATA</b>\n\n" +
                                "Farmaco: <b>%s</b>\n" +
                                "Classificazione DEA: %s <b>Schedule %s</b>\n\n" +
                                "<i>%s</i>\n\n" +
                                "⚠️ Questo farmaco è soggetto a controlli rigorosi per rischio di abuso o dipendenza.",
                        escapeHtml(drugName), emoji, schedule, description
                ));
            }

        } catch (Exception e) {
            logger.error("Error checking drug schedule: " + drugName, e);
            sendMessage(chatId, "❌ Errore durante la verifica. Riprova più tardi.");
        }
    }

    private String getScheduleEmoji(String schedule) {
        return switch (schedule) {
            case "I", "II" -> "🔴";
            case "III" -> "🟠";
            case "IV" -> "🟡";
            case "V" -> "🟢";
            default -> "⚪";
        };
    }

    private String getScheduleDescription(String schedule) {
        return switch (schedule) {
            case "I" -> "Alto potenziale di abuso, nessun uso medico accettato negli USA";
            case "II" -> "Alto potenziale di abuso, può causare grave dipendenza fisica o psicologica";
            case "III" -> "Potenziale di abuso moderato, può causare dipendenza fisica o psicologica";
            case "IV" -> "Basso potenziale di abuso relativo a Schedule III";
            case "V" -> "Basso potenziale di abuso relativo a Schedule IV";
            default -> "Informazioni non disponibili";
        };
    }

    // ==================== UTILITY METHODS ====================

    private void registerUser(long chatId, String username) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO users (telegram_id, username, last_active) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                             "ON CONFLICT(telegram_id) DO UPDATE SET username = excluded.username, last_active = CURRENT_TIMESTAMP")) {

            pstmt.setLong(1, chatId);
            pstmt.setString(2, username);
            pstmt.executeUpdate();

        } catch (Exception e) {
            logger.error("Error registering user: " + chatId, e);
        }
    }

    private void recordSearch(long chatId, String query) {
        try (Connection conn = dbManager.getConnection()) {
            // Inserisci nella tabella searches
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO searches (telegram_id, query_text) VALUES (?, ?)")) {
                pstmt.setLong(1, chatId);
                pstmt.setString(2, query);
                pstmt.executeUpdate();
            }

            // Incrementa il contatore nell'utente
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE users SET search_count = search_count + 1, last_active = CURRENT_TIMESTAMP WHERE telegram_id = ?")) {
                pstmt.setLong(1, chatId);
                pstmt.executeUpdate();
            }

            logger.debug("Search recorded for user {}: {}", chatId, query);

        } catch (Exception e) {
            logger.error("Error recording search", e);
        }
    }

    private int getSearchCount(long chatId) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT search_count FROM users WHERE telegram_id = ?")) {

            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("search_count");
            }

        } catch (Exception e) {
            logger.error("Error getting search count", e);
        }

        return 0;
    }

    private String formatDrugInfo(Drug drug, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<b>%d. %s</b>\n", index, escapeHtml(drug.getBrandName())));

        if (drug.getGenericName() != null && !drug.getGenericName().isEmpty()) {
            sb.append(String.format("   📋 <i>Principio attivo:</i> %s\n",
                    escapeHtml(drug.getGenericName())));
        }

        if (drug.getManufacturer() != null && !drug.getManufacturer().isEmpty()) {
            sb.append(String.format("   🏭 <i>Produttore:</i> %s\n",
                    escapeHtml(drug.getManufacturer())));
        }

        if (drug.getIndications() != null && !drug.getIndications().isEmpty()) {
            String indications = truncate(drug.getIndications(), 200);
            sb.append(String.format("   💊 <i>Indicazioni:</i> %s\n",
                    escapeHtml(indications)));
        }

        return sb.toString();
    }

    private String formatRecallInfo(Recall recall, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<b>%d. Richiamo del %s</b>\n", index,
                recall.getRecallDate() != null ? recall.getRecallDate() : "data sconosciuta"));

        if (recall.getProductDescription() != null) {
            sb.append(String.format("   📦 <i>Prodotto:</i> %s\n",
                    escapeHtml(truncate(recall.getProductDescription(), 100))));
        }

        if (recall.getReasonForRecall() != null) {
            sb.append(String.format("   ⚠️ <i>Motivo:</i> %s\n",
                    escapeHtml(truncate(recall.getReasonForRecall(), 150))));
        }

        if (recall.getClassification() != null) {
            sb.append(String.format("   🏷️ <i>Classificazione:</i> Class %s\n", recall.getClassification()));
        }

        return sb.toString();
    }

    private InlineKeyboardMarkup createRecallsKeyboard(String drugName) {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔍 Controlla richiami per questo farmaco")
                .callbackData("recalls:" + drugName)
                .build();

        InlineKeyboardRow row = new InlineKeyboardRow(button);
        return InlineKeyboardMarkup.builder()
                .keyboardRow(row)
                .build();
    }

    private void handleCallback(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();

        logger.debug("Callback: {}", callbackData);

        if (callbackData.startsWith("recalls:")) {
            String drugName = callbackData.substring(8);

            // Rispondi al callback per fermare l'animazione
            try {
                telegramClient.execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .replyMarkup(null)  // Rimuove la tastiera inline
                        .build());
            } catch (Exception e) {
                logger.warn("Could not remove keyboard: " + e.getMessage());
            }

            handleRecalls(chatId, drugName);
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending message to " + chatId, e);
        }
    }

    private void sendMessageWithKeyboard(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending message with keyboard to " + chatId, e);
        }
    }

    // Utility per escape HTML
    private String escapeHtml(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // Utility per troncare testo
    private String truncate(String text, int maxLength) {
        if (text == null || text.isEmpty()) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}