package org.medBot.handler;

import org.medBot.util.MessageSender;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Gestisce il comando /help.
 * Mostra la lista completa dei comandi disponibili.
 */
public class HelpHandler implements CommandHandler {
    
    @Override
    public void handle(long chatId, String args, TelegramClient telegramClient) {
        String help = "<b>📋 Comandi Disponibili</b>\n\n" +
                "/start - Messaggio di benvenuto\n" +
                "/help - Mostra questa guida\n\n" +
                "<b>🔍 Ricerca Farmaci</b>\n" +
                "/cerca &lt;nome&gt; - Cerca un farmaco\n" +
                "Esempio: <code>/cerca aspirin</code>\n\n" +
                "<b>⚠️ Sicurezza</b>\n" +
                "/richiami &lt;nome|all&gt; - Controlla richiami che sono stati ritirati dal mercato per problemi di qualità, sicurezza o efficacia, per proteggere la salute pubblica\n" +
                "/farmacolegale &lt;nome&gt; - Verifica se è sostanza controllata\n" +
                "/effetticollaterali &lt;nome&gt; - Effetti collaterali segnalati\n" +
                "/interazioni &lt;farmaco1 + farmaco2&gt; - Verifica interazioni tra farmaci\n\n" +
                "<b>📊 Statistiche</b>\n" +
                "/mystats - Le tue statistiche\n" +
                "/recenti - Farmaci cercati di recente\n" +
                "/bookmarks - Gestisci i preferiti\n\n" +
                "<b>💡 Esempi:</b>\n" +
                "• <code>/cerca ibuprofen</code>\n" +
                "• <code>/richiami aspirin</code>\n" +
                "• <code>/interazioni aspirin + ibuprofen</code>\n" +
                "• <code>/bookmarks add aspirin</code>";
        
        MessageSender.send(chatId, help, telegramClient);
    }
    
    @Override
    public String getCommandName() {
        return "help";
    }
}