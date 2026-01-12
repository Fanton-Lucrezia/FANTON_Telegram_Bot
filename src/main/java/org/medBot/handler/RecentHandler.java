package org.medBot.handler;

import org.medBot.bot.MessageSender;
import org.medBot.service.StatisticsService;
import org.telegram.telegrambots.meta.generics.TelegramClient;

//Gestisce il comando /recenti per mostrare le ultime ricerche dell'utente
public class RecentHandler implements CommandHandler {
    private final StatisticsService statsService;
    private final MessageSender messageSender;

    public RecentHandler(StatisticsService statsService, MessageSender messageSender) {
        this.statsService = statsService;
        this.messageSender = messageSender;
    }

    @Override
    public void handle(long chatId, String args, String username, TelegramClient telegramClient) {
        try {
            String recent = statsService.getRecentSearches(chatId);
            messageSender.sendMessage(chatId, recent);
        } catch (Exception e) {
            System.out.println("Errore ricerche recenti: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore nel recuperare le ricerche.");
        }
    }

    @Override
    public String getCommandName() {
        return "recenti";
    }
}