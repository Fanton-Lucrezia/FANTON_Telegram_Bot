package org.medBot.handler;

import org.medBot.service.OpenFdaService;
import org.medBot.util.MessageSender;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Gestisce il comando /farmacolegale per verificare se un farmaco
 * è una sostanza controllata.
 */
public class ControlledSubstanceHandler implements CommandHandler {
    
    private final OpenFdaService fdaService;
    
    public ControlledSubstanceHandler(OpenFdaService fdaService) {
        this.fdaService = fdaService;
    }
    
    @Override
    public void handle(long chatId, String args, TelegramClient telegramClient) {
        if (args.isEmpty()) {
            MessageSender.send(chatId, "❌ Specifica il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/farmacolegale oxycodone</code>\n\n" +
                    "💡 Verifica se è una sostanza controllata (rischio dipendenza).", telegramClient);
            return;
        }
        
        MessageSender.send(chatId, "🔍 Verifico lo status di \"" + args + "\"...", telegramClient);
        
        try {
            String schedule = fdaService.checkDrugSchedule(args);
            
            if (schedule == null) {
                MessageSender.send(chatId, "✅ \"" + args + "\" NON è una sostanza controllata.\n\n" +
                        "Basso rischio di abuso/dipendenza.", telegramClient);
            } else {
                String emoji = getScheduleEmoji(schedule);
                String description = getScheduleDescription(schedule);
                
                MessageSender.send(chatId, String.format(
                        "🚨 <b>SOSTANZA CONTROLLATA</b>\n\n" +
                                "Farmaco: <b>%s</b>\n" +
                                "Classificazione: %s <b>Schedule %s</b>\n\n" +
                                "📋 %s\n\n" +
                                "⚠️ Richiede prescrizione speciale.",
                        args, emoji, schedule, description), telegramClient);
            }
            
        } catch (Exception e) {
            System.out.println("Errore verifica sostanza: " + e.getMessage());
            MessageSender.send(chatId, "❌ Errore durante la verifica.", telegramClient);
        }
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
    
    @Override
    public String getCommandName() {
        return "farmacolegale";
    }
}