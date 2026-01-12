package org.medBot.bot;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

//Classe helper per semplificare l'invio di messaggi su Telegram
//Centralizza la logica di invio messaggi e gestisce gli errori
public class MessageSender {
    private final TelegramClient telegramClient;

    //Costruttore che riceve il client Telegram per inviare i messaggi
    public MessageSender(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    //Invia un messaggio di testo semplice senza bottoni
    //chatId: identificativo della chat dove inviare il messaggio
    //text: testo del messaggio (può contenere HTML per formattazione)
    public void sendMessage(long chatId, String text) {
        //Crea l'oggetto SendMessage con i parametri necessari
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")  //Permette di usare tag HTML per grassetto, corsivo, ecc.
                .build();

        try {
            //Invia il messaggio tramite le API di Telegram
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            //Se c'è un errore nell'invio, lo stampa ma non blocca l'esecuzione
            System.out.println("Errore invio messaggio: " + e.getMessage());
        }
    }

    //Invia un messaggio con bottoni inline (tastiera personalizzata)
    //I bottoni inline appaiono sotto il messaggio e permettono interazioni rapide
    public void sendMessageWithKeyboard(long chatId, String text, InlineKeyboardMarkup keyboard) {
        //Crea il messaggio includendo sia il testo che i bottoni
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(keyboard)  //Aggiunge i bottoni inline al messaggio
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Errore invio messaggio: " + e.getMessage());
        }
    }
}