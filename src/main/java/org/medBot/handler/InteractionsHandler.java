package org.medBot.handler;

import org.medBot.bot.MessageSender;
import org.medBot.service.OpenFdaService;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/*Gestisce il comando /interazioni per verificare interazioni tra più farmaci
Cerca nella FDA database eventi avversi riportati quando i farmaci sono usati insieme*/
public class InteractionsHandler implements CommandHandler {
    private final OpenFdaService fdaService;
    private final MessageSender messageSender;

    public InteractionsHandler(OpenFdaService fdaService, MessageSender messageSender) {
        this.fdaService = fdaService;
        this.messageSender = messageSender;
    }

    @Override
    public void handle(long chatId, String args, String username, TelegramClient telegramClient) {
        //Validazione input base
        if (args == null || args.isEmpty()) {
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

    @Override
    public String getCommandName() {
        return "interazioni";
    }
}