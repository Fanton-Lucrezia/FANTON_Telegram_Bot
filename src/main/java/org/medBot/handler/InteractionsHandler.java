package org.medBot.handler;

import org.medBot.service.OpenFdaService;
import org.medBot.util.MessageSender;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Gestisce il comando /interazioni per verificare interazioni tra farmaci.
 */
public class InteractionsHandler implements CommandHandler {
    
    private final OpenFdaService fdaService;
    
    public InteractionsHandler(OpenFdaService fdaService) {
        this.fdaService = fdaService;
    }
    
    @Override
    public void handle(long chatId, String args, TelegramClient telegramClient) {
        if (args.isEmpty()) {
            MessageSender.send(chatId, "❌ Specifica i farmaci da confrontare!\n\n" +
                    "📝 Esempio: <code>/interazioni aspirin + ibuprofen</code>\n\n" +
                    "💡 <b>Cosa fa:</b>\n" +
                    "Verifica se due o più farmaci hanno segnalazioni di interazioni " +
                    "negative quando usati insieme.", telegramClient);
            return;
        }
        
        //Divide i farmaci usando il separatore +
        String[] drugs = args.split("\\+");
        if (drugs.length < 2) {
            MessageSender.send(chatId, "❌ Specifica almeno due farmaci separati da +\n\n" +
                    "Esempio: <code>/interazioni aspirin + ibuprofen</code>", telegramClient);
            return;
        }
        
        //Pulisce i nomi dei farmaci
        for (int i = 0; i < drugs.length; i++) {
            drugs[i] = drugs[i].trim();
        }
        
        MessageSender.send(chatId, "🔍 Verifico interazioni tra: " + String.join(", ", drugs) + "...", 
                telegramClient);
        
        try {
            var interactions = fdaService.checkDrugInteractions(drugs);
            
            if (interactions.isEmpty() || ((Number) interactions.get("count")).intValue() == 0) {
                MessageSender.send(chatId, "✅ <b>Nessuna interazione grave segnalata</b>\n\n" +
                        "Non ci sono segnalazioni recenti di eventi avversi gravi " +
                        "quando questi farmaci vengono usati insieme.\n\n" +
                        "⚠️ <i>Importante: Consulta sempre un medico o farmacista.</i>", telegramClient);
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
            response.append("• Consulta un medico o farmacista per informazioni accurate\n\n");
            response.append("<i>Fonte: FDA Adverse Event Reporting System</i>");
            
            MessageSender.send(chatId, response.toString(), telegramClient);
            
        } catch (Exception e) {
            System.out.println("Errore verifica interazioni: " + e.getMessage());
            MessageSender.send(chatId, "❌ Errore durante la verifica delle interazioni.", telegramClient);
        }
    }
    
    @Override
    public String getCommandName() {
        return "interazioni";
    }
}