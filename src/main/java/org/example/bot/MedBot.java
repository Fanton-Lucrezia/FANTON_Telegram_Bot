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
import java.util.ArrayList;
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

    private static final String DISCLAIMER = "\n\n⚠️ <i>Disclaimer: Queste informazioni sono solo a scopo informativo "
            +
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
            case "/farmacolegale" -> handleControlledSubstance(chatId, args);
            case "/effetticollaterali" -> handleAdverseEvents(chatId, args);
            case "/informazioni" -> handleHealthTopic(chatId, args);
            default -> sendMessage(chatId, "❓ Comando sconosciuto. Usa /help per la lista dei comandi.");
        }
    }

    private void handleStart(long chatId, String username) {
        String welcome = String.format(
                "👋 Benvenuto <b>%s</b> su OpenFDA MedBot!\n\n" +
                        "🔬 Questo bot utilizza l'<b>API pubblica della FDA</b> (Food and Drug Administration americana) "
                        +
                        "per fornirti informazioni su farmaci e richiami.\n\n" +
                        "🇺🇸 <b>Nota importante:</b> I dati provengono da un database americano, quindi " +
                        "i risultati sono in <b>inglese</b>. Per migliori risultati, cerca i farmaci con il loro " +
                        "nome inglese (es. 'aspirin' invece di 'aspirina').\n\n" +
                        "📖 Usa /help per vedere tutti i comandi disponibili.",
                username != null ? username : "utente");
        sendMessage(chatId, welcome + DISCLAIMER);
    }

    private void handleHelp(long chatId) {
        String help = "<b>📋 Lista Comandi</b>\n\n" +
                "/start - Messaggio di benvenuto\n" +
                "/help - Mostra questa lista\n\n" +
                "<b>🔍 Ricerca Farmaci</b>\n" +
                "/cerca &lt;nome&gt;\n" +
                "Cerca informazioni dettagliate su un farmaco.\n" +
                "Esempio: /cerca aspirin\n\n" +
                "<b>⚠️ Sicurezza e Legalità</b>\n" +
                "/richiami &lt;nome|all&gt;\n" +
                "Verifica richiami FDA per un farmaco.\n" +
                "Esempi:\n" +
                "• /richiami aspirin\n" +
                "• /richiami all - ultimi richiami\n\n" +
                "/farmacolegale &lt;nome&gt;\n" +
                "Verifica se un farmaco è una sostanza controllata " +
                "(richiede prescrizione speciale per rischio dipendenza).\n" +
                "Esempio: /farmacolegale oxycodone\n\n" +
                "/effetticollaterali &lt;nome&gt;\n" +
                "Mostra effetti collaterali segnalati dagli utenti.\n" +
                "Esempio: /effetticollaterali aspirin\n\n" +
                "<b>💡 Informazioni Salute</b>\n" +
                "/informazioni &lt;argomento&gt;\n" +
                "Consigli su prevenzione e salute in generale.\n" +
                "Esempi:\n" +
                "• /informazioni diabetes (diabete)\n" +
                "• /informazioni flu (influenza)\n" +
                "• /informazioni heart (cuore)\n" +
                "• /informazioni nutrition (nutrizione)\n\n" +
                "<b>📊 Le Tue Informazioni</b>\n" +
                "/mystats - Statistiche personali\n" +
                "/recenti - Farmaci cercati di recente\n\n" +
                "💡 <b>Suggerimento:</b> Usa nomi in <b>inglese</b> " +
                "per risultati migliori!\n" +
                "Esempi: aspirin, ibuprofen, diabetes, flu";

        sendMessage(chatId, help);
    }

    private void handleSearchDrug(long chatId, String drugName) {
        handleSearchDrug(chatId, drugName, 0);
    }

    private void handleSearchDrug(long chatId, String drugName, int offset) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Devi specificare il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/cerca aspirina</code>");
            return;
        }

        if (offset == 0) {
            sendMessage(chatId, "🔍 Cerco \"" + drugName + "\"...");
            // Registra la ricerca solo la prima volta
            recordSearch(chatId, drugName);
        }

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

            // Paginazione
            int pageSize = 3;
            int start = offset;
            int end = Math.min(start + pageSize, drugs.size());

            StringBuilder response = new StringBuilder();

            if (offset == 0) {
                response.append(String.format("✅ <b>%d risultati</b> per \"%s\":\n\n",
                        drugs.size(), escapeHtml(drugName)));
            } else {
                response.append(String.format("✅ <b>Risultati %d-%d di %d</b> per \"%s\":\n\n",
                        start + 1, end, drugs.size(), escapeHtml(drugName)));
            }

            for (int i = start; i < end; i++) {
                Drug drug = drugs.get(i);
                response.append(formatDrugInfo(drug, i + 1));
                if (i < end - 1)
                    response.append("\n➖➖➖\n\n");
            }

            // Costruisci la tastiera
            List<InlineKeyboardRow> rows = new ArrayList<>();

            // Bottone per altri risultati se ce ne sono ancora
            if (end < drugs.size()) {
                int remaining = drugs.size() - end;
                InlineKeyboardButton moreButton = InlineKeyboardButton.builder()
                        .text(String.format("⬇️ Altri %d risultati", remaining))
                        .callbackData("moredrugs:" + drugName + ":" + end)
                        .build();
                rows.add(new InlineKeyboardRow(moreButton));
            }

            // Bottone per richiami (sempre presente)
            InlineKeyboardButton recallsButton = InlineKeyboardButton.builder()
                    .text("🔍 Controlla richiami")
                    .callbackData("recalls:" + drugName)
                    .build();
            rows.add(new InlineKeyboardRow(recallsButton));

            InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                    .keyboard(rows)
                    .build();

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
        handleRecalls(chatId, drugName, 0);
    }

    private void handleRecalls(long chatId, String drugName, int offset) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Specifica il nome del farmaco o scrivi 'all'.\n\n" +
                    "📝 Esempio: <code>/richiami aspirina</code>\n" +
                    "📝 Oppure: <code>/richiami all</code>");
            return;
        }

        if (offset == 0) {
            sendMessage(chatId, "🔍 Cerco richiami...");
        }

        try {
            List<Recall> recalls = drugName.equalsIgnoreCase("all")
                    ? fdaService.getRecentRecalls(50)
                    : fdaService.searchRecalls(drugName);

            if (recalls.isEmpty()) {
                sendMessage(chatId,
                        "✅ <b>Nessun richiamo trovato</b> per \"" + escapeHtml(drugName) + "\".\n\n" +
                                "🎉 Buone notizie! Non ci sono richiami recenti per questo farmaco.");
                return;
            }

            // Mostra richiami con paginazione
            int pageSize = 5;
            int start = offset;
            int end = Math.min(start + pageSize, recalls.size());

            StringBuilder response = new StringBuilder();

            if (offset == 0) {
                response.append(String.format("⚠️ <b>%d Richiami FDA</b> trovati", recalls.size()));
                if (!drugName.equalsIgnoreCase("all")) {
                    response.append(" per \"").append(escapeHtml(drugName)).append("\"");
                }
                response.append(":\n\n");

                // Spiegazione classificazione solo la prima volta
                response.append("<i>ℹ️ Classificazione FDA:</i>\n");
                response.append("• <b>Class I</b>: Rischio grave per salute/morte\n");
                response.append("• <b>Class II</b>: Rischio temporaneo per salute\n");
                response.append("• <b>Class III</b>: Improbabile danno alla salute\n\n");
            } else {
                response.append(String.format("⚠️ <b>Richiami %d-%d di %d:</b>\n\n",
                        start + 1, end, recalls.size()));
            }

            for (int i = start; i < end; i++) {
                Recall recall = recalls.get(i);
                response.append(formatRecallInfo(recall, i + 1));
                if (i < end - 1)
                    response.append("\n➖➖➖\n\n");
            }

            // Bottone per mostrare altri richiami
            if (end < recalls.size()) {
                int remaining = recalls.size() - end;
                InlineKeyboardButton button = InlineKeyboardButton.builder()
                        .text(String.format("📋 Mostra altri %d richiami", remaining))
                        .callbackData("morerecalls:" + drugName + ":" + end)
                        .build();

                InlineKeyboardRow row = new InlineKeyboardRow(button);
                InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(row)
                        .build();

                sendMessageWithKeyboard(chatId, response.toString(), keyboard);
            } else {
                sendMessage(chatId, response.toString());
            }

        } catch (Exception e) {
            logger.error("Errore ricerca richiami: " + drugName, e);

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
                    sendMessage(chatId, "📜 Non hai ancora fatto ricerche.\n\nProva con: /cerca aspirin");
                    return;
                }
            }

            recent.append("\n💡 Per cercare di nuovo un farmaco usa: /cerca &lt;nome&gt;");

            sendMessage(chatId, recent.toString());

        } catch (Exception e) {
            logger.error("Errore recupero ricerche recenti", e);
            sendMessage(chatId, "❌ Errore nel recuperare le ricerche recenti.");
        }
    }

    private void handleControlledSubstance(long chatId, String drugName) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Devi specificare il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/farmacolegale oxycodone</code>\n\n" +
                    "💡 <b>Cosa fa questo comando:</b>\n" +
                    "Verifica se un farmaco è classificato come <b>sostanza controllata</b> " +
                    "(farmaci con rischio di abuso/dipendenza che richiedono prescrizioni speciali).\n\n" +
                    "<i>Nota: Cerca solo farmaci prescritti legalmente, non droghe illegali.</i>");
            return;
        }

        sendMessage(chatId, "🔍 Verifico lo status legale di \"" + drugName + "\"...");

        try {
            String schedule = fdaService.checkDrugSchedule(drugName);

            if (schedule == null) {
                sendMessage(chatId,
                        "✅ <b>\"" + escapeHtml(drugName) + "\" NON è una sostanza controllata</b>\n\n" +
                                "Questo farmaco non risulta classificato come sostanza controllata secondo la " +
                                "DEA (Drug Enforcement Administration).\n\n" +
                                "💡 <b>Cosa significa:</b>\n" +
                                "• Può essere prescritto normalmente\n" +
                                "• Non richiede prescrizioni speciali\n" +
                                "• Basso rischio di abuso/dipendenza");
            } else {
                String emoji = getScheduleEmoji(schedule);
                String description = getScheduleDescription(schedule);

                sendMessage(chatId, String.format(
                        "🚨 <b>SOSTANZA CONTROLLATA</b>\n\n" +
                                "Farmaco: <b>%s</b>\n" +
                                "Classificazione DEA: %s <b>Schedule %s</b>\n\n" +
                                "📋 <b>Descrizione:</b>\n" +
                                "<i>%s</i>\n\n" +
                                "⚠️ <b>Cosa comporta:</b>\n" +
                                "• Prescrizione medica speciale obbligatoria\n" +
                                "• Controlli rigorosi su produzione e distribuzione\n" +
                                "• Limiti sulla quantità prescrivibile\n" +
                                "• Tracciamento governativo della distribuzione\n" +
                                "• Rischio di dipendenza fisica o psicologica",
                        escapeHtml(drugName), emoji, schedule, description));
            }

        } catch (Exception e) {
            logger.error("Errore verifica sostanza controllata: " + drugName, e);

            if (e.getMessage().contains("404")) {
                sendMessage(chatId,
                        "❌ <b>Farmaco non trovato nel database FDA</b>\n\n" +
                                "\"" + escapeHtml(drugName) + "\" non è presente nel database.\n\n" +
                                "💡 <b>Possibili motivi:</b>\n" +
                                "• Non è un farmaco approvato dalla FDA\n" +
                                "• È una droga illegale (non un farmaco prescrivibile)\n" +
                                "• Nome scritto in modo errato\n\n" +
                                "📝 <b>Esempi di farmaci controllati che funzionano:</b>\n" +
                                "• oxycodone (antidolorifico oppioide)\n" +
                                "• alprazolam o xanax (ansiolitico)\n" +
                                "• tramadol (antidolorifico)\n" +
                                "• codeine (antitussivo oppioide)");
            } else {
                sendMessage(chatId, "❌ Errore durante la verifica. Riprova più tardi.");
            }
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

    private void handleAdverseEvents(long chatId, String drugName) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Devi specificare il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/effetticollaterali aspirin</code>\n\n" +
                    "💡 <b>Cosa fa questo comando:</b>\n" +
                    "Mostra gli effetti collaterali più comuni segnalati dagli utenti " +
                    "nel database FDA (segnalazioni spontanee di eventi avversi).");
            return;
        }

        sendMessage(chatId, "🔍 Cerco effetti collaterali per \"" + drugName + "\"...");

        try {
            var events = fdaService.getAdverseEvents(drugName);

            if (events.isEmpty()) {
                sendMessage(chatId,
                        "✅ <b>Nessun effetto collaterale recente registrato</b> per \"" + escapeHtml(drugName)
                                + "\".\n\n" +
                                "💡 <b>Nota:</b> Questo non significa che il farmaco non abbia effetti collaterali, " +
                                "ma che non ci sono segnalazioni recenti nel database FDA delle reazioni avverse.");
                return;
            }

            StringBuilder response = new StringBuilder();
            response.append(
                    String.format("⚠️ <b>Effetti Collaterali Segnalati per \"%s\"</b>\n\n", escapeHtml(drugName)));

            int total = ((Number) events.get("total")).intValue();
            response.append(String.format("📊 Segnalazioni analizzate: <b>%d</b>\n\n", total));

            // Reazioni più comuni
            @SuppressWarnings("unchecked")
            var reactions = (java.util.Map<String, Integer>) events.get("topReactions");
            if (reactions != null && !reactions.isEmpty()) {
                response.append("<b>🔴 Effetti collaterali più segnalati:</b>\n");
                int count = 0;
                for (var entry : reactions.entrySet()) {
                    if (count >= 10)
                        break;
                    response.append(String.format("• %s (%d segnalazioni)\n",
                            escapeHtml(entry.getKey()), entry.getValue()));
                    count++;
                }
                response.append("\n");
            }

            // Gravità - mostra solo percentuali se ha senso
            @SuppressWarnings("unchecked")
            var severity = (java.util.Map<String, Integer>) events.get("serious");
            if (severity != null && total > 0) {
                int serious = severity.getOrDefault("serious", 0);
                int nonSerious = severity.getOrDefault("nonSerious", 0);
                double seriousPercent = serious * 100.0 / total;

                response.append("<b>⚠️ Gravità:</b>\n");
                response.append(String.format("• Gravi: %d (%.1f%%)\n", serious, seriousPercent));
                response.append(String.format("• Non gravi: %d (%.1f%%)\n\n", nonSerious, 100.0 - seriousPercent));
            }

            response.append("ℹ️ <i>Nota importante: Questi dati riflettono il numero di segnalazioni, " +
                    "non necessariamente la pericolosità del farmaco. Un farmaco molto usato " +
                    "avrà più segnalazioni anche se è sicuro. Consulta sempre un medico.</i>");

            sendMessage(chatId, response.toString());

        } catch (Exception e) {
            logger.error("Errore ricerca effetti collaterali: " + drugName, e);

            if (e.getMessage().contains("404")) {
                sendMessage(chatId, "❌ Nessun effetto collaterale trovato per \"" + escapeHtml(drugName) + "\".");
            } else {
                sendMessage(chatId, "❌ Errore durante la ricerca. Riprova più tardi.");
            }
        }
    }

    private void handleHealthTopic(long chatId, String topic) {
        if (topic.isEmpty()) {
            sendMessage(chatId, "❌ Specifica un argomento di salute!\n\n" +
                    "💡 <b>Cosa fa questo comando:</b>\n" +
                    "Fornisce informazioni generali su salute, prevenzione e benessere.\n\n" +
                    "📝 <b>Esempi di argomenti:</b>\n" +
                    "• <code>/informazioni diabetes</code> (diabete)\n" +
                    "• <code>/informazioni heart</code> (cuore/cardio)\n" +
                    "• <code>/informazioni flu</code> (influenza)\n" +
                    "• <code>/informazioni nutrition</code> (nutrizione)\n" +
                    "• <code>/informazioni exercise</code> (esercizio fisico)\n" +
                    "• <code>/informazioni smoking</code> (fumo)\n" +
                    "• <code>/informazioni sleep</code> (sonno)");
            return;
        }

        sendMessage(chatId, "🔍 Cerco informazioni su \"" + topic + "\"...");

        try {
            var healthInfo = fdaService.getHealthInfo(topic);

            if (healthInfo == null || healthInfo.isEmpty()) {
                sendMessage(chatId,
                        "❌ <b>Nessuna informazione trovata</b> per \"" + escapeHtml(topic) + "\".\n\n" +
                                "💡 <b>Suggerimenti:</b>\n" +
                                "• Usa termini più generici in inglese\n" +
                                "• Prova: diabetes, heart, cancer, flu, nutrition, exercise, smoking, sleep\n" +
                                "• Evita termini troppo specifici o tecnici");
                return;
            }

            StringBuilder response = new StringBuilder();
            response.append(
                    String.format("💡 <b>Informazioni su: %s</b>\n\n", escapeHtml((String) healthInfo.get("title"))));

            if (healthInfo.containsKey("sections")) {
                @SuppressWarnings("unchecked")
                var sections = (java.util.List<java.util.Map<String, String>>) healthInfo.get("sections");

                for (var section : sections) {
                    String title = section.get("title");
                    String content = section.get("content");

                    if (title != null && content != null) {
                        response.append(String.format("<b>%s</b>\n%s\n\n",
                                escapeHtml(title), escapeHtml(content)));
                    }
                }
            }

            if (healthInfo.containsKey("url")) {
                response.append(
                        String.format("\n🔗 <a href=\"%s\">Leggi l'articolo completo</a>", healthInfo.get("url")));
            }

            response.append("\n\n<i>ℹ️ Fonte: MyHealthfinder (National Library of Medicine - Governo USA)</i>");

            sendMessage(chatId, response.toString());

        } catch (Exception e) {
            logger.error("Errore ricerca informazioni salute: " + topic, e);
            sendMessage(chatId, "❌ Errore durante la ricerca. Riprova più tardi.");
        }
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
            String indications = formatIndications(drug.getIndications());
            sb.append("   💊 <i>Indicazioni:</i>\n");
            sb.append(indications);
        }

        return sb.toString();
    }

    private String formatIndications(String rawIndications) {
        // Pulisci il testo
        String text = rawIndications.replaceAll("\\s+", " ").trim();

        // Dividi in frasi (punto, punto esclamativo, punto interrogativo)
        String[] sentences = text.split("(?<=[.!?])\\s+");

        StringBuilder formatted = new StringBuilder();
        int charCount = 0;
        int maxChars = 300; // Limite caratteri totali

        for (String sentence : sentences) {
            if (charCount + sentence.length() > maxChars) {
                formatted.append("   ...\n");
                break;
            }

            formatted.append("   • ").append(escapeHtml(sentence.trim())).append("\n");
            charCount += sentence.length();
        }

        return formatted.toString();
    }

    private String formatRecallInfo(Recall recall, int index) {
        StringBuilder sb = new StringBuilder();

        // Formatta la data
        String dateStr = recall.getRecallDate();
        if (dateStr != null && dateStr.length() == 8) {
            // Da 20251224 a 2025-12-24
            dateStr = dateStr.substring(0, 4) + "-" +
                    dateStr.substring(4, 6) + "-" +
                    dateStr.substring(6, 8);
        }

        sb.append(String.format("<b>%d. Richiamo del %s</b>\n", index,
                dateStr != null ? dateStr : "data sconosciuta"));

        if (recall.getProductDescription() != null) {
            // Non troncare, mostra tutto
            sb.append(String.format("   📦 <i>Prodotto:</i>\n   %s\n\n",
                    escapeHtml(recall.getProductDescription())));
        }

        if (recall.getReasonForRecall() != null) {
            // Non troncare, mostra tutto
            sb.append(String.format("   ⚠️ <i>Motivo:</i>\n   %s\n\n",
                    escapeHtml(recall.getReasonForRecall())));
        }

        if (recall.getClassification() != null) {
            String classEmoji = getClassificationEmoji(recall.getClassification());
            sb.append(String.format("   %s <i>Classificazione:</i> <b>Class %s</b>\n",
                    classEmoji, recall.getClassification()));
        }

        return sb.toString();
    }

    private String getClassificationEmoji(String classification) {
        return switch (classification.toUpperCase()) {
            case "I", "CLASS I" -> "🔴";
            case "II", "CLASS II" -> "🟠";
            case "III", "CLASS III" -> "🟡";
            default -> "⚪";
        };
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

        logger.debug("Callback ricevuto: {}", callbackData);

        // Rimuove la tastiera inline
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

        if (callbackData.startsWith("recalls:")) {
            String drugName = callbackData.substring(8);
            handleRecalls(chatId, drugName);

        } else if (callbackData.startsWith("morerecalls:")) {
            // Formato: morerecalls:drugName:offset
            String[] parts = callbackData.split(":", 3);
            String drugName = parts[1];
            int offset = Integer.parseInt(parts[2]);
            handleRecalls(chatId, drugName, offset);

        } else if (callbackData.startsWith("moredrugs:")) {
            // Formato: moredrugs:drugName:offset
            String[] parts = callbackData.split(":", 3);
            String drugName = parts[1];
            int offset = Integer.parseInt(parts[2]);
            handleSearchDrug(chatId, drugName, offset);
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
        if (text == null || text.isEmpty())
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // Utility per troncare testo
    private String truncate(String text, int maxLength) {
        if (text == null || text.isEmpty())
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}