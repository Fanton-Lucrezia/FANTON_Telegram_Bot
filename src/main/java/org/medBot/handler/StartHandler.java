package org.medBot.handler;

import org.medBot.util.MessageSender;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Gestisce il comando /start.
 * Mostra il messaggio di benvenuto con il menù principale.
 */
public class StartHandler implements CommandHandler {
    
    private static final String DISCLAIMER = "\n\n⚠️ <i>Queste informazioni sono solo a scopo informativo " +
            "e non costituiscono consulenza medica. Consulta un professionista sanitario.</i>";
    
    @Override
    public void handle(long chatId, String args, TelegramClient telegramClient) {
        String welcome = "👋 Benvenuto su <b>MedBot</b>!\n\n" +
                "🔬 Bot per informazioni su farmaci usando le API della FDA americana.\n\n" +
                "🇺🇸 I dati sono in <b>inglese</b>, cerca i farmaci con nomi inglesi " +
                "(es. 'aspirin' invece di 'aspirina').\n\n" +
                "📖 Usa /help o il menù qui sotto per iniziare!";
        
        MessageSender.sendWithMenu(chatId, welcome + DISCLAIMER, telegramClient);
    }
    
    @Override
    public String getCommandName() {
        return "start";
    }
}