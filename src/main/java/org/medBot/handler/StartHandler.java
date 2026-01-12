package org.medBot.handler;

import org.medBot.bot.MessageSender;
import org.telegram.telegrambots.meta.generics.TelegramClient;

//Gestisce il comando /start che da il benvenuto all'utente
public class StartHandler implements CommandHandler {
    private final MessageSender messageSender;
    
    //Disclaimer legale mostrato nei messaggi per evitare responsabilità mediche
    private static final String DISCLAIMER = "\n\n⚠️ <i>Queste informazioni sono solo a scopo informativo " +
            "e non costituiscono consulenza medica. Consulta un professionista sanitario.</i>";

    public StartHandler(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public void handle(long chatId, String args, String username, TelegramClient telegramClient) {
        String welcome = String.format(
                "👋 Benvenuto <b>%s</b> su MedBot!\n\n" +
                        "🔬 Bot per informazioni su farmaci usando le API della FDA americana.\n\n" +
                        "🇺🇸 I dati sono in <b>inglese</b>, cerca i farmaci con nomi inglesi " +
                        "(es. 'aspirin' invece di 'aspirina').\n\n" +
                        "📖 Usa /help per iniziare!",
                username != null ? username : "utente");

        messageSender.sendMessage(chatId, welcome + DISCLAIMER);
    }

    @Override
    public String getCommandName() {
        return "start";
    }
}