package org.medBot.handler;

import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Interfaccia base per tutti i gestori di comandi.
 * Ogni comando implementa questa interfaccia per gestire la propria logica.
 */
public interface CommandHandler {
    
    /**
     * Gestisce il comando ricevuto dall'utente.
     */
    void handle(long chatId, String args, TelegramClient telegramClient);
    
    /**
     * Restituisce il nome del comando (es. "cerca", "richiami").
     */
    String getCommandName();
}