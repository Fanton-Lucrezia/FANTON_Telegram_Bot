package org.medBot.service;

import org.medBot.dao.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Gestisce le statistiche degli utenti.
 * Separa la logica delle statistiche dal resto del codice.
 */
public class StatisticsService {
    private final DatabaseManager dbManager;

    public StatisticsService() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Registra una ricerca effettuata dall'utente.
     */
    public void recordSearch(long chatId, String query) {
        try (Connection conn = dbManager.getConnection()) {
            //Inserisce nella tabella searches
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO searches (telegram_id, query_text) VALUES (?, ?)")) {
                pstmt.setLong(1, chatId);
                pstmt.setString(2, query);
                pstmt.executeUpdate();
            }

            //Incrementa il contatore nell'utente
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE users SET search_count = search_count + 1, last_active = CURRENT_TIMESTAMP WHERE telegram_id = ?")) {
                pstmt.setLong(1, chatId);
                pstmt.executeUpdate();
            }

        } catch (Exception e) {
            System.out.println("Errore registrazione ricerca: " + e.getMessage());
        }
    }

    /**
     * Ottiene le statistiche dell'utente.
     */
    public String getUserStats(long chatId) throws Exception {
        int searchCount = getSearchCount(chatId);

        //Ottiene i farmaci più cercati
        StringBuilder topDrugs = new StringBuilder();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT query_text, COUNT(*) as count FROM searches " +
                             "WHERE telegram_id = ? " +
                             "GROUP BY LOWER(query_text) " +
                             "ORDER BY count DESC LIMIT 5")) {

            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            int rank = 1;
            while (rs.next()) {
                topDrugs.append(String.format("%d. %s (%d volte)\n",
                        rank++, rs.getString("query_text"), rs.getInt("count")));
            }
        }

        String response = String.format(
                "📊 <b>Le tue statistiche:</b>\n\n" +
                        "🔍 Ricerche totali: <b>%d</b>\n" +
                        "👤 ID Telegram: <code>%d</code>\n\n",
                searchCount, chatId);

        if (topDrugs.length() > 0) {
            response += "💊 <b>Farmaci più cercati:</b>\n" + topDrugs + "\n";
        }

        response += "💡 Usa /recenti per le ultime ricerche!";
        return response;
    }

    /**
     * Ottiene le ricerche recenti dell'utente.
     */
    public String getRecentSearches(long chatId) throws Exception {
        StringBuilder recent = new StringBuilder();
        recent.append("📜 <b>Ricerche recenti:</b>\n\n");

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT DISTINCT query_text, MAX(created_at) as last_search " +
                             "FROM searches WHERE telegram_id = ? " +
                             "GROUP BY LOWER(query_text) " +
                             "ORDER BY last_search DESC LIMIT 10")) {

            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            int count = 0;
            while (rs.next()) {
                count++;
                recent.append(String.format("• %s\n", rs.getString("query_text")));
            }

            if (count == 0) {
                return "📜 Nessuna ricerca effettuata.\n\nProva: /cerca aspirin";
            }
        }

        recent.append("\n💡 Per cercare usa: /cerca &lt;nome&gt;");
        return recent.toString();
    }

    /**
     * Ottiene il numero totale di ricerche dell'utente.
     */
    private int getSearchCount(long chatId) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT search_count FROM users WHERE telegram_id = ?")) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("search_count");
            }
        } catch (Exception e) {
            System.out.println("Errore conteggio ricerche: " + e.getMessage());
        }
        return 0;
    }
}