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
        System.out.println("💾 [BOOKMARK] Aggiunta: chatId=" + chatId + ", drug=" + drugName);
        
        try (Connection conn = dbManager.getConnection()) {
            //Disabilita l'autocommit per controllo manuale della transazione
            conn.setAutoCommit(false);
            
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO bookmarks (telegram_id, drug_name) VALUES (?, ?)")) {

                pstmt.setLong(1, chatId);
                pstmt.setString(2, drugName);
                int rows = pstmt.executeUpdate();
                
                //Forza il commit delle modifiche sul database
                conn.commit();
                
                System.out.println("✅ [BOOKMARK] Salvato (" + rows + " righe inserite)");
                
            } catch (Exception e) {
                //In caso di errore, annulla le modifiche
                conn.rollback();
                System.out.println("❌ [BOOKMARK] Errore durante insert: " + e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            System.out.println("❌ [BOOKMARK] Errore aggiunta bookmark: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //Rimuove un farmaco dai preferiti dell'utente
    public void removeBookmark(long chatId, String drugName) {
        System.out.println("🗑️ [BOOKMARK] Rimozione: chatId=" + chatId + ", drug=" + drugName);
        
        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM bookmarks WHERE telegram_id = ? AND drug_name = ?")) {

                pstmt.setLong(1, chatId);
                pstmt.setString(2, drugName);
                int rows = pstmt.executeUpdate();
                
                conn.commit();
                
                System.out.println("✅ [BOOKMARK] Rimosso (" + rows + " righe eliminate)");
                
            } catch (Exception e) {
                conn.rollback();
                System.out.println("❌ [BOOKMARK] Errore durante delete: " + e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            System.out.println("❌ [BOOKMARK] Errore rimozione bookmark: " + e.getMessage());
            e.printStackTrace();
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

            boolean result = rs.next() && rs.getInt(1) > 0;
            System.out.println("🔍 [BOOKMARK] Verifica per '" + drugName + "': " + (result ? "TROVATO" : "NON TROVATO"));
            return result;

        } catch (Exception e) {
            System.out.println("❌ [BOOKMARK] Errore verifica bookmark: " + e.getMessage());
            return false;
        }
    }

    /*Restituisce la lista completa dei preferiti dell'utente formattata
    Se non ha preferiti, restituisce un messaggio appropriato*/
    public String getBookmarks(long chatId) {
        System.out.println("📋 [BOOKMARK] Recupero lista per chatId=" + chatId);
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT drug_name, created_at FROM bookmarks WHERE telegram_id = ? ORDER BY created_at DESC")) {

            pstmt.setLong(1, chatId);
            ResultSet rs = pstmt.executeQuery();

            List<String> bookmarks = new ArrayList<>();
            while (rs.next()) {
                bookmarks.add(rs.getString("drug_name"));
            }
            
            System.out.println("✅ [BOOKMARK] Trovati " + bookmarks.size() + " preferiti");

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
            System.out.println("❌ [BOOKMARK] Errore recupero bookmarks: " + e.getMessage());
            e.printStackTrace();
            return "❌ Errore nel recuperare i preferiti.";
        }
    }
}