package org.medBot.handler;

import org.medBot.bot.MessageSender;
import org.medBot.service.OpenFdaService;
import org.telegram.telegrambots.meta.generics.TelegramClient;

//Gestisce il comando /effetticollaterali per cercare effetti collaterali segnalati
public class AdverseEventsHandler implements CommandHandler {
    private final OpenFdaService fdaService;
    private final MessageSender messageSender;

    public AdverseEventsHandler(OpenFdaService fdaService, MessageSender messageSender) {
        this.fdaService = fdaService;
        this.messageSender = messageSender;
    }

    @Override
    public void handle(long chatId, String args, String username, TelegramClient telegramClient) {
        //Validazione: solo un farmaco per volta
        if (args == null || args.isEmpty() || args.length() < 2) {
            messageSender.sendMessage(chatId, "❌ Nome farmaco non valido.\n\n" +
                    "📝 Esempio corretto: <code>/effetticollaterali aspirin</code>");
            return;
        }

        //Controlla se l'utente ha inserito più farmaci (usa /interazioni per quello)
        if (args.contains(",") || args.contains("+") || args.contains(";")) {
            messageSender.sendMessage(chatId, "❌ Specifica un solo farmaco per volta.\n\n" +
                    "📝 Esempio corretto: <code>/effetticollaterali aspirin</code>\n\n" +
                    "💡 Per interazioni tra farmaci usa: <code>/interazioni farmaco1 + farmaco2</code>");
            return;
        }

        messageSender.sendMessage(chatId, "🔍 Cerco effetti collaterali...");

        try {
            //Chiama l'API per ottenere gli eventi avversi
            var events = fdaService.getAdverseEvents(args);

            if (events.isEmpty()) {
                messageSender.sendMessage(chatId, "✅ Nessun effetto collaterale recente registrato.");
                return;
            }

            StringBuilder response = new StringBuilder();
            response.append(String.format("⚠️ <b>Effetti Collaterali - %s</b>\n\n", args));

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

    @Override
    public String getCommandName() {
        return "effetticollaterali";
    }
}