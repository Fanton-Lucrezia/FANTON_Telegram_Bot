package org.medBot.bot;

import org.medBot.service.BookmarkService;
import org.medBot.service.OpenFdaService;
import org.medBot.service.StatisticsService;
import org.medBot.model.Drug;
import org.medBot.model.Recall;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestisce tutti i comandi del bot.
 * Separa la logica dei comandi dalla classe principale MedBot.
 */
public class CommandHandler {
    private final OpenFdaService fdaService;
    private final StatisticsService statsService;
    private final BookmarkService bookmarkService;
    private final MessageSender messageSender;

    private static final String DISCLAIMER = "\n\n⚠️ <i>Queste informazioni sono solo a scopo informativo " +
            "e non costituiscono consulenza medica. Consulta un professionista sanitario.</i>";

    public CommandHandler(MessageSender messageSender) {
        this.messageSender = messageSender;
        this.fdaService = new OpenFdaService();
        this.statsService = new StatisticsService();
        this.bookmarkService = new BookmarkService();
    }

    /**
     * Gestisce il comando /start
     */
    public void handleStart(long chatId, String username) {
        String welcome = String.format(
                "👋 Benvenuto <b>%s</b> su MedBot!\n\n" +
                        "🔬 Bot per informazioni su farmaci usando le API della FDA americana.\n\n" +
                        "🇺🇸 I dati sono in <b>inglese</b>, cerca i farmaci con nomi inglesi " +
                        "(es. 'aspirin' invece di 'aspirina').\n\n" +
                        "📖 Usa /help o il menù qui sotto per iniziare!",
                username != null ? username : "utente");

        messageSender.sendMessageWithMenu(chatId, welcome + DISCLAIMER);
    }

    /**
     * Gestisce il comando /help
     */
    public void handleHelp(long chatId) {
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
                "/interazioni &lt;farmaco1 + farmaco2&gt; - Verifica interazioni\n\n" +
                "<b>📊 Statistiche</b>\n" +
                "/mystats - Le tue statistiche\n" +
                "/recenti - Farmaci cercati di recente\n" +
                "/bookmarks - Gestisci i preferiti\n\n" +
                "<b>💡 Esempi:</b>\n" +
                "• <code>/cerca ibuprofen</code>\n" +
                "• <code>/farmacolegale oxycodone</code>\n" +
                "• <code>/interazioni aspirin + ibuprofen</code>";

        messageSender.sendMessage(chatId, help);
    }

    /**
     * Gestisce il comando /cerca
     */
    public void handleSearchDrug(long chatId, String drugName, int offset) {
        if (drugName.isEmpty()) {
            messageSender.sendMessage(chatId, "❌ Specifica il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/cerca aspirin</code>");
            return;
        }

        //Registra la ricerca solo alla prima pagina
        if (offset == 0) {
            messageSender.sendMessage(chatId, "🔍 Cerco \"" + drugName + "\"...");
            statsService.recordSearch(chatId, drugName);
        }

        try {
            List<Drug> drugs = fdaService.searchDrug(drugName);

            if (drugs.isEmpty()) {
                messageSender.sendMessage(chatId, "❌ Nessun risultato per \"" + drugName + "\".\n\n" +
                        "💡 Prova con il nome generico o in inglese.");
                return;
            }

            //Paginazione: 3 risultati per pagina
            int pageSize = 3;
            int end = Math.min(offset + pageSize, drugs.size());

            StringBuilder response = new StringBuilder();
            response.append(String.format("✅ <b>%d risultati</b> per \"%s\":\n\n",
                    drugs.size(), drugName));

            //Formatta ogni farmaco
            for (int i = offset; i < end; i++) {
                response.append(formatDrugInfo(drugs.get(i), i + 1));
                if (i < end - 1) response.append("\n➖➖➖\n\n");
            }

            //Crea tastiera con bottoni
            List<InlineKeyboardRow> rows = new ArrayList<>();

            //Bottone per altri risultati
            if (end < drugs.size()) {
                InlineKeyboardButton moreButton = InlineKeyboardButton.builder()
                        .text(String.format("⬇️ Altri %d risultati", drugs.size() - end))
                        .callbackData("moredrugs:" + drugName + ":" + end)
                        .build();
                rows.add(new InlineKeyboardRow(moreButton));
            }

            //Bottoni azioni
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

            messageSender.sendMessageWithKeyboard(chatId, response.toString(), keyboard);

        } catch (Exception e) {
            System.out.println("Errore ricerca: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore durante la ricerca.");
        }
    }

    /**
     * Gestisce il comando /richiami
     */
    public void handleRecalls(long chatId, String drugName, int offset) {
        if (drugName.isEmpty()) {
            messageSender.sendMessage(chatId, "❌ Specifica il nome del farmaco o 'all'.\n\n" +
                    "📝 Esempio: <code>/richiami aspirin</code>");
            return;
        }

        if (offset == 0) {
            messageSender.sendMessage(chatId, "🔍 Cerco richiami...");
        }

        try {
            List<Recall> recalls = drugName.equalsIgnoreCase("all")
                    ? fdaService.getRecentRecalls(50)
                    : fdaService.searchRecalls(drugName);

            if (recalls.isEmpty()) {
                messageSender.sendMessage(chatId, "✅ Nessun richiamo per \"" + drugName + "\".\n\n" +
                        "🎉 Buone notizie!");
                return;
            }

            //Paginazione: 5 richiami per pagina
            int pageSize = 5;
            int end = Math.min(offset + pageSize, recalls.size());

            StringBuilder response = new StringBuilder();
            response.append(String.format("⚠️ <b>%d Richiami FDA</b>\n\n", recalls.size()));

            if (offset == 0) {
                response.append("<i>Classificazione:</i>\n" +
                        "• <b>Class I</b>: Rischio grave\n" +
                        "• <b>Class II</b>: Rischio temporaneo\n" +
                        "• <b>Class III</b>: Rischio minimo\n\n");
            }

            for (int i = offset; i < end; i++) {
                response.append(formatRecallInfo(recalls.get(i), i + 1));
                if (i < end - 1) response.append("\n➖➖➖\n\n");
            }

            //Bottone per altri richiami
            if (end < recalls.size()) {
                InlineKeyboardButton button = InlineKeyboardButton.builder()
                        .text(String.format("📋 Altri %d richiami", recalls.size() - end))
                        .callbackData("morerecalls:" + drugName + ":" + end)
                        .build();

                InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(button))
                        .build();

                messageSender.sendMessageWithKeyboard(chatId, response.toString(), keyboard);
            } else {
                messageSender.sendMessage(chatId, response.toString());
            }

        } catch (Exception e) {
            System.out.println("Errore richiami: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore durante la ricerca richiami.");
        }
    }

    /**
     * Gestisce il comando /farmacolegale
     */
    public void handleControlledSubstance(long chatId, String drugName) {
        if (drugName.isEmpty()) {
            messageSender.sendMessage(chatId, "❌ Specifica il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/farmacolegale oxycodone</code>\n\n" +
                    "💡 Verifica se è una sostanza controllata (rischio dipendenza).");
            return;
        }

        messageSender.sendMessage(chatId, "🔍 Verifico \"" + drugName + "\"...");

        try {
            String schedule = fdaService.checkDrugSchedule(drugName);

            if (schedule == null) {
                messageSender.sendMessage(chatId, "✅ \"" + drugName + "\" NON è una sostanza controllata.\n\n" +
                        "Basso rischio di abuso/dipendenza.");
            } else {
                String emoji = getScheduleEmoji(schedule);
                String description = getScheduleDescription(schedule);

                messageSender.sendMessage(chatId, String.format(
                        "🚨 <b>SOSTANZA CONTROLLATA</b>\n\n" +
                                "Farmaco: <b>%s</b>\n" +
                                "Classificazione: %s <b>Schedule %s</b>\n\n" +
                                "📋 %s\n\n" +
                                "⚠️ Richiede prescrizione speciale.",
                        drugName, emoji, schedule, description));
            }

        } catch (Exception e) {
            System.out.println("Errore verifica sostanza: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore durante la verifica.");
        }
    }

    /**
     * Gestisce il comando /effetticollaterali
     */
    public void handleAdverseEvents(long chatId, String drugName) {
        if (drugName.isEmpty()) {
            messageSender.sendMessage(chatId, "❌ Specifica il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/effetticollaterali aspirin</code>");
            return;
        }

        messageSender.sendMessage(chatId, "🔍 Cerco effetti collaterali...");

        try {
            var events = fdaService.getAdverseEvents(drugName);

            if (events.isEmpty()) {
                messageSender.sendMessage(chatId, "✅ Nessun effetto collaterale recente registrato.");
                return;
            }

            StringBuilder response = new StringBuilder();
            response.append(String.format("⚠️ <b>Effetti Collaterali - %s</b>\n\n", drugName));

            int total = ((Number) events.get("total")).intValue();
            response.append(String.format("📊 Segnalazioni: <b>%d</b>\n\n", total));

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
            }

            response.append("\n<i>Consulta sempre un medico.</i>");
            messageSender.sendMessage(chatId, response.toString());

        } catch (Exception e) {
            System.out.println("Errore effetti collaterali: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore durante la ricerca.");
        }
    }

    /**
     * Gestisce il comando /interazioni
     */
    public void handleDrugInteractions(long chatId, String args) {
        if (args.isEmpty()) {
            messageSender.sendMessage(chatId, "❌ Specifica i farmaci separati da +\n\n" +
                    "📝 Esempio: <code>/interazioni aspirin + ibuprofen</code>");
            return;
        }

        String[] drugs = args.split("\\+");
        if (drugs.length < 2) {
            messageSender.sendMessage(chatId, "❌ Specifica almeno due farmaci separati da +");
            return;
        }

        //Pulisce i nomi
        for (int i = 0; i < drugs.length; i++) {
            drugs[i] = drugs[i].trim();
        }

        messageSender.sendMessage(chatId, "🔍 Verifico interazioni...");

        try {
            var interactions = fdaService.checkDrugInteractions(drugs);

            if (interactions.isEmpty() || ((Number) interactions.get("count")).intValue() == 0) {
                messageSender.sendMessage(chatId, "✅ <b>Nessuna interazione grave segnalata</b>\n\n" +
                        "⚠️ <i>Consulta sempre un medico.</i>");
                return;
            }

            int count = ((Number) interactions.get("count")).intValue();
            StringBuilder response = new StringBuilder();
            response.append("⚠️ <b>POSSIBILI INTERAZIONI</b>\n\n");
            response.append("Farmaci: ").append(String.join(" + ", drugs)).append("\n\n");
            response.append(String.format("📊 <b>%d segnalazioni</b> di eventi avversi.\n\n", count));

            @SuppressWarnings("unchecked")
            var commonReactions = (java.util.List<String>) interactions.get("commonReactions");
            if (commonReactions != null && !commonReactions.isEmpty()) {
                response.append("<b>🔴 Reazioni comuni:</b>\n");
                for (int i = 0; i < Math.min(8, commonReactions.size()); i++) {
                    response.append("• ").append(commonReactions.get(i)).append("\n");
                }
            }

            response.append("\n🚨 Consulta un medico per informazioni accurate.");
            messageSender.sendMessage(chatId, response.toString());

        } catch (Exception e) {
            System.out.println("Errore interazioni: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore durante la verifica.");
        }
    }

    /**
     * Gestisce il comando /mystats
     */
    public void handleMyStats(long chatId) {
        try {
            String stats = statsService.getUserStats(chatId);
            messageSender.sendMessage(chatId, stats);
        } catch (Exception e) {
            System.out.println("Errore statistiche: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore nel recuperare le statistiche.");
        }
    }

    /**
     * Gestisce il comando /recenti
     */
    public void handleRecentSearches(long chatId) {
        try {
            String recent = statsService.getRecentSearches(chatId);
            messageSender.sendMessage(chatId, recent);
        } catch (Exception e) {
            System.out.println("Errore ricerche recenti: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore nel recuperare le ricerche.");
        }
    }

    /**
     * Gestisce il comando /bookmarks
     */
    public void handleBookmarks(long chatId, String args) {
        String[] parts = args.split("\\s+", 2);
        String action = parts.length > 0 ? parts[0].toLowerCase() : "";
        String drugName = parts.length > 1 ? parts[1].trim() : "";

        try {
            switch (action) {
                case "add" -> {
                    if (drugName.isEmpty()) {
                        messageSender.sendMessage(chatId, "❌ Specifica il farmaco!\n" +
                                "Esempio: <code>/bookmarks add aspirin</code>");
                        return;
                    }
                    bookmarkService.addBookmark(chatId, drugName);
                    messageSender.sendMessage(chatId, "⭐ Farmaco salvato!");
                }
                case "remove" -> {
                    if (drugName.isEmpty()) {
                        messageSender.sendMessage(chatId, "❌ Specifica il farmaco!\n" +
                                "Esempio: <code>/bookmarks remove aspirin</code>");
                        return;
                    }
                    bookmarkService.removeBookmark(chatId, drugName);
                    messageSender.sendMessage(chatId, "🗑️ Farmaco rimosso.");
                }
                case "list", "" -> {
                    String bookmarks = bookmarkService.getBookmarks(chatId);
                    messageSender.sendMessage(chatId, bookmarks);
                }
                default -> messageSender.sendMessage(chatId, "❌ Azione non valida.\n\n" +
                        "Usa: <code>/bookmarks</code>, <code>/bookmarks add &lt;nome&gt;</code>");
            }
        } catch (Exception e) {
            System.out.println("Errore bookmarks: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore gestione preferiti.");
        }
    }

    // ==================== UTILITY METHODS ====================

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

    private String formatIndications(String raw) {
        String text = raw.replaceAll("\\s+", " ").trim();
        String[] sentences = text.split("(?<=[.!?])\\s+");

        StringBuilder formatted = new StringBuilder();
        int charCount = 0;

        for (String sentence : sentences) {
            if (charCount + sentence.length() > 300) {
                formatted.append("   ...\n");
                break;
            }
            formatted.append("   • ").append(sentence.trim()).append("\n");
            charCount += sentence.length();
        }

        return formatted.toString();
    }

    private String formatRecallInfo(Recall recall, int index) {
        StringBuilder sb = new StringBuilder();

        String dateStr = recall.getRecallDate();
        if (dateStr != null && dateStr.length() == 8) {
            dateStr = dateStr.substring(0, 4) + "-" +
                    dateStr.substring(4, 6) + "-" +
                    dateStr.substring(6, 8);
        }

        sb.append(String.format("<b>%d. Richiamo del %s</b>\n", index, dateStr != null ? dateStr : "N/A"));

        if (recall.getProductDescription() != null) {
            sb.append(String.format("   📦 %s\n\n", recall.getProductDescription()));
        }

        if (recall.getReasonForRecall() != null) {
            sb.append(String.format("   ⚠️ %s\n\n", recall.getReasonForRecall()));
        }

        if (recall.getClassification() != null) {
            String emoji = getClassificationEmoji(recall.getClassification());
            sb.append(String.format("   %s <b>Class %s</b>\n", emoji, recall.getClassification()));
        }

        return sb.toString();
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
            case "I" -> "Alto potenziale di abuso, nessun uso medico negli USA";
            case "II" -> "Alto potenziale di abuso, rischio grave dipendenza";
            case "III" -> "Potenziale di abuso moderato";
            case "IV" -> "Basso potenziale di abuso";
            case "V" -> "Potenziale di abuso molto basso";
            default -> "Informazioni non disponibili";
        };
    }

    private String getClassificationEmoji(String classification) {
        return switch (classification.toUpperCase()) {
            case "I", "CLASS I" -> "🔴";
            case "II", "CLASS II" -> "🟠";
            case "III", "CLASS III" -> "🟡";
            default -> "⚪";
        };
    }
}