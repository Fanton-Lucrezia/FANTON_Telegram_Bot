package org.medBot.handler;

import org.medBot.dao.DatabaseManager;
import org.medBot.model.Drug;
import org.medBot.service.OpenFdaService;
import org.medBot.util.MessageSender;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestisce il comando /cerca per la ricerca di farmaci.
 */
public class SearchHandler implements CommandHandler {
    
    private final OpenFdaService fdaService;
    private final DatabaseManager dbManager;
    
    public SearchHandler(OpenFdaService fdaService, DatabaseManager dbManager) {
        this.fdaService = fdaService;
        this.dbManager = dbManager;
    }
    
    @Override
    public void handle(long chatId, String args, TelegramClient telegramClient) {
        handleSearch(chatId, args, 0, telegramClient);
    }
    
    /**
     * Gestisce la ricerca con paginazione.
     */
    public void handleSearch(long chatId, String drugName, int offset, TelegramClient telegramClient) {
        //Verifica che sia stato specificato un nome
        if (drugName.isEmpty()) {
            MessageSender.send(chatId, "❌ Specifica il nome del farmaco!\n\n" +
                    "📝 Esempio: <code>/cerca aspirin</code>", telegramClient);
            return;
        }
        
        //Mostra messaggio di caricamento solo alla prima pagina
        if (offset == 0) {
            MessageSender.send(chatId, "🔍 Cerco \"" + drugName + "\"...", telegramClient);
            recordSearch(chatId, drugName);
        }
        
        try {
            List<Drug> drugs = fdaService.searchDrug(drugName);
            
            if (drugs.isEmpty()) {
                MessageSender.send(chatId, "❌ Nessun risultato per \"" + drugName + "\".\n\n" +
                        "💡 Prova con il nome generico o in inglese.", telegramClient);
                return;
            }
            
            //Implementa la paginazione dei risultati (3 risultati per pagina)
            int pageSize = 3;
            int end = Math.min(offset + pageSize, drugs.size());
            
            StringBuilder response = new StringBuilder();
            response.append(String.format("✅ <b>%d risultati</b> per \"%s\":\n\n", 
                    drugs.size(), drugName));
            
            //Formatta ogni farmaco trovato
            for (int i = offset; i < end; i++) {
                response.append(formatDrug(drugs.get(i), i + 1));
                if (i < end - 1) response.append("\n➖➖➖\n\n");
            }
            
            //Crea i bottoni per navigazione e azioni
            List<InlineKeyboardRow> rows = new ArrayList<>();
            
            //Bottone per vedere altri risultati se disponibili
            if (end < drugs.size()) {
                InlineKeyboardButton moreButton = InlineKeyboardButton.builder()
                        .text(String.format("⬇️ Altri %d risultati", drugs.size() - end))
                        .callbackData("moredrugs:" + drugName + ":" + end)
                        .build();
                rows.add(new InlineKeyboardRow(moreButton));
            }
            
            //Bottoni per azioni sul farmaco
            InlineKeyboardRow actionsRow = new InlineKeyboardRow();
            actionsRow.add(InlineKeyboardButton.builder()
                    .text("🔍 Richiami")
                    .callbackData("recalls:" + drugName)
                    .build());
            actionsRow.add(InlineKeyboardButton.builder()
                    .text("⭐ Salva")
                    .callbackData("bookmark:" + drugName)
                    .build());
            rows.add(actionsRow);
            
            InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                    .keyboard(rows)
                    .build();
            
            MessageSender.sendWithKeyboard(chatId, response.toString(), keyboard, telegramClient);
            
        } catch (Exception e) {
            System.out.println("Errore ricerca farmaco: " + e.getMessage());
            MessageSender.send(chatId, "❌ Errore durante la ricerca. Riprova più tardi.", telegramClient);
        }
    }
    
    /**
     * Formatta le informazioni di un farmaco.
     */
    private String formatDrug(Drug drug, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<b>%d. %s</b>\n", index, drug.getBrandName()));
        
        if (drug.getGenericName() != null && !drug.getGenericName().isEmpty()) {
            sb.append(String.format("   📋 <i>Principio attivo:</i> %s\n", drug.getGenericName()));
        }
        
        if (drug.getManufacturer() != null && !drug.getManufacturer().isEmpty()) {
            sb.append(String.format("   🏭 <i>Produttore:</i> %s\n", drug.getManufacturer()));
        }
        
        if (drug.getIndications() != null && !drug.getIndications().isEmpty()) {
            String indications = formatIndications(drug.getIndications());
            sb.append("   💊 <i>Indicazioni:</i>\n");
            sb.append(indications);
        }
        
        return sb.toString();
    }
    
    /**
     * Formatta le indicazioni terapeutiche in modo leggibile.
     */
    private String formatIndications(String rawIndications) {
        String text = rawIndications.replaceAll("\\s+", " ").trim();
        String[] sentences = text.split("(?<=[.!?])\\s+");
        
        StringBuilder formatted = new StringBuilder();
        int charCount = 0;
        int maxChars = 300;
        
        for (String sentence : sentences) {
            if (charCount + sentence.length() > maxChars) {
                formatted.append("   ...\n");
                break;
            }
            formatted.append("   • ").append(sentence.trim()).append("\n");
            charCount += sentence.length();
        }
        
        return formatted.toString();
    }
    
    /**
     * Registra una ricerca effettuata dall'utente nel database.
     */
    private void recordSearch(long chatId, String query) {
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
    
    @Override
    public String getCommandName() {
        return "cerca";
    }
}