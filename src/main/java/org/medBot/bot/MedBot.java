package org.medBot.bot;

import org.medBot.MyConfiguration;
import org.medBot.dao.DatabaseManager;
import org.medBot.model.Drug;
import org.medBot.model.Recall;
import org.medBot.service.OpenFdaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Bot Telegram per informazioni su farmaci tramite API OpenFDA.
 * Implementa un menù con bottoni per migliorare l'esperienza utente.
 */
public class MedBot implements LongPollingSingleThreadUpdateConsumer {
    private static final Logger logger = LoggerFactory.getLogger(MedBot.class);
    private final TelegramClient telegramClient;
    private final OpenFdaService fdaService;
    private final DatabaseManager dbManager;

    //Messaggio di disclaimer legale per le informazioni mediche
    private static final String DISCLAIMER = "\n\n⚠️ <i>Queste informazioni sono solo a scopo informativo " +
            "e non costituiscono consulenza medica. Consulta un professionista sanitario.</i>";

    public MedBot() {
        String botToken = MyConfiguration.getInstance().getProperty("BOT_TOKEN");
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.fdaService = new OpenFdaService();
        this.dbManager = DatabaseManager.getInstance();
        logger.info("MedBot inizializzato");
    }

    @Override
    public void consume(Update update) {
        //Gestisce i messaggi di testo inviati dagli utenti
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText().trim();
            long chatId = update.getMessage().getChatId();
            String username = update.getMessage().getFrom().getUserName();

            //Registra l'utente nel database se non esiste già
            registerUser(chatId, username);
            logger.info("Messaggio da {}: {}", chatId, messageText);

            //Gestisce i comandi che iniziano con /
            if (messageText.startsWith("/")) {
                handleCommand(chatId, messageText, username);
            } else {
                sendMessageWithMenu(chatId, "❓ Comando non riconosciuto. Usa il menù qui sotto:");
            }
        }
        //Gestisce i callback dai bottoni inline
        else if (update.hasCallbackQuery()) {
            handleCallback(update);
        }
    }

    /**
     * Gestisce i comandi ricevuti dall'utente.
     */
    private void handleCommand(long chatId, String message, String username) {
        //Divide il comando dagli argomenti
        String[] parts = message.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        //Esegue l'azione corrispondente al comando
        switch (command) {
            case "/start" -> handleStart(chatId, username);
            case "/help" -> handleHelp(chatId);
            case "/cerca" -> handleSearchDrug(chatId, args);
            case "/richiami" -> handleRecalls(chatId, args);
            case "/mystats" -> handleMyStats(chatId);
            case "/recenti" -> handleRecentSearches(chatId);
            case "/farmacolegale" -> handleControlledSubstance(chatId, args);
            case "/effetticollaterali" -> handleAdverseEvents(chatId, args);
            case "/interazioni" -> handleDrugInteractions(chatId, args);
            case "/bookmarks" -> handleBookmarks(chatId, args);
            default -> sendMessageWithMenu(chatId, "❓ Comando sconosciuto. Usa /help o il menù:");
        }
    }

    /**
     * Mostra il messaggio di benvenuto con il menù principale.
     */
    private void handleStart(long chatId, String username) {
        String welcome = String.format(
                "👋 Benvenuto <b>%s</b> su MedBot!\n\n" +
                        "🔬 Bot per informazioni su farmaci usando le API della FDA americana.\n\n" +
                        "🇺🇸 I dati sono in <b>inglese</b>, cerca i farmaci con nomi inglesi " +
                        "(es. 'aspirin' invece di 'aspirina').\n\n" +
                        "📖 Usa /help o il menù qui sotto per iniziare!",
                username != null ? username : "utente");

        sendMessageWithMenu(chatId, welcome + DISCLAIMER);
    }

    /**
     * Mostra la lista completa dei comandi disponibili.
     */
    private void handleHelp(long chatId) {
        String help = "<b>📋 Comandi Disponibili</b>\n\n" +
                "/start - Messaggio di benvenuto\n" +
                "/help - Mostra questa guida\n\n" +
                "<b>🔍 Ricerca Farmaci</b>\n" +
                "/cerca &lt;nome&gt; - Cerca un farmaco\n" +
                "Esempio: <code>/cerca aspirin</code>\n\n" +
                "<b>⚠️ Sicurezza</b>\n" +
                "/richiami &lt;nome|all&gt; - Controlla richiami FDA\n" +
                "/farmacolegale &lt;nome&gt; - Verifica se è sostanza controllata\n" +
                "/effetticollaterali &lt;nome&gt; - Effetti collaterali segnalati\n" +
                "/interazioni &lt;farmaco1 + farmaco2&gt; - Verifica interazioni tra farmaci\n\n" +
                "<b>📊 Statistiche</b>\n" +
                "/mystats - Le tue statistiche\n" +
                "/recenti - Farmaci cercati di recente\n" +
                "/bookmarks - Gestisci i preferiti\n\n" +
                "<b>💡 Esempi:</b>\n" +
                "• <code>/cerca ibuprofen</code>\n" +
                "• <code>/richiami aspirin</code>\n" +
                "• <code>/interazioni aspirin + ibuprofen</code>\n" +
                "• <code>/bookmarks add aspirin</code>";

        sendMessage(chatId, help);
    }

    /**
     * Cerca informazioni su un farmaco specifico.
     */
    private void handleSearchDrug(long chatId, String drugName) {
        handleSearchDrug(chatId, drugName, 0);
    }

    private void handleSearchDrug(long chatId, String drugName, int offset) {
        //Verifica che sia stato specificato un nome
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Specifica il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/cerca aspirin</code>");
            return;
        }

        //Mostra messaggio di caricamento solo alla prima pagina
        if (offset == 0) {
            sendMessage(chatId, "🔍 Cerco \"" + drugName + "\"...");
            recordSearch(chatId, drugName);
        }

        try {
            List<Drug> drugs = fdaService.searchDrug(drugName);

            if (drugs.isEmpty()) {
                sendMessage(chatId, "❌ Nessun risultato per \"" + drugName + "\".\n\n" +
                        "💡 Prova con il nome generico o in inglese.");
                return;
            }

            //Implementa la paginazione dei risultati (3 risultati per pagina)
            int pageSize = 3;
            int end = Math.min(offset + pageSize, drugs.size());

            StringBuilder response = new StringBuilder();
            response.append(String.format("✅ <b>%d risultati</b> per \"%s\":\n\n",
                    drugs.size(), drugName));

            //Formatta ogni farmaco trovato
            for (int i = offset; i < end; i++) {
                response.append(formatDrugInfo(drugs.get(i), i + 1));
                if (i < end - 1) response.append("\n➖➖➖\n\n");
            }

            //Crea i bottoni per navigazione e azioni
            List<InlineKeyboardRow> rows = new ArrayList<>();

            //Bottone per vedere altri risultati se disponibili
            if (end < drugs.size()) {
                InlineKeyboardButton moreButton = InlineKeyboardButton.builder()
                        .text(String.format("⬇️ Altri %d risultati", drugs.size() - end))
                        .callbackData("moredrugs:" + drugName + ":" + end)
                        .build();
                rows.add(new InlineKeyboardRow(moreButton));
            }

            //Bottoni per azioni sul farmaco
            InlineKeyboardRow actionsRow = new InlineKeyboardRow();
            actionsRow.add(InlineKeyboardButton.builder()
                    .text("🔍 Richiami")
                    .callbackData("recalls:" + drugName)
                    .build());
            actionsRow.add(InlineKeyboardButton.builder()
                    .text("⭐ Salva")
                    .callbackData("bookmark:" + drugName)
                    .build());
            rows.add(actionsRow);

            InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                    .keyboard(rows)
                    .build();

            sendMessageWithKeyboard(chatId, response.toString(), keyboard);

        } catch (Exception e) {
            logger.error("Errore ricerca farmaco: " + drugName, e);
            sendMessage(chatId, "❌ Errore durante la ricerca. Riprova più tardi.");
        }
    }

    /**
     * Cerca richiami FDA per un farmaco specifico.
     */
    private void handleRecalls(long chatId, String drugName) {
        handleRecalls(chatId, drugName, 0);
    }

    private void handleRecalls(long chatId, String drugName, int offset) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Specifica il nome del farmaco o 'all'.\n\n" +
                    "📝 Esempio: <code>/richiami aspirin</code>");
            return;
        }

        if (offset == 0) {
            sendMessage(chatId, "🔍 Cerco richiami...");
        }

        try {
            //Ottiene i richiami dal servizio FDA
            List<Recall> recalls = drugName.equalsIgnoreCase("all")
                    ? fdaService.getRecentRecalls(50)
                    : fdaService.searchRecalls(drugName);

            if (recalls.isEmpty()) {
                sendMessage(chatId, "✅ Nessun richiamo trovato per \"" + drugName + "\".\n\n" +
                        "🎉 Buone notizie!");
                return;
            }

            //Implementa paginazione (5 richiami per pagina)
            int pageSize = 5;
            int end = Math.min(offset + pageSize, recalls.size());

            StringBuilder response = new StringBuilder();
            response.append(String.format("⚠️ <b>%d Richiami FDA</b>\n\n", recalls.size()));

            //Spiega le classificazioni solo alla prima pagina
            if (offset == 0) {
                response.append("<i>Classificazione FDA:</i>\n" +
                        "• <b>Class I</b>: Rischio grave\n" +
                        "• <b>Class II</b>: Rischio temporaneo\n" +
                        "• <b>Class III</b>: Rischio minimo\n\n");
            }

            //Formatta ogni richiamo
            for (int i = offset; i < end; i++) {
                response.append(formatRecallInfo(recalls.get(i), i + 1));
                if (i < end - 1) response.append("\n➖➖➖\n\n");
            }

            //Bottone per vedere altri richiami
            if (end < recalls.size()) {
                InlineKeyboardButton button = InlineKeyboardButton.builder()
                        .text(String.format("📋 Altri %d richiami", recalls.size() - end))
                        .callbackData("morerecalls:" + drugName + ":" + end)
                        .build();

                InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(button))
                        .build();

                sendMessageWithKeyboard(chatId, response.toString(), keyboard);
            } else {
                sendMessage(chatId, response.toString());
            }

        } catch (Exception e) {
            logger.error("Errore ricerca richiami: " + drugName, e);
            sendMessage(chatId, "❌ Errore durante la ricerca richiami.");
        }
    }

    /**
     * Mostra le statistiche personali dell'utente.
     */
    private void handleMyStats(long chatId) {
        try {
            int searchCount = getSearchCount(chatId);

            //Ottiene i farmaci più cercati dall'utente
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

            response += "💡 Usa /recenti per le ultime ricerche!";
            sendMessage(chatId, response);

        } catch (Exception e) {
            logger.error("Errore statistiche utente: " + chatId, e);
            sendMessage(chatId, "❌ Errore nel recuperare le statistiche.");
        }
    }

    /**
     * Mostra le ricerche recenti dell'utente.
     */
    private void handleRecentSearches(long chatId) {
        try {
            StringBuilder recent = new StringBuilder();
            recent.append("📜 <b>Ricerche recenti:</b>\n\n");

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "SELECT DISTINCT query_text, MAX(created_at) as last_search " +
                                 "FROM searches WHERE telegram_id = ? " +
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
                    sendMessage(chatId, "📜 Nessuna ricerca effettuata.\n\nProva: /cerca aspirin");
                    return;
                }
            }

            recent.append("\n💡 Per cercare usa: /cerca &lt;nome&gt;");
            sendMessage(chatId, recent.toString());

        } catch (Exception e) {
            logger.error("Errore ricerche recenti", e);
            sendMessage(chatId, "❌ Errore nel recuperare le ricerche.");
        }
    }

    /**
     * Verifica se un farmaco è una sostanza controllata.
     */
    private void handleControlledSubstance(long chatId, String drugName) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Specifica il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/farmacolegale oxycodone</code>\n\n" +
                    "💡 Verifica se è una sostanza controllata (rischio dipendenza).");
            return;
        }

        sendMessage(chatId, "🔍 Verifico lo status di \"" + drugName + "\"...");

        try {
            String schedule = fdaService.checkDrugSchedule(drugName);

            if (schedule == null) {
                sendMessage(chatId, "✅ \"" + drugName + "\" NON è una sostanza controllata.\n\n" +
                        "Basso rischio di abuso/dipendenza.");
            } else {
                String emoji = getScheduleEmoji(schedule);
                String description = getScheduleDescription(schedule);

                sendMessage(chatId, String.format(
                        "🚨 <b>SOSTANZA CONTROLLATA</b>\n\n" +
                                "Farmaco: <b>%s</b>\n" +
                                "Classificazione: %s <b>Schedule %s</b>\n\n" +
                                "📋 %s\n\n" +
                                "⚠️ Richiede prescrizione speciale.",
                        drugName, emoji, schedule, description));
            }

        } catch (Exception e) {
            logger.error("Errore verifica sostanza: " + drugName, e);
            sendMessage(chatId, "❌ Errore durante la verifica.");
        }
    }

    /**
     * Restituisce l'emoji appropriata per la classificazione DEA.
     */
    private String getScheduleEmoji(String schedule) {
        return switch (schedule) {
            case "I", "II" -> "🔴";
            case "III" -> "🟠";
            case "IV" -> "🟡";
            case "V" -> "🟢";
            default -> "⚪";
        };
    }

    /**
     * Restituisce la descrizione per la classificazione DEA.
     */
    private String getScheduleDescription(String schedule) {
        return switch (schedule) {
            case "I" -> "Alto potenziale di abuso, nessun uso medico negli USA";
            case "II" -> "Alto potenziale di abuso, rischio grave dipendenza";
            case "III" -> "Potenziale di abuso moderato";
            case "IV" -> "Basso potenziale di abuso";
            case "V" -> "Potenziale di abuso molto basso";
            default -> "Informazioni non disponibili";
        };
    }

    /**
     * Mostra gli effetti collaterali segnalati per un farmaco.
     */
    private void handleAdverseEvents(long chatId, String drugName) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Specifica il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/effetticollaterali aspirin</code>");
            return;
        }

        sendMessage(chatId, "🔍 Cerco effetti collaterali per \"" + drugName + "\"...");

        try {
            var events = fdaService.getAdverseEvents(drugName);

            if (events.isEmpty()) {
                sendMessage(chatId, "✅ Nessun effetto collaterale recente registrato.");
                return;
            }

            StringBuilder response = new StringBuilder();
            response.append(String.format("⚠️ <b>Effetti Collaterali - %s</b>\n\n", drugName));

            int total = ((Number) events.get("total")).intValue();
            response.append(String.format("📊 Segnalazioni: <b>%d</b>\n\n", total));

            //Mostra le reazioni più comuni
            @SuppressWarnings("unchecked")
            var reactions = (java.util.Map<String, Integer>) events.get("topReactions");
            if (reactions != null && !reactions.isEmpty()) {
                response.append("<b>🔴 Effetti più segnalati:</b>\n");
                int count = 0;
                for (var entry : reactions.entrySet()) {
                    if (count >= 10) break;
                    response.append(String.format("• %s (%d)\n", entry.getKey(), entry.getValue()));
                    count++;
                }
                response.append("\n");
            }

            response.append("<i>Nota: Consulta sempre un medico per informazioni complete.</i>");
            sendMessage(chatId, response.toString());

        } catch (Exception e) {
            logger.error("Errore effetti collaterali: " + drugName, e);
            sendMessage(chatId, "❌ Errore durante la ricerca.");
        }
    }

    /**
     * Comando NUOVO: Verifica interazioni tra due o più farmaci.
     * Sostituisce il comando /informazioni che non era affidabile.
     */
    private void handleDrugInteractions(long chatId, String args) {
        if (args.isEmpty()) {
            sendMessage(chatId, "❌ Specifica i farmaci da confrontare!\n\n" +
                    "📝 Esempio: <code>/interazioni aspirin + ibuprofen</code>\n\n" +
                    "💡 <b>Cosa fa:</b>\n" +
                    "Verifica se due o più farmaci hanno segnalazioni di interazioni " +
                    "negative quando usati insieme.\n\n" +
                    "<b>Altri esempi:</b>\n" +
                    "• <code>/interazioni warfarin + aspirin</code>\n" +
                    "• <code>/interazioni omeprazole + clopidogrel</code>");
            return;
        }

        //Divide i farmaci usando il separatore +
        String[] drugs = args.split("\\+");
        if (drugs.length < 2) {
            sendMessage(chatId, "❌ Specifica almeno due farmaci separati da +\n\n" +
                    "Esempio: <code>/interazioni aspirin + ibuprofen</code>");
            return;
        }

        //Pulisce i nomi dei farmaci
        for (int i = 0; i < drugs.length; i++) {
            drugs[i] = drugs[i].trim();
        }

        sendMessage(chatId, "🔍 Verifico interazioni tra: " + String.join(", ", drugs) + "...");

        try {
            //Cerca segnalazioni di eventi avversi che coinvolgono tutti i farmaci
            var interactions = fdaService.checkDrugInteractions(drugs);

            if (interactions.isEmpty() || ((Number) interactions.get("count")).intValue() == 0) {
                sendMessage(chatId, "✅ <b>Nessuna interazione grave segnalata</b>\n\n" +
                        "Non ci sono segnalazioni recenti di eventi avversi gravi " +
                        "quando questi farmaci vengono usati insieme.\n\n" +
                        "⚠️ <i>Importante: Questo non significa che non possano esserci interazioni. " +
                        "Consulta sempre un medico o farmacista.</i>");
                return;
            }

            int count = ((Number) interactions.get("count")).intValue();
            StringBuilder response = new StringBuilder();
            response.append("⚠️ <b>POSSIBILI INTERAZIONI</b>\n\n");
            response.append("Farmaci: ").append(String.join(" + ", drugs)).append("\n\n");
            response.append(String.format("📊 <b>%d segnalazioni</b> di eventi avversi " +
                    "quando questi farmaci sono usati insieme.\n\n", count));

            //Mostra le reazioni più comuni nelle interazioni
            @SuppressWarnings("unchecked")
            var commonReactions = (java.util.List<String>) interactions.get("commonReactions");
            if (commonReactions != null && !commonReactions.isEmpty()) {
                response.append("<b>🔴 Reazioni più comuni:</b>\n");
                for (int i = 0; i < Math.min(8, commonReactions.size()); i++) {
                    response.append("• ").append(commonReactions.get(i)).append("\n");
                }
                response.append("\n");
            }

            response.append("🚨 <b>IMPORTANTE:</b>\n");
            response.append("• NON interrompere i farmaci senza consultare un medico\n");
            response.append("• Queste sono segnalazioni, non certezze scientifiche\n");
            response.append("• Consulta un medico o farmacista per informazioni accurate\n\n");
            response.append("<i>Fonte: FDA Adverse Event Reporting System (FAERS)</i>");

            sendMessage(chatId, response.toString());

        } catch (Exception e) {
            logger.error("Errore verifica interazioni: " + args, e);
            sendMessage(chatId, "❌ Errore durante la verifica delle interazioni.");
        }
    }

    /**
     * Gestisce i bookmark (preferiti) dell'utente.
     */
    private void handleBookmarks(long chatId, String args) {
        String[] parts = args.split("\\s+", 2);
        String action = parts.length > 0 ? parts[0].toLowerCase() : "";
        String drugName = parts.length > 1 ? parts[1].trim() : "";

        try {
            switch (action) {
                case "add" -> {
                    if (drugName.isEmpty()) {
                        sendMessage(chatId, "❌ Specifica il farmaco!\n\nEsempio: <code>/bookmarks add aspirin</code>");
                        return;
                    }
                    addBookmark(chatId, drugName);
                    sendMessage(chatId, "⭐ Farmaco \"" + drugName + "\" aggiunto ai preferiti!");
                }
                case "remove" -> {
                    if (drugName.isEmpty()) {
                        sendMessage(chatId, "❌ Specifica il farmaco!\n\nEsempio: <code>/bookmarks remove aspirin</code>");
                        return;
                    }
                    removeBookmark(chatId, drugName);
                    sendMessage(chatId, "🗑️ Farmaco \"" + drugName + "\" rimosso dai preferiti.");
                }
                case "list", "" -> {
                    //Mostra la lista dei bookmark
                    List<String> bookmarks = getBookmarks(chatId);
                    if (bookmarks.isEmpty()) {
                        sendMessage(chatId, "📌 Nessun preferito salvato.\n\n" +
                                "Usa: <code>/bookmarks add &lt;farmaco&gt;</code>");
                    } else {
                        StringBuilder response = new StringBuilder("⭐ <b>I tuoi farmaci preferiti:</b>\n\n");
                        for (String bookmark : bookmarks) {
                            response.append("• ").append(bookmark).append("\n");
                        }
                        response.append("\n💡 Per cercare: /cerca &lt;nome&gt;\n");
                        response.append("🗑️ Per rimuovere: <code>/bookmarks remove &lt;nome&gt;</code>");
                        sendMessage(chatId, response.toString());
                    }
                }
                default -> sendMessage(chatId, "❌ Azione non valida.\n\n" +
                        "<b>Azioni disponibili:</b>\n" +
                        "• <code>/bookmarks</code> - lista preferiti\n" +
                        "• <code>/bookmarks add &lt;farmaco&gt;</code>\n" +
                        "• <code>/bookmarks remove &lt;farmaco&gt;</code>");
            }
        } catch (Exception e) {
            logger.error("Errore gestione bookmarks", e);
            sendMessage(chatId, "❌ Errore nella gestione dei preferiti.");
        }
    }

    /**
     * Aggiunge un farmaco ai preferiti dell'utente.
     */
    private void addBookmark(long chatId, String drugName) throws Exception {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT OR IGNORE INTO bookmarks (telegram_id, drug_name) VALUES (?, ?)")) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, drugName);
            pstmt.executeUpdate();
        }
    }

    /**
     * Rimuove un farmaco dai preferiti dell'utente.
     */
    private void removeBookmark(long chatId, String drugName) throws Exception {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM bookmarks WHERE telegram_id = ? AND drug_name = ?")) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, drugName);
            pstmt.executeUpdate();
        }
    }

    /**
     * Ottiene la lista dei preferiti dell'utente.
     */
    private List<String> getBookmarks(long chatId) throws Exception {
        List<String> bookmarks = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT drug_name FROM bookmarks WHERE telegram_id = ? ORDER BY created_at DESC")) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                bookmarks.add(rs.getString("drug_name"));
            }
        }
        return bookmarks;
    }

    /**
     * Gestisce i callback dai bottoni inline.
     */
    private void handleCallback(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();

        logger.debug("Callback: {}", callbackData);

        //Rimuove la tastiera inline dal messaggio precedente
        try {
            telegramClient.execute(
                    org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup.builder()
                            .chatId(chatId)
                            .messageId(messageId)
                            .replyMarkup(null)
                            .build());
        } catch (Exception e) {
            logger.warn("Impossibile rimuovere tastiera: " + e.getMessage());
        }

        //Gestisce i diversi tipi di callback
        if (callbackData.startsWith("recalls:")) {
            String drugName = callbackData.substring(8);
            handleRecalls(chatId, drugName);
        } else if (callbackData.startsWith("morerecalls:")) {
            String[] parts = callbackData.split(":", 3);
            handleRecalls(chatId, parts[1], Integer.parseInt(parts[2]));
        } else if (callbackData.startsWith("moredrugs:")) {
            String[] parts = callbackData.split(":", 3);
            handleSearchDrug(chatId, parts[1], Integer.parseInt(parts[2]));
        } else if (callbackData.startsWith("bookmark:")) {
            String drugName = callbackData.substring(9);
            try {
                addBookmark(chatId, drugName);
                sendMessage(chatId, "⭐ Farmaco salvato nei preferiti!");
            } catch (Exception e) {
                logger.error("Errore salvataggio bookmark", e);
                sendMessage(chatId, "❌ Errore nel salvare il preferito.");
            }
        }
    }

    // ==================== UTILITY METHODS ====================

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
            logger.error("Errore registrazione utente: " + chatId, e);
        }
    }

    /**
     * Registra una ricerca effettuata dall'utente.
     */
    private void recordSearch(long chatId, String query) {
        try (Connection conn = dbManager.getConnection()) {
            //Inserisce nella tabella searches
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO searches (telegram_id, query_text) VALUES (?, ?)")) {
                pstmt.setLong(1, chatId);
                pstmt.setString(2, query);
                pstmt.executeUpdate();
            }

            //Incrementa il contatore nell'utente
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE users SET search_count = search_count + 1, last_active = CURRENT_TIMESTAMP WHERE telegram_id = ?")) {
                pstmt.setLong(1, chatId);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            logger.error("Errore registrazione ricerca", e);
        }
    }

    /**
     * Ottiene il numero totale di ricerche dell'utente.
     */
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
            logger.error("Errore conteggio ricerche", e);
        }
        return 0;
    }

    /**
     * Formatta le informazioni di un farmaco per la visualizzazione.
     */
    private String formatDrugInfo(Drug drug, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<b>%d. %s</b>\n", index, drug.getBrandName()));

        if (drug.getGenericName() != null && !drug.getGenericName().isEmpty()) {
            sb.append(String.format("   📋 <i>Principio attivo:</i> %s\n", drug.getGenericName()));
        }

        if (drug.getManufacturer() != null && !drug.getManufacturer().isEmpty()) {
            sb.append(String.format("   🏭 <i>Produttore:</i> %s\n", drug.getManufacturer()));
        }

        if (drug.getIndications() != null && !drug.getIndications().isEmpty()) {
            String indications = formatIndications(drug.getIndications());
            sb.append("   💊 <i>Indicazioni:</i>\n");
            sb.append(indications);
        }

        return sb.toString();
    }

    /**
     * Formatta le indicazioni terapeutiche in modo leggibile.
     */
    private String formatIndications(String rawIndications) {
        String text = rawIndications.replaceAll("\\s+", " ").trim();
        String[] sentences = text.split("(?<=[.!?])\\s+");

        StringBuilder formatted = new StringBuilder();
        int charCount = 0;
        int maxChars = 300;

        for (String sentence : sentences) {
            if (charCount + sentence.length() > maxChars) {
                formatted.append("   ...\n");
                break;
            }
            formatted.append("   • ").append(sentence.trim()).append("\n");
            charCount += sentence.length();
        }

        return formatted.toString();
    }

    /**
     * Formatta le informazioni di un richiamo FDA.
     */
    private String formatRecallInfo(Recall recall, int index) {
        StringBuilder sb = new StringBuilder();

        //Formatta la data da formato YYYYMMDD a YYYY-MM-DD
        String dateStr = recall.getRecallDate();
        if (dateStr != null && dateStr.length() == 8) {
            dateStr = dateStr.substring(0, 4) + "-" +
                    dateStr.substring(4, 6) + "-" +
                    dateStr.substring(6, 8);
        }

        sb.append(String.format("<b>%d. Richiamo del %s</b>\n", index, dateStr != null ? dateStr : "N/A"));

        if (recall.getProductDescription() != null) {
            sb.append(String.format("   📦 <i>Prodotto:</i>\n   %s\n\n", recall.getProductDescription()));
        }

        if (recall.getReasonForRecall() != null) {
            sb.append(String.format("   ⚠️ <i>Motivo:</i>\n   %s\n\n", recall.getReasonForRecall()));
        }

        if (recall.getClassification() != null) {
            String emoji = getClassificationEmoji(recall.getClassification());
            sb.append(String.format("   %s <i>Classificazione:</i> <b>Class %s</b>\n",
                    emoji, recall.getClassification()));
        }

        return sb.toString();
    }

    /**
     * Restituisce l'emoji per la classificazione del richiamo.
     */
    private String getClassificationEmoji(String classification) {
        return switch (classification.toUpperCase()) {
            case "I", "CLASS I" -> "🔴";
            case "II", "CLASS II" -> "🟠";
            case "III", "CLASS III" -> "🟡";
            default -> "⚪";
        };
    }

    /**
     * Invia un messaggio di testo semplice.
     */
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
            logger.error("Errore invio messaggio a " + chatId, e);
        }
    }

    /**
     * Invia un messaggio con il menù principale (tastiera permanente).
     */
    private void sendMessageWithMenu(long chatId, String text) {
        //Crea la tastiera permanente con i comandi principali
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("/cerca"));
        row1.add(new KeyboardButton("/richiami"));
        row1.add(new KeyboardButton("/interazioni"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("/mystats"));
        row2.add(new KeyboardButton("/bookmarks"));
        row2.add(new KeyboardButton("/help"));

        ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2))
                .resizeKeyboard(true)
                .persistent(true)
                .build();

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
            logger.error("Errore invio messaggio con menù a " + chatId, e);
        }
    }

    /**
     * Invia un messaggio con tastiera inline (bottoni sotto il messaggio).
     */
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
            logger.error("Errore invio messaggio con tastiera inline a " + chatId, e);
        }
    }
}