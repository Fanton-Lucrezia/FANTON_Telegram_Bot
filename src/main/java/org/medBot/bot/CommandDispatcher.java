package org.medBot.bot;

import org.medBot.handler.*;
import org.medBot.service.BookmarkService;
import org.medBot.service.OpenFdaService;
import org.medBot.service.StatisticsService;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.HashMap;
import java.util.Map;

/*Dispatcher che smista i comandi ai rispettivi handler
Ogni comando ha il suo handler dedicato per una migliore organizzazione del codice*/
public class CommandDispatcher {
    private final Map<String, org.medBot.handler.CommandHandler> handlers;
    private final MessageSender messageSender;

    //Costruttore che inizializza tutti gli handler e li registra nella mappa
    public CommandDispatcher(MessageSender messageSender) {
        this.messageSender = messageSender;
        this.handlers = new HashMap<>();
        
        //Crea le istanze dei servizi necessari
        OpenFdaService fdaService = new OpenFdaService();
        StatisticsService statsService = new StatisticsService();
        BookmarkService bookmarkService = new BookmarkService();
        
        //Registra tutti gli handler associandoli al loro comando
        registerHandler(new StartHandler(messageSender));
        registerHandler(new HelpHandler(messageSender));
        registerHandler(new SearchHandler(fdaService, statsService, messageSender));
        registerHandler(new RecallsHandler(fdaService, messageSender));
        registerHandler(new AdverseEventsHandler(fdaService, messageSender));
        registerHandler(new InteractionsHandler(fdaService, messageSender));
        registerHandler(new StatsHandler(statsService, messageSender));
        registerHandler(new RecentHandler(statsService, messageSender));
        registerHandler(new BookmarksHandler(bookmarkService, messageSender));
    }

    //Aggiunge un handler alla mappa usando il suo nome comando come chiave
    private void registerHandler(org.medBot.handler.CommandHandler handler) {
        handlers.put(handler.getCommandName(), handler);
    }

    //Gestisce un comando instradandolo all'handler appropriato
    //Restituisce true se il comando esiste, false altrimenti
    public boolean handleCommand(String command, long chatId, String args, String username, TelegramClient telegramClient) {
        //Rimuove lo slash iniziale e converte in lowercase
        String commandName = command.toLowerCase().replace("/", "");
        
        //Cerca l'handler nella mappa
        org.medBot.handler.CommandHandler handler = handlers.get(commandName);
        
        if (handler != null) {
            //Se l'handler esiste, delega la gestione del comando
            handler.handle(chatId, args, username, telegramClient);
            return true;
        }
        
        //Comando non trovato
        return false;
    }

    //Gestisce i callback dei bottoni inline instradandoli agli handler appropriati
    public void handleCallback(String callbackData, long chatId, TelegramClient telegramClient) {
        //Identifica il tipo di callback e lo smista all'handler corretto
        if (callbackData.startsWith("recalls:")) {
            String drugName = callbackData.substring(8);
            handlers.get("richiami").handle(chatId, drugName, null, telegramClient);
        } 
        else if (callbackData.startsWith("morerecalls:")) {
            String[] parts = callbackData.split(":", 3);
            RecallsHandler handler = (RecallsHandler) handlers.get("richiami");
            handler.handleWithOffset(chatId, parts[1], Integer.parseInt(parts[2]), telegramClient);
        } 
        else if (callbackData.startsWith("moredrugs:")) {
            String[] parts = callbackData.split(":", 3);
            SearchHandler handler = (SearchHandler) handlers.get("cerca");
            handler.handleWithOffset(chatId, parts[1], Integer.parseInt(parts[2]), telegramClient);
        } 
        else if (callbackData.startsWith("bookmark:")) {
            String drugName = callbackData.substring(9);
            BookmarksHandler handler = (BookmarksHandler) handlers.get("bookmarks");
            handler.handleAdd(chatId, drugName, telegramClient);
        }
    }

    //Verifica se un comando esiste nella mappa degli handler
    public boolean hasCommand(String command) {
        String commandName = command.toLowerCase().replace("/", "");
        return handlers.containsKey(commandName);
    }
}