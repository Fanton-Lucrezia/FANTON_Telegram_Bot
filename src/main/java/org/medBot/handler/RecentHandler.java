package org.medBot.handler;

import org.medBot.dao.DatabaseManager;
import org.medBot.util.MessageSender;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Gestisce il comando /recenti per mostrare le ricerche recenti.
 */
public class RecentHandler implements CommandHandler {
    
    private final DatabaseManager dbManager;
    
    public RecentHandler(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }
    
    @Override
    public void handle(long chatId, String args, TelegramClient telegramClient) {
        try {
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
                    MessageSender.send(chatId, "📜 Nessuna ricerca effettuata.\n\nProva: /cerca aspirin", 
                            telegramClient);
                    return;
                }
            }
            
            recent.append("\n💡 Per cercare usa: /cerca &lt;nome&gt;");
            MessageSender.send(chatId, recent.toString(), telegramClient);
            
        } catch (Exception e) {
            System.out.println("Errore ricerche recenti: " + e.getMessage());
            MessageSender.send(chatId, "❌ Errore nel recuperare le ricerche.", telegramClient);
        }
    }
    
    @Override
    public String getCommandName() {
        return "recenti";
    }
}