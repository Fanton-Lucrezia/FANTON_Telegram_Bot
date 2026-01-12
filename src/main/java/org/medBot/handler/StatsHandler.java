package org.medBot.handler;

import org.medBot.bot.MessageSender;
import org.medBot.service.StatisticsService;
import org.telegram.telegrambots.meta.generics.TelegramClient;

//Gestisce i comandi per le statistiche (/mystats e /statistiche)
public class StatsHandler implements CommandHandler {
    private final StatisticsService statsService;
    private final MessageSender messageSender;

    public StatsHandler(StatisticsService statsService, MessageSender messageSender) {
        this.statsService = statsService;
        this.messageSender = messageSender;
    }

    @Override
    public void handle(long chatId, String args, String username, TelegramClient telegramClient) {
        //Se viene chiamato con "global" o "statistiche", mostra stats globali
        //Altrimenti mostra le stats personali dell'utente
        if ("global".equalsIgnoreCase(args)) {
            handleGlobalStats(chatId);
        } else {
            handleMyStats(chatId);
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

    @Override
    public String getCommandName() {
        return "mystats";
    }
}