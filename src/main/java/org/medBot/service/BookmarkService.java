package org.medBot.service;

import org.medBot.dao.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

//Servizio che gestisce i farmaci preferiti (bookmarks) degli utenti
//Permette di salvare, rimuovere e visualizzare i farmaci preferiti
public class BookmarkService {
    private final DatabaseManager dbManager;

    //Costruttore che inizializza il database manager
    public BookmarkService() {
        this.dbManager = DatabaseManager.getInstance();
    }

    //Verifica se un farmaco è già presente nei preferiti dell'utente
    //Questo evita duplicati e permette di mostrare "Già salvato" invece di "Salva"
    public boolean isBookmarked(long chatId, String drugName) throws Exception {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM bookmarks WHERE telegram_id = ? AND LOWER(drug_name) = LOWER(?)")) {
            //Usa LOWER() per confronto case-insensitive (Aspirin = aspirin)
            pstmt.setLong(1, chatId);
            pstmt.setString(2, drugName);
            ResultSet rs = pstmt.executeQuery();
            //Restituisce true se COUNT > 0, cioè se il farmaco è già salvato
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    //Aggiunge un farmaco ai preferiti dell'utente
    //INSERT OR IGNORE evita errori se il farmaco è già salvato (vincolo UNIQUE)
    public void addBookmark(long chatId, String drugName) throws Exception {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT OR IGNORE INTO bookmarks (telegram_id, drug_name) VALUES (?, ?)")) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, drugName);
            pstmt.executeUpdate();
        }
    }

    //Rimuove un farmaco dai preferiti dell'utente
    //Usa LOWER() per trovare il farmaco indipendentemente da maiuscole/minuscole
    public void removeBookmark(long chatId, String drugName) throws Exception {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM bookmarks WHERE telegram_id = ? AND LOWER(drug_name) = LOWER(?)")) {
            pstmt.setLong(1, chatId);
            pstmt.setString(2, drugName);
            pstmt.executeUpdate();
        }
    }

    //Ottiene la lista completa dei farmaci preferiti dell'utente
    //Restituisce una stringa formattata pronta per essere inviata su Telegram
    public String getBookmarks(long chatId) throws Exception {
        List<String> bookmarks = new ArrayList<>();
        
        //Recupera tutti i bookmarks ordinati dal più recente
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT drug_name FROM bookmarks WHERE telegram_id = ? ORDER BY created_at DESC")) {
            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();
            //Aggiunge ogni farmaco alla lista
            while (rs.next()) {
                bookmarks.add(rs.getString("drug_name"));
            }
        }

        //Se non ci sono preferiti, restituisce un messaggio informativo
        if (bookmarks.isEmpty()) {
            return "📌 Nessun preferito salvato.\n\n" +
                    "Usa: <code>/bookmarks add &lt;farmaco&gt;</code>";
        }

        //Costruisce la risposta con la lista dei preferiti
        StringBuilder response = new StringBuilder("⭐ <b>I tuoi farmaci preferiti:</b>\n\n");
        for (String bookmark : bookmarks) {
            response.append("• ").append(bookmark).append("\n");
        }
        //Aggiunge suggerimenti su come usare i preferiti
        response.append("\n💡 Per cercare: /cerca &lt;nome&gt;\n");
        response.append("🗑️ Per rimuovere: <code>/bookmarks remove &lt;nome&gt;</code>");
        return response.toString();
    }
}