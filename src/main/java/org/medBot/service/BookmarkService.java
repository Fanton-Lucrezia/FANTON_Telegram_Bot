package org.medBot.service;

import org.medBot.dao.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/*Servizio per gestire i bookmark (preferiti) degli utenti
Permette di salvare, rimuovere e visualizzare i farmaci preferiti di ogni utente*/
public class BookmarkService {
    private final DatabaseManager dbManager;

    //Costruttore che inizializza il database manager
    public BookmarkService() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /*Aggiunge un farmaco ai preferiti dell'utente
    Se il bookmark esiste già, non fa nulla grazie a INSERT OR IGNORE*/
    public void addBookmark(long chatId, String drugName) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT OR IGNORE INTO bookmarks (telegram_id, drug_name) VALUES (?, ?)")) {

            pstmt.setLong(1, chatId);
            pstmt.setString(2, drugName);
            pstmt.executeUpdate();

        } catch (Exception e) {
            System.out.println("Errore aggiunta bookmark: " + e.getMessage());
        }
    }

    //Rimuove un farmaco dai preferiti dell'utente
    public void removeBookmark(long chatId, String drugName) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM bookmarks WHERE telegram_id = ? AND drug_name = ?")) {

            pstmt.setLong(1, chatId);
            pstmt.setString(2, drugName);
            pstmt.executeUpdate();

        } catch (Exception e) {
            System.out.println("Errore rimozione bookmark: " + e.getMessage());
        }
    }

    //Verifica se un farmaco è già nei preferiti dell'utente
    public boolean isBookmarked(long chatId, String drugName) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM bookmarks WHERE telegram_id = ? AND LOWER(drug_name) = LOWER(?)")) {

            pstmt.setLong(1, chatId);
            pstmt.setString(2, drugName);
            ResultSet rs = pstmt.executeQuery();

            return rs.next() && rs.getInt(1) > 0;

        } catch (Exception e) {
            System.out.println("Errore verifica bookmark: " + e.getMessage());
            return false;
        }
    }

    /*Restituisce la lista completa dei preferiti dell'utente formattata
    Se non ha preferiti, restituisce un messaggio appropriato*/
    public String getBookmarks(long chatId) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT drug_name, created_at FROM bookmarks WHERE telegram_id = ? ORDER BY created_at DESC")) {

            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            List<String> bookmarks = new ArrayList<>();
            while (rs.next()) {
                bookmarks.add(rs.getString("drug_name"));
            }

            //Se non ci sono preferiti, restituisce un messaggio vuoto
            if (bookmarks.isEmpty()) {
                return "📋 <b>I Tuoi Preferiti</b>\n\n" +
                        "Non hai ancora salvato nessun farmaco.\n\n" +
                        "💡 Usa <code>/bookmarks add &lt;nome&gt;</code> per aggiungere.";
            }

            //Costruisce la risposta formattata con tutti i preferiti
            StringBuilder response = new StringBuilder();
            response.append("📋 <b>I Tuoi Preferiti</b> (").append(bookmarks.size()).append(")\n\n");

            for (int i = 0; i < bookmarks.size(); i++) {
                response.append(i + 1).append(". ").append(bookmarks.get(i)).append("\n");
            }

            response.append("\n💡 Usa <code>/bookmarks remove &lt;nome&gt;</code> per rimuovere.");

            return response.toString();

        } catch (Exception e) {
            System.out.println("Errore recupero bookmarks: " + e.getMessage());
            return "❌ Errore nel recuperare i preferiti.";
        }
    }
}