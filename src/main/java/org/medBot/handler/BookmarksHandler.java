package org.medBot.handler;

import org.medBot.dao.DatabaseManager;
import org.medBot.util.MessageSender;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestisce il comando /bookmarks per i farmaci preferiti.
 */
public class BookmarksHandler implements CommandHandler {
    
    private final DatabaseManager dbManager;
    
    public BookmarksHandler(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }
    
    @Override
    public void handle(long chatId, String args, TelegramClient telegramClient) {
        String[] parts = args.split("\\s+", 2);
        String action = parts.length > 0 ? parts[0].toLowerCase() : "";
        String drugName = parts.length > 1 ? parts[1].trim() : "";
        
        try {
            switch (action) {
                case "add" -> {
                    if (drugName.isEmpty()) {
                        MessageSender.send(chatId, "❌ Specifica il farmaco!\n\nEsempio: <code>/bookmarks add aspirin</code>", 
                                telegramClient);
                        return;
                    }
                    addBookmark(chatId, drugName, telegramClient);
                }
                case "remove" -> {
                    if (drugName.isEmpty()) {
                        MessageSender.send(chatId, "❌ Specifica il farmaco!\n\nEsempio: <code>/bookmarks remove aspirin</code>", 
                                telegramClient);
                        return;
                    }
                    removeBookmark(chatId, drugName, telegramClient);
                }
                case "list", "" -> showBookmarks(chatId, telegramClient);
                default -> MessageSender.send(chatId, "❌ Azione non valida.\n\n" +
                        "<b>Azioni disponibili:</b>\n" +
                        "• <code>/bookmarks</code> - lista preferiti\n" +
                        "• <code>/bookmarks add &lt;farmaco&gt;</code>\n" +
                        "• <code>/bookmarks remove &lt;farmaco&gt;</code>", telegramClient);
            }
        } catch (Exception e) {
            System.out.println("Errore gestione bookmarks: " + e.getMessage());
            MessageSender.send(chatId, "❌ Errore nella gestione dei preferiti.", telegramClient);
        }
    }
    
    /**
     * Aggiunge un farmaco ai preferiti (uso pubblico per callback).
     */
    public void addBookmark(long chatId, String drugName, TelegramClient telegramClient) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT OR IGNORE INTO bookmarks (telegram_id, drug_name) VALUES (?, ?)")) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, drugName);
            pstmt.executeUpdate();
            MessageSender.send(chatId, "⭐ Farmaco \"" + drugName + "\" aggiunto ai preferiti!", telegramClient);
        } catch (Exception e) {
            System.out.println("Errore add bookmark: " + e.getMessage());
            MessageSender.send(chatId, "❌ Errore nel salvare il preferito.", telegramClient);
        }
    }
    
    /**
     * Rimuove un farmaco dai preferiti.
     */
    private void removeBookmark(long chatId, String drugName, TelegramClient telegramClient) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM bookmarks WHERE telegram_id = ? AND drug_name = ?")) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, drugName);
            pstmt.executeUpdate();
            MessageSender.send(chatId, "🗑️ Farmaco \"" + drugName + "\" rimosso dai preferiti.", telegramClient);
        } catch (Exception e) {
            System.out.println("Errore remove bookmark: " + e.getMessage());
        }
    }
    
    /**
     * Mostra la lista dei preferiti.
     */
    private void showBookmarks(long chatId, TelegramClient telegramClient) {
        try {
            List<String> bookmarks = getBookmarks(chatId);
            if (bookmarks.isEmpty()) {
                MessageSender.send(chatId, "📌 Nessun preferito salvato.\n\n" +
                        "Usa: <code>/bookmarks add &lt;farmaco&gt;</code>", telegramClient);
            } else {
                StringBuilder response = new StringBuilder("⭐ <b>I tuoi farmaci preferiti:</b>\n\n");
                for (String bookmark : bookmarks) {
                    response.append("• ").append(bookmark).append("\n");
                }
                response.append("\n💡 Per cercare: /cerca &lt;nome&gt;\n");
                response.append("🗑️ Per rimuovere: <code>/bookmarks remove &lt;nome&gt;</code>");
                MessageSender.send(chatId, response.toString(), telegramClient);
            }
        } catch (Exception e) {
            System.out.println("Errore show bookmarks: " + e.getMessage());
        }
    }
    
    /**
     * Ottiene la lista dei preferiti.
     */
    private List<String> getBookmarks(long chatId) throws Exception {
        List<String> bookmarks = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT drug_name FROM bookmarks WHERE telegram_id = ? ORDER BY created_at DESC")) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                bookmarks.add(rs.getString("drug_name"));
            }
        }
        return bookmarks;
    }
    
    @Override
    public String getCommandName() {
        return "bookmarks";
    }
}