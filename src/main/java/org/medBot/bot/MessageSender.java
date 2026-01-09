package org.medBot.bot;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

/**
 * Gestisce l'invio dei messaggi al bot Telegram.
 * Separa la logica di invio messaggi dal resto del codice.
 */
public class MessageSender {
    private final TelegramClient telegramClient;

    public MessageSender(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    /**
     * Invia un messaggio di testo semplice.
     */
    public void sendMessage(long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Errore invio messaggio: " + e.getMessage());
        }
    }

    /**
     * Invia un messaggio con il menù permanente (tastiera in basso).
     */
    public void sendMessageWithMenu(long chatId, String text) {
        //Crea la tastiera permanente
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("/cerca"));
        row1.add(new KeyboardButton("/richiami"));
        row1.add(new KeyboardButton("/interazioni"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("/mystats"));
        row2.add(new KeyboardButton("/bookmarks"));
        row2.add(new KeyboardButton("/help"));

        ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2))
                .resizeKeyboard(true)
                //.persistent(true) DOMANDA
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Errore invio messaggio con menù: " + e.getMessage());
        }
    }

    /**
     * Invia un messaggio con tastiera inline (bottoni sotto il messaggio).
     */
    public void sendMessageWithKeyboard(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Errore invio messaggio con tastiera: " + e.getMessage());
        }
    }
}