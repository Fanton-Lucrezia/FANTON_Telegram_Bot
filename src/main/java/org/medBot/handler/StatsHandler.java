package org.medBot.handler;

import org.medBot.dao.DatabaseManager;
import org.medBot.util.MessageSender;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Gestisce il comando /mystats per mostrare le statistiche personali.
 */
public class StatsHandler implements CommandHandler {
    
    private final DatabaseManager dbManager;
    
    public StatsHandler(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }
    
    @Override
    public void handle(long chatId, String args, TelegramClient telegramClient) {
        try {
            int searchCount = getSearchCount(chatId);
            
            //Ottiene i farmaci più cercati dall'utente
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
            MessageSender.send(chatId, response, telegramClient);
            
        } catch (Exception e) {
            System.out.println("Errore statistiche: " + e.getMessage());
            MessageSender.send(chatId, "❌ Errore nel recuperare le statistiche.", telegramClient);
        }
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
    
    @Override
    public String getCommandName() {
        return "mystats";
    }
}