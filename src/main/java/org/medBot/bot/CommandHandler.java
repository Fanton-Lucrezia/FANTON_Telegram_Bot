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

/*Gestisce tutti i comandi del bot
Ogni metodo handleX corrisponde a un comando specifico e contiene la logica per elaborare la richiesta*/
public class CommandHandler {
    private final OpenFdaService fdaService;
    private final StatisticsService statsService;
    private final BookmarkService bookmarkService;
    private final MessageSender messageSender;

    //Disclaimer legale mostrato in alcuni messaggi per evitare responsabilità mediche
    private static final String DISCLAIMER = "\n\n⚠️ <i>Queste informazioni sono solo a scopo informativo " +
            "e non costituiscono consulenza medica. Consulta un professionista sanitario.</i>";

    //Costruttore che inizializza tutti i servizi necessari per il funzionamento dei comandi
    public CommandHandler(MessageSender messageSender) {
        this.messageSender = messageSender;
        this.fdaService = new OpenFdaService();
        this.statsService = new StatisticsService();
        this.bookmarkService = new BookmarkService();
    }

    //Gestisce il comando /start che dà il benvenuto all'utente
    //Mostra un messaggio introduttivo con informazioni sul bot
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

    //Gestisce il comando /help che mostra la lista di tutti i comandi disponibili
    //Include esempi d'uso e suggerimenti per l'utente
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
                "/mystats - Le tue statistiche personali\n" +
                "/statistiche - Statistiche globali del bot\n" +
                "/recenti - Farmaci cercati di recente\n" +
                "/bookmarks - Gestisci i preferiti\n\n" +
                "<b>💡 Suggerimento:</b>\n" +
                "Puoi inviare un comando senza parametri e ti chiederò di inserire le informazioni necessarie.";

        messageSender.sendMessage(chatId, help);
    }

    /*Gestisce il comando /cerca per ricercare farmaci nelle API FDA
    Supporta la paginazione per mostrare più risultati
    offset: posizione da cui iniziare a mostrare i risultati (0 per la prima pagina)*/
    public void handleSearchDrug(long chatId, String drugName, int offset) {
        //Validazione input: controlla che il nome sia valido
        if (drugName.isEmpty() || drugName.length() < 2) {
            messageSender.sendMessage(chatId, "❌ Nome farmaco non valido.\n\n" +
                    "📝 Esempio corretto: <code>/cerca aspirin</code>");
            return;
        }

        //Controlla se l'utente ha inserito più farmaci separati (non supportato)
        if (drugName.contains(",") || drugName.contains(";")) {
            messageSender.sendMessage(chatId, "❌ Specifica un solo farmaco per volta.\n\n" +
                    "📝 Esempio corretto: <code>/cerca aspirin</code>");
            return;
        }

        //Solo alla prima pagina (offset=0) mostra messaggio di caricamento e registra la ricerca
        if (offset == 0) {
            messageSender.sendMessage(chatId, "🔍 Cerco \"" + drugName + "\"...");
            statsService.recordSearch(chatId, drugName);
        }

        try {
            //Chiama il servizio FDA per ottenere i risultati
            List<Drug> drugs = fdaService.searchDrug(drugName);

            //Se non trova nulla, informa l'utente
            if (drugs.isEmpty()) {
                messageSender.sendMessage(chatId, "❌ Nessun risultato per \"" + drugName + "\".\n\n" +
                        "💡 Prova con il nome generico o in inglese.");
                return;
            }

            //Paginazione: mostra 3 risultati per volta
            int pageSize = 3;
            int end = Math.min(offset + pageSize, drugs.size());

            //Costruisce la risposta formattata
            StringBuilder response = new StringBuilder();
            response.append(String.format("✅ <b>%d risultati</b> per \"%s\":\n\n",
                    drugs.size(), drugName));

            //Formatta ogni farmaco della pagina corrente
            for (int i = offset; i < end; i++) {
                response.append(formatDrugInfo(drugs.get(i), i + 1));
                if (i < end - 1) response.append("\n➖➖➖\n\n");
            }

            //Crea i bottoni inline per azioni rapide
            List<InlineKeyboardRow> rows = new ArrayList<>();

            //Se ci sono altri risultati, aggiunge il bottone "Altri risultati"
            if (end < drugs.size()) {
                InlineKeyboardButton moreButton = InlineKeyboardButton.builder()
                        .text(String.format("⬇️ Altri %d risultati", drugs.size() - end))
                        .callbackData("moredrugs:" + drugName + ":" + end)
                        .build();
                rows.add(new InlineKeyboardRow(moreButton));
            }

            //Riga di bottoni per azioni (Richiami e Salva)
            InlineKeyboardRow actionsRow = new InlineKeyboardRow();
            actionsRow.add(InlineKeyboardButton.builder()
                    .text("🔍 Richiami")
                    .callbackData("recalls:" + drugName)
                    .build());
            
            //Controlla se il farmaco è già salvato nei preferiti per mostrare il bottone appropriato
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

            //Crea la tastiera inline con tutti i bottoni
            InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                    .keyboard(rows)
                    .build();

            messageSender.sendMessageWithKeyboard(chatId, response.toString(), keyboard);

        } catch (Exception e) {
            System.out.println("Errore ricerca: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore durante la ricerca.");
        }
    }

    /*Gestisce il comando /richiami per cercare richiami FDA
    Può cercare richiami per un farmaco specifico o mostrare gli ultimi richiami generali*/
    public void handleRecalls(long chatId, String drugName, int offset) {
        //Validazione input
        if (drugName.isEmpty()) {
            messageSender.sendMessage(chatId, "❌ Specifica il nome del farmaco o 'all'.\n\n" +
                    "📝 Esempio corretto: <code>/richiami aspirin</code>");
            return;
        }

        //Mostra messaggio di caricamento solo alla prima pagina
        if (offset == 0) {
            messageSender.sendMessage(chatId, "🔍 Cerco richiami...");
        }

        try {
            //Se l'utente scrive "all", mostra gli ultimi 50 richiami generali
            //Altrimenti cerca richiami per il farmaco specifico
            List<Recall> recalls = drugName.equalsIgnoreCase("all")
                    ? fdaService.getRecentRecalls(50)
                    : fdaService.searchRecalls(drugName);

            //Se non ci sono richiami, è una buona notizia!
            if (recalls.isEmpty()) {
                messageSender.sendMessage(chatId, "✅ Nessun richiamo per \"" + drugName + "\".\n\n" +
                        "🎉 Buone notizie!");
                return;
            }

            //Paginazione: 5 richiami per volta
            int pageSize = 5;
            int end = Math.min(offset + pageSize, recalls.size());

            StringBuilder response = new StringBuilder();
            response.append(String.format("⚠️ <b>%d Richiami FDA</b>\n\n", recalls.size()));

            //Solo nella prima pagina, spiega cosa significano le classificazioni
            if (offset == 0) {
                response.append("<i>Classificazione:</i>\n" +
                        "• <b>Class I</b>: Rischio grave\n" +
                        "• <b>Class II</b>: Rischio temporaneo\n" +
                        "• <b>Class III</b>: Rischio minimo\n\n");
            }

            //Formatta ogni richiamo della pagina corrente
            for (int i = offset; i < end; i++) {
                response.append(formatRecallInfo(recalls.get(i), i + 1));
                if (i < end - 1) response.append("\n➖➖➖\n\n");
            }

            //Se ci sono altri richiami, aggiunge bottone per la paginazione
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
            messageSender.sendMessage(chatId, "❌ Errore durante la ricerca richiami. Riprova.");
        }
    }

    //Gestisce il comando /effetticollaterali per cercare effetti collaterali segnalati
    public void handleAdverseEvents(long chatId, String drugName) {
        //Validazione: solo un farmaco per volta
        if (drugName.isEmpty() || drugName.length() < 2) {
            messageSender.sendMessage(chatId, "❌ Nome farmaco non valido.\n\n" +
                    "📝 Esempio corretto: <code>/effetticollaterali aspirin</code>");
            return;
        }

        //Controlla se l'utente ha inserito più farmaci (usa /interazioni per quello)
        if (drugName.contains(",") || drugName.contains("+") || drugName.contains(";")) {
            messageSender.sendMessage(chatId, "❌ Specifica un solo farmaco per volta.\n\n" +
                    "📝 Esempio corretto: <code>/effetticollaterali aspirin</code>\n\n" +
                    "💡 Per interazioni tra farmaci usa: <code>/interazioni farmaco1 + farmaco2</code>");
            return;
        }

        messageSender.sendMessage(chatId, "🔍 Cerco effetti collaterali...");

        try {
            //Chiama l'API per ottenere gli eventi avversi
            var events = fdaService.getAdverseEvents(drugName);

            if (events.isEmpty()) {
                messageSender.sendMessage(chatId, "✅ Nessun effetto collaterale recente registrato.");
                return;
            }

            StringBuilder response = new StringBuilder();
            response.append(String.format("⚠️ <b>Effetti Collaterali - %s</b>\n\n", drugName));

            //Mostra il numero totale di segnalazioni
            int total = ((Number) events.get("total")).intValue();
            response.append(String.format("📊 Segnalazioni: <b>%d</b>\n\n", total));

            //SuppressWarnings perché il cast da Object a Map non può essere verificato a compile-time
            //Ma sappiamo che parseAdverseEvents ritorna sempre una Map di questo tipo
            @SuppressWarnings("unchecked")
            var reactions = (java.util.Map<String, Integer>) events.get("topReactions");
            if (reactions != null && !reactions.isEmpty()) {
                response.append("<b>🔴 Effetti più segnalati:</b>\n");
                int count = 0;
                //Mostra al massimo i primi 10 effetti collaterali più comuni
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

    /*Gestisce il comando /interazioni per verificare interazioni tra più farmaci
    Cerca nella FDA database eventi avversi riportati quando i farmaci sono usati insieme*/
    public void handleDrugInteractions(long chatId, String args) {
        //Validazione input base
        if (args.isEmpty()) {
            messageSender.sendMessage(chatId, "❌ Specifica i farmaci separati da +\n\n" +
                    "📝 Esempio corretto: <code>/interazioni aspirin + ibuprofen</code>");
            return;
        }

        //Divide i farmaci usando il simbolo +
        String[] drugs = args.split("\\+");
        if (drugs.length < 2) {
            messageSender.sendMessage(chatId, "❌ Specifica almeno due farmaci separati da +\n\n" +
                    "📝 Esempio corretto: <code>/interazioni aspirin + ibuprofen</code>");
            return;
        }

        //Pulisce gli spazi e valida ogni singolo farmaco
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
            //Chiama l'API per cercare eventi avversi con tutti i farmaci insieme
            var interactions = fdaService.checkDrugInteractions(drugs);

            //Se non trova interazioni significative, è una buona notizia
            if (interactions.isEmpty() || ((Number) interactions.get("count")).intValue() == 0) {
                messageSender.sendMessage(chatId, "✅ <b>Nessuna interazione grave segnalata</b>\n\n" +
                        "⚠️ <i>Consulta sempre un medico.</i>");
                return;
            }

            //Costruisce la risposta con le interazioni trovate
            int count = ((Number) interactions.get("count")).intValue();
            StringBuilder response = new StringBuilder();
            response.append("⚠️ <b>POSSIBILI INTERAZIONI</b>\n\n");
            response.append("Farmaci: ").append(String.join(" + ", drugs)).append("\n\n");
            response.append(String.format("📊 <b>%d segnalazioni</b> di eventi avversi.\n\n", count));

            //Mostra le reazioni più comuni quando i farmaci sono presi insieme
            @SuppressWarnings("unchecked")
            var commonReactions = (java.util.List<String>) interactions.get("commonReactions");
            if (commonReactions != null && !commonReactions.isEmpty()) {
                response.append("<b>🔴 Reazioni comuni:</b>\n");
                //Mostra al massimo 8 reazioni
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

    //Gestisce il comando /mystats per mostrare le statistiche personali dell'utente
    public void handleMyStats(long chatId) {
        try {
            String stats = statsService.getUserStats(chatId);
            messageSender.sendMessage(chatId, stats);
        } catch (Exception e) {
            System.out.println("Errore statistiche: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore nel recuperare le statistiche.");
        }
    }

    //Gestisce il comando /statistiche per mostrare statistiche globali del bot
    public void handleGlobalStats(long chatId) {
        try {
            String stats = statsService.getGlobalStats();
            messageSender.sendMessage(chatId, stats);
        } catch (Exception e) {
            System.out.println("Errore statistiche globali: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore nel recuperare le statistiche.");
        }
    }

    //Gestisce il comando /recenti per mostrare le ultime ricerche dell'utente
    public void handleRecentSearches(long chatId) {
        try {
            String recent = statsService.getRecentSearches(chatId);
            messageSender.sendMessage(chatId, recent);
        } catch (Exception e) {
            System.out.println("Errore ricerche recenti: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore nel recuperare le ricerche.");
        }
    }

    //Gestisce l'aggiunta di un bookmark tramite bottone inline
    public void handleBookmarkAdd(long chatId, String drugName) {
        try {
            //Controlla se il farmaco è già nei preferiti
            boolean alreadySaved = bookmarkService.isBookmarked(chatId, drugName);
            
            if (alreadySaved) {
                //Se già salvato, mostra messaggio con bottone per vedere i richiami
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
                //Salva il farmaco nei preferiti
                bookmarkService.addBookmark(chatId, drugName);
                
                //Mostra conferma con bottone per i richiami
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

    /*Gestisce il comando /bookmarks con le sue sotto-azioni
    Supporta: list (mostra tutti), add (aggiungi), remove (rimuovi)*/
    public void handleBookmarks(long chatId, String args) {
        //Divide il comando in azione e parametro
        String[] parts = args.split("\\s+", 2);
        String action = parts.length > 0 ? parts[0].toLowerCase() : "";
        String drugName = parts.length > 1 ? parts[1].trim() : "";

        try {
            switch (action) {
                case "add" -> {
                    //Validazione nome farmaco
                    if (drugName.isEmpty() || drugName.length() < 2) {
                        messageSender.sendMessage(chatId, "❌ Nome farmaco non valido!\n\n" +
                                "📝 Esempio corretto: <code>/bookmarks add aspirin</code>");
                        return;
                    }
                    
                    //Controlla se è già salvato
                    boolean alreadySaved = bookmarkService.isBookmarked(chatId, drugName);
                    if (alreadySaved) {
                        messageSender.sendMessage(chatId, "ℹ️ <b>\"" + drugName + "\"</b> è già nei tuoi preferiti!");
                    } else {
                        bookmarkService.addBookmark(chatId, drugName);
                        messageSender.sendMessage(chatId, "⭐ Farmaco salvato!");
                    }
                }
                case "remove" -> {
                    //Validazione nome farmaco
                    if (drugName.isEmpty()) {
                        messageSender.sendMessage(chatId, "❌ Specifica il farmaco da rimuovere!\n\n" +
                                "📝 Esempio corretto: <code>/bookmarks remove aspirin</code>");
                        return;
                    }
                    
                    //Controlla se il farmaco è nei preferiti prima di rimuoverlo
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
                    //Mostra la lista completa dei preferiti
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

    //==================== METODI DI FORMATTAZIONE ====================

    /*Formatta le informazioni di un farmaco in modo leggibile
    Mostra: nome, principio attivo, produttore, indicazioni terapeutiche*/
    private String formatDrugInfo(Drug drug, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<b>%d. %s</b>\n", index, drug.getBrandName()));

        //Aggiunge il nome generico se disponibile
        if (drug.getGenericName() != null && !drug.getGenericName().isEmpty()) {
            sb.append(String.format("   📋 <i>Principio attivo:</i> %s\n", drug.getGenericName()));
        }

        //Aggiunge il produttore se disponibile
        if (drug.getManufacturer() != null && !drug.getManufacturer().isEmpty()) {
            sb.append(String.format("   🏭 <i>Produttore:</i> %s\n", drug.getManufacturer()));
        }

        //Aggiunge le indicazioni terapeutiche se disponibili
        if (drug.getIndications() != null && !drug.getIndications().isEmpty()) {
            String indications = formatIndications(drug.getIndications());
            sb.append("   💊 <i>Indicazioni:</i>\n");
            sb.append(indications);
        }

        return sb.toString();
    }

    /*Formatta le indicazioni terapeutiche in modo leggibile
    Divide il testo in frasi e limita a 300 caratteri per evitare messaggi troppo lunghi*/
    private String formatIndications(String raw) {
        //Rimuove spazi multipli e normalizza il testo
        String text = raw.replaceAll("\\s+", " ").trim();
        //Divide in frasi usando i delimitatori comuni
        String[] sentences = text.split("(?<=[.!?])\\s+");

        StringBuilder formatted = new StringBuilder();
        int charCount = 0;

        //Aggiunge frasi fino al limite di 300 caratteri
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

    /*Formatta le informazioni di un richiamo FDA in modo leggibile
    Mostra: data, descrizione prodotto, motivo del richiamo, classificazione*/
    private String formatRecallInfo(Recall recall, int index) {
        StringBuilder sb = new StringBuilder();

        //Formatta la data da YYYYMMDD a YYYY-MM-DD
        String dateStr = recall.getRecallDate();
        if (dateStr != null && dateStr.length() == 8) {
            dateStr = dateStr.substring(0, 4) + "-" +
                    dateStr.substring(4, 6) + "-" +
                    dateStr.substring(6, 8);
        }

        sb.append(String.format("<b>%d. Richiamo del %s</b>\n", index, dateStr != null ? dateStr : "N/A"));

        //Aggiunge la descrizione del prodotto
        if (recall.getProductDescription() != null) {
            sb.append(String.format("   📦 %s\n\n", recall.getProductDescription()));
        }

        //Aggiunge il motivo del richiamo
        if (recall.getReasonForRecall() != null) {
            sb.append(String.format("   ⚠️ %s\n\n", recall.getReasonForRecall()));
        }

        //Aggiunge la classificazione con emoji corrispondente alla gravità
        if (recall.getClassification() != null) {
            String emoji = getClassificationEmoji(recall.getClassification());
            sb.append(String.format("   %s <b>Class %s</b>\n", emoji, recall.getClassification()));
        }

        return sb.toString();
    }

    /*Restituisce l'emoji appropriata per la classificazione del richiamo
    Class I (rosso) = più grave, Class III (giallo) = meno grave*/
    private String getClassificationEmoji(String classification) {
        return switch (classification.toUpperCase()) {
            case "I", "CLASS I" -> "🔴";
            case "II", "CLASS II" -> "🟠";
            case "III", "CLASS III" -> "🟡";
            default -> "⚪";
        };
    }
}