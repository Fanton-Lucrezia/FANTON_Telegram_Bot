package org.medBot.handler;

import org.medBot.bot.MessageSender;
import org.medBot.model.Recall;
import org.medBot.service.OpenFdaService;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

/*Gestisce il comando /richiami per cercare richiami FDA
Può cercare richiami per un farmaco specifico o mostrare gli ultimi richiami generali*/
public class RecallsHandler implements CommandHandler {
    private final OpenFdaService fdaService;
    private final MessageSender messageSender;

    public RecallsHandler(OpenFdaService fdaService, MessageSender messageSender) {
        this.fdaService = fdaService;
        this.messageSender = messageSender;
    }

    @Override
    public void handle(long chatId, String args, String username, TelegramClient telegramClient) {
        handleWithOffset(chatId, args, 0, telegramClient);
    }

    //Gestisce i richiami con supporto per la paginazione
    public void handleWithOffset(long chatId, String drugName, int offset, TelegramClient telegramClient) {
        //Validazione input
        if (drugName == null || drugName.isEmpty()) {
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

    @Override
    public String getCommandName() {
        return "richiami";
    }
}