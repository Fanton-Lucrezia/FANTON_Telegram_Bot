package org.medBot.service;

import org.medBot.dao.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/*Servizio per gestire le statistiche del bot e degli utenti
Traccia ricerche, calcola statistiche globali e personali*/
public class StatisticsService {
    private final DatabaseManager dbManager;

    //Costruttore che inizializza il database manager
    public StatisticsService() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /*Registra una ricerca effettuata dall'utente nel database
    Incrementa anche il contatore di ricerche dell'utente*/
    public void recordSearch(long chatId, String query) {
        try (Connection conn = dbManager.getConnection()) {
            //Inserisce la ricerca nella tabella searches
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO searches (telegram_id, query_text) VALUES (?, ?)")) {
                pstmt.setLong(1, chatId);
                pstmt.setString(2, query);
                pstmt.executeUpdate();
            }

            //Incrementa il contatore nell'utente e aggiorna last_active
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE users SET search_count = search_count + 1, last_active = CURRENT_TIMESTAMP " +
                            "WHERE telegram_id = ?")) {
                pstmt.setLong(1, chatId);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.out.println("Errore registrazione ricerca: " + e.getMessage());
        }
    }

    /*Restituisce le statistiche personali dell'utente
    Include: numero di ricerche, farmaco più cercato*/
    public String getUserStats(long chatId) {
        try (Connection conn = dbManager.getConnection()) {
            //Recupera il numero totale di ricerche dell'utente
            int totalSearches;
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT search_count FROM users WHERE telegram_id = ?")) {
                pstmt.setLong(1, chatId);
                ResultSet rs = pstmt.executeQuery();
                totalSearches = rs.next() ? rs.getInt("search_count") : 0;
            }

            //Se l'utente non ha mai fatto ricerche, restituisce un messaggio appropriato
            if (totalSearches == 0) {
                return "📊 <b>Le Tue Statistiche</b>\n\n" +
                        "Non hai ancora effettuato ricerche.\n\n" +
                        "💡 Prova con <code>/cerca aspirin</code>";
            }

            //Trova il farmaco più cercato dall'utente
            String topDrug = "N/A";
            int topCount = 0;
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT query_text, COUNT(*) as count FROM searches " +
                            "WHERE telegram_id = ? GROUP BY LOWER(query_text) " +
                            "ORDER BY count DESC LIMIT 1")) {
                pstmt.setLong(1, chatId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    topDrug = rs.getString("query_text");
                    topCount = rs.getInt("count");
                }
            }

            //Costruisce la risposta formattata con le statistiche
            return String.format(
                    "📊 <b>Le Tue Statistiche</b>\n\n" +
                            "🔍 Ricerche totali: <b>%d</b>\n" +
                            "⭐ Farmaco preferito: <b>%s</b> (%d volte)\n\n" +
                            "💡 Usa <code>/recenti</code> per vedere le tue ultime ricerche.",
                    totalSearches, topDrug, topCount);

        } catch (Exception e) {
            System.out.println("Errore statistiche utente: " + e.getMessage());
            return "❌ Errore nel recuperare le statistiche.";
        }
    }

    /*Restituisce le statistiche globali del bot
    Include: utenti totali, ricerche totali, farmaco più cercato in assoluto*/
    public String getGlobalStats() {
        try (Connection conn = dbManager.getConnection()) {
            //Conta il numero totale di utenti registrati
            int totalUsers;
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM users")) {
                ResultSet rs = pstmt.executeQuery();
                totalUsers = rs.getInt(1);
            }

            //Conta il numero totale di ricerche effettuate
            int totalSearches;
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM searches")) {
                ResultSet rs = pstmt.executeQuery();
                totalSearches = rs.getInt(1);
            }

            //Trova il farmaco più cercato in assoluto da tutti gli utenti
            String topDrug = "N/A";
            int topCount = 0;
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT query_text, COUNT(*) as count FROM searches " +
                            "GROUP BY LOWER(query_text) ORDER BY count DESC LIMIT 1")) {
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    topDrug = rs.getString("query_text");
                    topCount = rs.getInt("count");
                }
            }

            //Conta quanti utenti hanno usato il bot negli ultimi 7 giorni
            int activeUsers;
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM users WHERE last_active > datetime('now', '-7 days')")) {
                ResultSet rs = pstmt.executeQuery();
                activeUsers = rs.getInt(1);
            }

            //Costruisce la risposta formattata con tutte le statistiche globali
            return String.format(
                    "📊 <b>Statistiche Globali</b>\n\n" +
                            "👥 Utenti totali: <b>%d</b>\n" +
                            "✅ Utenti attivi (7gg): <b>%d</b>\n" +
                            "🔍 Ricerche totali: <b>%d</b>\n" +
                            "⭐ Farmaco più cercato: <b>%s</b> (%d volte)\n",
                    totalUsers, activeUsers, totalSearches, topDrug, topCount);

        } catch (Exception e) {
            System.out.println("Errore statistiche globali: " + e.getMessage());
            return "❌ Errore nel recuperare le statistiche.";
        }
    }

    /*Restituisce le ultime 10 ricerche dell'utente in ordine cronologico inverso
    Mostra le ricerche più recenti per prime*/
    public String getRecentSearches(long chatId) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT query_text, created_at FROM searches " +
                             "WHERE telegram_id = ? ORDER BY created_at DESC LIMIT 10")) {

            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            List<String> searches = new ArrayList<>();
            while (rs.next()) {
                searches.add(rs.getString("query_text"));
            }

            //Se non ci sono ricerche recenti, restituisce un messaggio appropriato
            if (searches.isEmpty()) {
                return "🕐 <b>Ricerche Recenti</b>\n\n" +
                        "Non hai ancora effettuato ricerche.\n\n" +
                        "💡 Prova con <code>/cerca aspirin</code>";
            }

            //Costruisce la risposta formattata con tutte le ricerche recenti
            StringBuilder response = new StringBuilder();
            response.append("🕐 <b>Ricerche Recenti</b>\n\n");

            for (int i = 0; i < searches.size(); i++) {
                response.append(i + 1).append(". ").append(searches.get(i)).append("\n");
            }

            return response.toString();

        } catch (Exception e) {
            System.out.println("Errore ricerche recenti: " + e.getMessage());
            return "❌ Errore nel recuperare le ricerche.";
        }
    }
}