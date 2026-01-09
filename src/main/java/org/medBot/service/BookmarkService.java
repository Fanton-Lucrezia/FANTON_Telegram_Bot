package org.medBot.service;

import org.medBot.dao.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestisce i bookmark (preferiti) degli utenti.
 * Separa la logica dei bookmark dal resto del codice.
 */
public class BookmarkService {
    private final DatabaseManager dbManager;

    public BookmarkService() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Aggiunge un farmaco ai preferiti dell'utente.
     */
    public void addBookmark(long chatId, String drugName) throws Exception {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT OR IGNORE INTO bookmarks (telegram_id, drug_name) VALUES (?, ?)")) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, drugName);
            pstmt.executeUpdate();
        }
    }

    /**
     * Rimuove un farmaco dai preferiti dell'utente.
     */
    public void removeBookmark(long chatId, String drugName) throws Exception {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM bookmarks WHERE telegram_id = ? AND drug_name = ?")) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, drugName);
            pstmt.executeUpdate();
        }
    }

    /**
     * Ottiene la lista dei preferiti dell'utente.
     */
    public String getBookmarks(long chatId) throws Exception {
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

        if (bookmarks.isEmpty()) {
            return "📌 Nessun preferito salvato.\n\n" +
                    "Usa: <code>/bookmarks add &lt;farmaco&gt;</code>";
        }

        StringBuilder response = new StringBuilder("⭐ <b>I tuoi farmaci preferiti:</b>\n\n");
        for (String bookmark : bookmarks) {
            response.append("• ").append(bookmark).append("\n");
        }
        response.append("\n💡 Per cercare: /cerca &lt;nome&gt;\n");
        response.append("🗑️ Per rimuovere: <code>/bookmarks remove &lt;nome&gt;</code>");
        return response.toString();
    }
}