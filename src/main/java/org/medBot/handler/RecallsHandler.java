package org.medBot.handler;

import org.medBot.model.Recall;
import org.medBot.service.OpenFdaService;
import org.medBot.util.MessageSender;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

/**
 * Gestisce il comando /richiami per verificare i richiami FDA.
 */
public class RecallsHandler implements CommandHandler {
    
    private final OpenFdaService fdaService;
    
    public RecallsHandler(OpenFdaService fdaService) {
        this.fdaService = fdaService;
    }
    
    @Override
    public void handle(long chatId, String args, TelegramClient telegramClient) {
        handleRecalls(chatId, args, 0, telegramClient);
    }
    
    /**
     * Gestisce la ricerca richiami con paginazione.
     */
    public void handleRecalls(long chatId, String drugName, int offset, TelegramClient telegramClient) {
        if (drugName.isEmpty()) {
            MessageSender.send(chatId, "❌ Specifica il nome del farmaco o 'all'.\n\n" +
                    "📝 Esempio: <code>/richiami aspirin</code>", telegramClient);
            return;
        }
        
        if (offset == 0) {
            MessageSender.send(chatId, "🔍 Cerco richiami...", telegramClient);
        }
        
        try {
            //Ottiene i richiami dal servizio FDA
            List<Recall> recalls = drugName.equalsIgnoreCase("all")
                    ? fdaService.getRecentRecalls(50)
                    : fdaService.searchRecalls(drugName);
            
            if (recalls.isEmpty()) {
                MessageSender.send(chatId, "✅ Nessun richiamo trovato per \"" + drugName + "\".\n\n" +
                        "🎉 Buone notizie!", telegramClient);
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
                response.append(formatRecall(recalls.get(i), i + 1));
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
                
                MessageSender.sendWithKeyboard(chatId, response.toString(), keyboard, telegramClient);
            } else {
                MessageSender.send(chatId, response.toString(), telegramClient);
            }
            
        } catch (Exception e) {
            System.out.println("Errore ricerca richiami: " + e.getMessage());
            MessageSender.send(chatId, "❌ Errore durante la ricerca richiami.", telegramClient);
        }
    }
    
    /**
     * Formatta le informazioni di un richiamo FDA.
     */
    private String formatRecall(Recall recall, int index) {
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
    
    @Override
    public String getCommandName() {
        return "richiami";
    }
}