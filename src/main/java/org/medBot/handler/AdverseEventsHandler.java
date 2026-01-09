package org.medBot.handler;

import org.medBot.service.OpenFdaService;
import org.medBot.util.MessageSender;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Gestisce il comando /effetticollaterali per mostrare
 * gli effetti collaterali segnalati per un farmaco.
 */
public class AdverseEventsHandler implements CommandHandler {
    
    private final OpenFdaService fdaService;
    
    public AdverseEventsHandler(OpenFdaService fdaService) {
        this.fdaService = fdaService;
    }
    
    @Override
    public void handle(long chatId, String args, TelegramClient telegramClient) {
        if (args.isEmpty()) {
            MessageSender.send(chatId, "❌ Specifica il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/effetticollaterali aspirin</code>", telegramClient);
            return;
        }
        
        MessageSender.send(chatId, "🔍 Cerco effetti collaterali per \"" + args + "\"...", telegramClient);
        
        try {
            var events = fdaService.getAdverseEvents(args);
            
            if (events.isEmpty()) {
                MessageSender.send(chatId, "✅ Nessun effetto collaterale recente registrato.", telegramClient);
                return;
            }
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("⚠️ <b>Effetti Collaterali - %s</b>\n\n", args));
            
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
            MessageSender.send(chatId, response.toString(), telegramClient);
            
        } catch (Exception e) {
            System.out.println("Errore effetti collaterali: " + e.getMessage());
            MessageSender.send(chatId, "❌ Errore durante la ricerca.", telegramClient);
        }
    }
    
    @Override
    public String getCommandName() {
        return "effetticollaterali";
    }
}