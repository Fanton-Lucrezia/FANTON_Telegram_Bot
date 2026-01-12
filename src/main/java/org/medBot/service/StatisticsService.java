package org.medBot.service;

import org.medBot.dao.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

//Servizio che gestisce le statistiche degli utenti e globali del bot
//Traccia le ricerche effettuate e genera report statistici
public class StatisticsService {
    private final DatabaseManager dbManager;

    //Costruttore che inizializza il database manager
    public StatisticsService() {
        this.dbManager = DatabaseManager.getInstance();
    }

    //Registra una nuova ricerca nel database
    //Ogni volta che un utente cerca un farmaco, viene salvato nella tabella searches
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

    //Genera le statistiche personali dell'utente
    //Include: numero di ricerche, preferiti salvati, farmaco più cercato
    public String getUserStats(long chatId) throws Exception {
        StringBuilder response = new StringBuilder("📊 <b>Le tue statistiche</b>\n\n");

        try (Connection conn = dbManager.getConnection()) {
            //Conta il numero totale di ricerche effettuate dall'utente
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM searches WHERE telegram_id = ?")) {
                pstmt.setLong(1, chatId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    response.append("🔍 Ricerche totali: <b>").append(rs.getInt(1)).append("</b>\n");
                }
            }

            //Conta il numero di farmaci salvati nei preferiti
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM bookmarks WHERE telegram_id = ?")) {
                pstmt.setLong(1, chatId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    response.append("⭐ Preferiti salvati: <b>").append(rs.getInt(1)).append("</b>\n");
                }
            }

            //Trova il farmaco più cercato dall'utente
            //GROUP BY raggruppa le ricerche per farmaco e COUNT conta quante volte
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

    //Genera le statistiche globali del bot
    //Mostra dati aggregati di tutti gli utenti: utenti totali, ricerche, top farmaci
    public String getGlobalStats() throws Exception {
        StringBuilder response = new StringBuilder("🌎 <b>Statistiche Globali</b>\n\n");

        try (Connection conn = dbManager.getConnection()) {
            //Conta il numero totale di utenti unici che hanno usato il bot
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(DISTINCT telegram_id) FROM users")) {
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    response.append("👥 Utenti totali: <b>").append(rs.getInt(1)).append("</b>\n");
                }
            }

            //Conta il numero totale di ricerche effettuate da tutti gli utenti
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM searches")) {
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    response.append("🔍 Ricerche totali: <b>").append(rs.getInt(1)).append("</b>\n");
                }
            }

            //Conta il numero totale di farmaci salvati nei preferiti da tutti
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM bookmarks")) {
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    response.append("⭐ Preferiti salvati: <b>").append(rs.getInt(1)).append("</b>\n\n");
                }
            }

            //Genera la classifica dei 5 farmaci più cercati in assoluto
            response.append("<b>🔥 Top 5 farmaci più cercati:</b>\n");
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT query_text, COUNT(*) as count FROM searches " +
                            "GROUP BY query_text ORDER BY count DESC LIMIT 5")) {
                ResultSet rs = pstmt.executeQuery();
                int position = 1;
                boolean hasResults = false;
                //Itera sui risultati e li formatta come classifica numerata
                while (rs.next()) {
                    hasResults = true;
                    response.append(String.format("%d. %s (%d ricerche)\n",
                            position++, rs.getString("query_text"), rs.getInt("count")));
                }
                
                //Se non ci sono ancora ricerche, mostra un messaggio
                if (!hasResults) {
                    response.append("<i>Nessuna ricerca effettuata ancora</i>\n");
                }
            }

            return response.toString();
        }
    }

    //Ottiene le ultime 10 ricerche distinte dell'utente
    //Utile per ripetere velocemente ricerche precedenti
    public String getRecentSearches(long chatId) throws Exception {
        List<String> searches = new ArrayList<>();

        //Recupera le ricerche più recenti, DISTINCT evita duplicati
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

        //Se non ci sono ricerche recenti, restituisce un messaggio
        if (searches.isEmpty()) {
            return "📋 Nessuna ricerca recente.";
        }

        //Costruisce la lista delle ricerche recenti
        StringBuilder response = new StringBuilder("🕒 <b>Ricerche recenti:</b>\n\n");
        for (String search : searches) {
            response.append("• ").append(search).append("\n");
        }

        return response.toString();
    }
}