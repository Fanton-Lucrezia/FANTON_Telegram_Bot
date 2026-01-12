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

    public void handleStart(long chatId, String username) {
        String welcome = String.format(
                "👋 Benvenuto <b>%s</b> su MedBot!\n\n" +
                        "🔬 Bot per informazioni su farmaci usando le API della FDA americana.\n\n" +
                        "🇺🇸 I dati sono in <b>inglese</b>, cerca i farmaci con nomi inglesi " +
                        "(es. 'aspirin' invece di 'aspirina').\n\n" +
                        "📖 Usa /help per iniziare!",
                username != null ? username : "utente");

        messageSender.sendMessage(chatId, welcome + DISCLAIMER);
    }

    public void handleHelp(long chatId) {
        String help = "<b>📋 Comandi Disponibili</b>\n\n" +
                "/start - Messaggio di benvenuto\n" +
                "/help - Mostra questa guida\n\n" +
                "<b>🔍 Ricerca Farmaci</b>\n" +
                "/cerca &lt;nome&gt; - Cerca un farmaco\n" +
                "Esempio: <code>/cerca aspirin</code>\n\n" +
                "<b>⚠️ Sicurezza</b>\n" +
                "/richiami &lt;nome|all&gt; - Controlla modifiche ai farmaci per problemi di sicurezza o efficacia\n" +
                "/effetticollaterali &lt;nome&gt; - Effetti collaterali segnalati\n" +
                "/interazioni &lt;farmaco1 + farmaco2&gt; - Verifica interazioni\n\n" +
                "<b>📊 Statistiche</b>\n" +
                "/mystats - Le tue statistiche\n" +
                "/recenti - Farmaci cercati di recente\n" +
                "/bookmarks - Gestisci i preferiti\n\n" +
                "<b>💡 Suggerimento:</b>\n" +
                "Puoi inviare un comando senza parametri e ti chiederò di inserire le informazioni necessarie.";

        messageSender.sendMessage(chatId, help);
    }

    public void handleSearchDrug(long chatId, String drugName, int offset) {
        //Validazione input
        if (drugName.isEmpty() || drugName.length() < 2) {
            messageSender.sendMessage(chatId, "❌ Nome farmaco non valido.\n\n" +
                    "📝 Esempio corretto: <code>/cerca aspirin</code>");
            return;
        }

        //Controlla se ci sono caratteri strani o multipli farmaci
        if (drugName.contains(",") || drugName.contains(";")) {
            messageSender.sendMessage(chatId, "❌ Specifica un solo farmaco per volta.\n\n" +
                    "📝 Esempio corretto: <code>/cerca aspirin</code>");
            return;
        }

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

            int pageSize = 3;
            int end = Math.min(offset + pageSize, drugs.size());

            StringBuilder response = new StringBuilder();
            response.append(String.format("✅ <b>%d risultati</b> per \"%s\":\n\n",
                    drugs.size(), drugName));

            for (int i = offset; i < end; i++) {
                response.append(formatDrugInfo(drugs.get(i), i + 1));
                if (i < end - 1) response.append("\n➖➖➖\n\n");
            }

            List<InlineKeyboardRow> rows = new ArrayList<>();

            if (end < drugs.size()) {
                InlineKeyboardButton moreButton = InlineKeyboardButton.builder()
                        .text(String.format("⬇️ Altri %d risultati", drugs.size() - end))
                        .callbackData("moredrugs:" + drugName + ":" + end)
                        .build();
                rows.add(new InlineKeyboardRow(moreButton));
            }

            InlineKeyboardRow actionsRow = new InlineKeyboardRow();
            actionsRow.add(InlineKeyboardButton.builder()
                    .text("🔍 Richiami")
                    .callbackData("recalls:" + drugName)
                    .build());
            
            //Controlla se è già salvato
            if (bookmarkService.isBookmarked(chatId, drugName)) {
                actionsRow.add(InlineKeyboardButton.builder()
                        .text("✅ Già salvato")
                        .callbackData("already_saved")
                        .build());
            } else {
                actionsRow.add(InlineKeyboardButton.builder()
                        .text("⭐ Salva")
                        .callbackData("bookmark:" + drugName)
                        .build());
            }
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

    public void handleRecalls(long chatId, String drugName, int offset) {
        //Validazione input
        if (drugName.isEmpty()) {
            messageSender.sendMessage(chatId, "❌ Specifica il nome del farmaco o 'all'.\n\n" +
                    "📝 Esempio corretto: <code>/richiami aspirin</code>");
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

    public void handleAdverseEvents(long chatId, String drugName) {
        //Validazione input: solo un farmaco
        if (drugName.isEmpty() || drugName.length() < 2) {
            messageSender.sendMessage(chatId, "❌ Nome farmaco non valido.\n\n" +
                    "📝 Esempio corretto: <code>/effetticollaterali aspirin</code>");
            return;
        }

        //Controlla se ci sono più farmaci
        if (drugName.contains(",") || drugName.contains("+") || drugName.contains(";")) {
            messageSender.sendMessage(chatId, "❌ Specifica un solo farmaco per volta.\n\n" +
                    "📝 Esempio corretto: <code>/effetticollaterali aspirin</code>\n\n" +
                    "💡 Per interazioni tra farmaci usa: <code>/interazioni farmaco1 + farmaco2</code>");
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

    public void handleDrugInteractions(long chatId, String args) {
        //Validazione input
        if (args.isEmpty()) {
            messageSender.sendMessage(chatId, "❌ Specifica i farmaci separati da +\n\n" +
                    "📝 Esempio corretto: <code>/interazioni aspirin + ibuprofen</code>");
            return;
        }

        String[] drugs = args.split("\\+");
        if (drugs.length < 2) {
            messageSender.sendMessage(chatId, "❌ Specifica almeno due farmaci separati da +\n\n" +
                    "📝 Esempio corretto: <code>/interazioni aspirin + ibuprofen</code>");
            return;
        }

        //Pulisce e valida i nomi
        for (int i = 0; i < drugs.length; i++) {
            drugs[i] = drugs[i].trim();
            if (drugs[i].isEmpty() || drugs[i].length() < 2) {
                messageSender.sendMessage(chatId, "❌ Uno o più nomi di farmaci non sono validi.\n\n" +
                        "📝 Esempio corretto: <code>/interazioni aspirin + ibuprofen</code>");
                return;
            }
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

    public void handleMyStats(long chatId) {
        try {
            String stats = statsService.getUserStats(chatId);
            messageSender.sendMessage(chatId, stats);
        } catch (Exception e) {
            System.out.println("Errore statistiche: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore nel recuperare le statistiche.");
        }
    }

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
     * Gestisce l'aggiunta di un bookmark tramite bottone inline.
     * Mantiene il bottone richiami dopo il salvataggio.
     */
    public void handleBookmarkAdd(long chatId, String drugName) {
        try {
            boolean alreadySaved = bookmarkService.isBookmarked(chatId, drugName);
            
            if (alreadySaved) {
                //Crea bottone richiami
                InlineKeyboardButton recallsButton = InlineKeyboardButton.builder()
                        .text("🔍 Controlla richiami")
                        .callbackData("recalls:" + drugName)
                        .build();
                
                InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(recallsButton))
                        .build();
                
                messageSender.sendMessageWithKeyboard(chatId, 
                        "ℹ️ <b>\"" + drugName + "\"</b> è già nei tuoi preferiti!", keyboard);
            } else {
                bookmarkService.addBookmark(chatId, drugName);
                
                //Crea bottone richiami
                InlineKeyboardButton recallsButton = InlineKeyboardButton.builder()
                        .text("🔍 Controlla richiami")
                        .callbackData("recalls:" + drugName)
                        .build();
                
                InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(recallsButton))
                        .build();
                
                messageSender.sendMessageWithKeyboard(chatId, "⭐ Farmaco salvato!", keyboard);
            }
        } catch (Exception e) {
            System.out.println("Errore bookmark: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore nel salvare il farmaco.");
        }
    }

    public void handleBookmarks(long chatId, String args) {
        String[] parts = args.split("\\s+", 2);
        String action = parts.length > 0 ? parts[0].toLowerCase() : "";
        String drugName = parts.length > 1 ? parts[1].trim() : "";

        try {
            switch (action) {
                case "add" -> {
                    if (drugName.isEmpty() || drugName.length() < 2) {
                        messageSender.sendMessage(chatId, "❌ Nome farmaco non valido!\n\n" +
                                "📝 Esempio corretto: <code>/bookmarks add aspirin</code>");
                        return;
                    }
                    
                    boolean alreadySaved = bookmarkService.isBookmarked(chatId, drugName);
                    if (alreadySaved) {
                        messageSender.sendMessage(chatId, "ℹ️ <b>\"" + drugName + "\"</b> è già nei tuoi preferiti!");
                    } else {
                        bookmarkService.addBookmark(chatId, drugName);
                        messageSender.sendMessage(chatId, "⭐ Farmaco salvato!");
                    }
                }
                case "remove" -> {
                    if (drugName.isEmpty()) {
                        messageSender.sendMessage(chatId, "❌ Specifica il farmaco da rimuovere!\n\n" +
                                "📝 Esempio corretto: <code>/bookmarks remove aspirin</code>");
                        return;
                    }
                    
                    boolean wasBookmarked = bookmarkService.isBookmarked(chatId, drugName);
                    if (!wasBookmarked) {
                        messageSender.sendMessage(chatId, "❌ <b>\"" + drugName + "\"</b> non è nei tuoi preferiti.\n\n" +
                                "💡 Usa <code>/bookmarks</code> per vedere la lista.");
                    } else {
                        bookmarkService.removeBookmark(chatId, drugName);
                        messageSender.sendMessage(chatId, "🗑️ Farmaco rimosso dai preferiti.");
                    }
                }
                case "list", "" -> {
                    String bookmarks = bookmarkService.getBookmarks(chatId);
                    messageSender.sendMessage(chatId, bookmarks);
                }
                default -> messageSender.sendMessage(chatId, "❌ Azione non valida.\n\n" +
                        "📝 Usa: <code>/bookmarks</code>, <code>/bookmarks add &lt;nome&gt;</code>, " +
                        "<code>/bookmarks remove &lt;nome&gt;</code>");
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

    private String getClassificationEmoji(String classification) {
        return switch (classification.toUpperCase()) {
            case "I", "CLASS I" -> "🔴";
            case "II", "CLASS II" -> "🟠";
            case "III", "CLASS III" -> "🟡";
            default -> "⚪";
        };
    }
}