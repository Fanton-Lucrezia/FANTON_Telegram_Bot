package org.medBot.handler;

import org.medBot.bot.MessageSender;
import org.medBot.model.Drug;
import org.medBot.service.OpenFdaService;
import org.medBot.service.StatisticsService;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;

//Gestisce il comando /cerca per la ricerca di farmaci nelle API FDA
public class SearchHandler implements CommandHandler {
    private final OpenFdaService fdaService;
    private final StatisticsService statsService;
    private final MessageSender messageSender;

    public SearchHandler(OpenFdaService fdaService, StatisticsService statsService, MessageSender messageSender) {
        this.fdaService = fdaService;
        this.statsService = statsService;
        this.messageSender = messageSender;
    }

    @Override
    public void handle(long chatId, String args, String username, TelegramClient telegramClient) {
        handleWithOffset(chatId, args, 0, telegramClient);
    }

    /*Gestisce la ricerca con paginazione
    offset: posizione da cui iniziare a mostrare i risultati (0 per la prima pagina)*/
    public void handleWithOffset(long chatId, String drugName, int offset, TelegramClient telegramClient) {
        //Validazione input: controlla che il nome sia valido
        if (drugName == null || drugName.isEmpty() || drugName.length() < 2) {
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
            actionsRow.add(InlineKeyboardButton.builder()
                    .text("⭐ Salva")
                    .callbackData("bookmark:" + drugName)
                    .build());
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

    @Override
    public String getCommandName() {
        return "cerca";
    }
}