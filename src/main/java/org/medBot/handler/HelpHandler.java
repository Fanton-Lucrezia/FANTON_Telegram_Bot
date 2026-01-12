package org.medBot.handler;

import org.medBot.bot.MessageSender;
import org.telegram.telegrambots.meta.generics.TelegramClient;

//Gestisce il comando /help che mostra la lista di tutti i comandi disponibili
public class HelpHandler implements CommandHandler {
    private final MessageSender messageSender;

    public HelpHandler(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public void handle(long chatId, String args, String username, TelegramClient telegramClient) {
        String help = "<b>📋 Comandi Disponibili</b>\n\n" +
                "/start - Messaggio di benvenuto\n" +
                "/help - Mostra questa guida\n\n" +
                "<b>🔍 Ricerca Farmaci</b>\n" +
                "/cerca &lt;nome&gt; - Cerca un farmaco\n" +
                "Esempio: <code>/cerca aspirin</code>\n\n" +
                "<b>⚠️ Sicurezza</b>\n" +
                "/richiami &lt;nome|all&gt; - Controlla modifiche ai farmaci per problemi di sicurezza o efficacia\n" +
                "/effetticollaterali &lt;nome&gt; - Effetti collaterali segnalati\n" +
                "/interazioni &lt;farmaco1 + farmaco2&gt; - Verifica interazioni\n\n" +
                "<b>📊 Statistiche</b>\n" +
                "/mystats - Le tue statistiche personali\n" +
                "/statistiche - Statistiche globali del bot\n" +
                "/recenti - Farmaci cercati di recente\n" +
                "/bookmarks - Gestisci i preferiti\n\n" +
                "<b>💡 Suggerimento:</b>\n" +
                "Puoi inviare un comando senza parametri e ti chiederò di inserire le informazioni necessarie.";

        messageSender.sendMessage(chatId, help);
    }

    @Override
    public String getCommandName() {
        return "help";
    }
}