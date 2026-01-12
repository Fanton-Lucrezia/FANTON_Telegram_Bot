package org.medBot.service;

import org.medBot.dao.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestisce le statistiche degli utenti.
 */
public class StatisticsService {
    private final DatabaseManager dbManager;

    public StatisticsService() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Registra una ricerca nel database.
     */
    public void recordSearch(long chatId, String searchTerm) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO searches (telegram_id, query_text) VALUES (?, ?)")) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, searchTerm);
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.out.println("Errore salvataggio ricerca: " + e.getMessage());
        }
    }

    /**
     * Ottiene le statistiche personali dell'utente.
     */
    public String getUserStats(long chatId) throws Exception {
        StringBuilder response = new StringBuilder("📊 <b>Le tue statistiche</b>\n\n");

        try (Connection conn = dbManager.getConnection()) {
            //Conta totale ricerche
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM searches WHERE telegram_id = ?")) {
                pstmt.setLong(1, chatId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    response.append("🔍 Ricerche totali: <b>").append(rs.getInt(1)).append("</b>\n");
                }
            }

            //Conta preferiti
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM bookmarks WHERE telegram_id = ?")) {
                pstmt.setLong(1, chatId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    response.append("⭐ Preferiti salvati: <b>").append(rs.getInt(1)).append("</b>\n");
                }
            }

            //Farmaco più cercato
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT query_text, COUNT(*) as count FROM searches " +
                            "WHERE telegram_id = ? GROUP BY query_text ORDER BY count DESC LIMIT 1")) {
                pstmt.setLong(1, chatId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    response.append("\n🏆 Farmaco più cercato: <b>")
                            .append(rs.getString("query_text"))
                            .append("</b> (").append(rs.getInt("count")).append(" volte)");
                }
            }

            return response.toString();
        }
    }

    /**
     * Ottiene statistiche globali del bot.
     */
    public String getGlobalStats() throws Exception {
        StringBuilder response = new StringBuilder("🌎 <b>Statistiche Globali</b>\n\n");

        try (Connection conn = dbManager.getConnection()) {
            //Utenti totali
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(DISTINCT telegram_id) FROM users")) {
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    response.append("👥 Utenti totali: <b>").append(rs.getInt(1)).append("</b>\n");
                }
            }

            //Ricerche totali
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM searches")) {
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    response.append("🔍 Ricerche totali: <b>").append(rs.getInt(1)).append("</b>\n");
                }
            }

            //Farmaci preferiti totali
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM bookmarks")) {
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    response.append("⭐ Preferiti salvati: <b>").append(rs.getInt(1)).append("</b>\n\n");
                }
            }

            //Top 5 farmaci più cercati
            response.append("<b>🔥 Top 5 farmaci più cercati:</b>\n");
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT query_text, COUNT(*) as count FROM searches " +
                            "GROUP BY query_text ORDER BY count DESC LIMIT 5")) {
                ResultSet rs = pstmt.executeQuery();
                int position = 1;
                boolean hasResults = false;
                while (rs.next()) {
                    hasResults = true;
                    response.append(String.format("%d. %s (%d ricerche)\n",
                            position++, rs.getString("query_text"), rs.getInt("count")));
                }
                
                if (!hasResults) {
                    response.append("<i>Nessuna ricerca effettuata ancora</i>\n");
                }
            }

            return response.toString();
        }
    }

    /**
     * Ottiene le ricerche recenti dell'utente.
     */
    public String getRecentSearches(long chatId) throws Exception {
        List<String> searches = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT DISTINCT query_text FROM searches " +
                             "WHERE telegram_id = ? ORDER BY created_at DESC LIMIT 10")) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                searches.add(rs.getString("query_text"));
            }
        }

        if (searches.isEmpty()) {
            return "📋 Nessuna ricerca recente.";
        }

        StringBuilder response = new StringBuilder("🕒 <b>Ricerche recenti:</b>\n\n");
        for (String search : searches) {
            response.append("• ").append(search).append("\n");
        }

        return response.toString();
    }
}