package org.medBot.handler;

import org.medBot.bot.MessageSender;
import org.medBot.service.BookmarkService;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/*Gestisce il comando /bookmarks con le sue sotto-azioni
Supporta: list (mostra tutti), add (aggiungi), remove (rimuovi)*/
public class BookmarksHandler implements CommandHandler {
    private final BookmarkService bookmarkService;
    private final MessageSender messageSender;

    public BookmarksHandler(BookmarkService bookmarkService, MessageSender messageSender) {
        this.bookmarkService = bookmarkService;
        this.messageSender = messageSender;
    }

    @Override
    public void handle(long chatId, String args, String username, TelegramClient telegramClient) {
        //Se non ci sono argomenti, mostra la lista
        if (args == null || args.trim().isEmpty()) {
            handleList(chatId);
            return;
        }

        //Divide il comando in azione e parametro
        String[] parts = args.split("\\s+", 2);
        String action = parts[0].toLowerCase();
        String drugName = parts.length > 1 ? parts[1].trim() : "";

        //Gestisce le diverse azioni
        switch (action) {
            case "add" -> handleAdd(chatId, drugName, telegramClient);
            case "remove" -> handleRemove(chatId, drugName);
            case "list" -> handleList(chatId);
            default -> messageSender.sendMessage(chatId, "❌ Azione non valida.\n\n" +
                    "📝 Usa: <code>/bookmarks</code>, <code>/bookmarks add &lt;nome&gt;</code>, " +
                    "<code>/bookmarks remove &lt;nome&gt;</code>");
        }
    }

    //Gestisce l'aggiunta di un bookmark
    public void handleAdd(long chatId, String drugName, TelegramClient telegramClient) {
        //Validazione nome farmaco
        if (drugName == null || drugName.isEmpty() || drugName.length() < 2) {
            messageSender.sendMessage(chatId, "❌ Nome farmaco non valido!\n\n" +
                    "📝 Esempio corretto: <code>/bookmarks add aspirin</code>");
            return;
        }

        try {
            //Controlla se è già salvato
            boolean alreadySaved = bookmarkService.isBookmarked(chatId, drugName);
            
            if (alreadySaved) {
                //Se già salvato, mostra messaggio con bottone per vedere i richiami
                InlineKeyboardButton recallsButton = InlineKeyboardButton.builder()
                        .text("🔍 Controlla richiami")
                        .callbackData("recalls:" + drugName)
                        .build();
                
                InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(recallsButton))
                        .build();
                
                messageSender.sendMessageWithKeyboard(chatId, 
                        "ℹ️ <b>\"" + drugName + "\"</b> è già nei tuoi preferiti!", keyboard);
            } else {
                //Salva il farmaco nei preferiti
                bookmarkService.addBookmark(chatId, drugName);
                
                //Mostra conferma con bottone per i richiami
                InlineKeyboardButton recallsButton = InlineKeyboardButton.builder()
                        .text("🔍 Controlla richiami")
                        .callbackData("recalls:" + drugName)
                        .build();
                
                InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(recallsButton))
                        .build();
                
                messageSender.sendMessageWithKeyboard(chatId, "⭐ Farmaco salvato!", keyboard);
            }
        } catch (Exception e) {
            System.out.println("Errore bookmark: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore nel salvare il farmaco.");
        }
    }

    //Gestisce la rimozione di un bookmark
    private void handleRemove(long chatId, String drugName) {
        //Validazione nome farmaco
        if (drugName.isEmpty()) {
            messageSender.sendMessage(chatId, "❌ Specifica il farmaco da rimuovere!\n\n" +
                    "📝 Esempio corretto: <code>/bookmarks remove aspirin</code>");
            return;
        }

        try {
            //Controlla se il farmaco è nei preferiti prima di rimuoverlo
            boolean wasBookmarked = bookmarkService.isBookmarked(chatId, drugName);
            if (!wasBookmarked) {
                messageSender.sendMessage(chatId, "❌ <b>\"" + drugName + "\"</b> non è nei tuoi preferiti.\n\n" +
                        "💡 Usa <code>/bookmarks</code> per vedere la lista.");
            } else {
                bookmarkService.removeBookmark(chatId, drugName);
                messageSender.sendMessage(chatId, "🗑️ Farmaco rimosso dai preferiti.");
            }
        } catch (Exception e) {
            System.out.println("Errore rimozione bookmark: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore nella rimozione.");
        }
    }

    //Mostra la lista completa dei preferiti
    private void handleList(long chatId) {
        try {
            String bookmarks = bookmarkService.getBookmarks(chatId);
            messageSender.sendMessage(chatId, bookmarks);
        } catch (Exception e) {
            System.out.println("Errore lista bookmarks: " + e.getMessage());
            messageSender.sendMessage(chatId, "❌ Errore nel recuperare i preferiti.");
        }
    }

    @Override
    public String getCommandName() {
        return "bookmarks";
    }
}