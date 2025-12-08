package org.example.bot;

import org.example.MyConfiguration;
import org.example.dao.UserDao;
import org.example.model.Drug;
import org.example.model.Recall;
import org.example.service.OpenFdaService;
import org.example.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Main bot class that handles all Telegram interactions.
 * Implements LongPollingSingleThreadUpdateConsumer for receiving updates.
 */
public class MedBot implements LongPollingSingleThreadUpdateConsumer {
    private static final Logger logger = LoggerFactory.getLogger(MedBot.class);
    private final TelegramClient telegramClient;
    private final OpenFdaService fdaService;
    private final UserDao userDao;

    private static final String DISCLAIMER = "\n\n⚠️ <i>Disclaimer: Queste informazioni sono solo a scopo informativo " +
            "e non costituiscono consulenza medica. In caso di dubbi o emergenze, " +
            "contatta un professionista sanitario.</i>";

    public MedBot() {
        String botToken = MyConfiguration.getInstance().getProperty("BOT_TOKEN");
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.fdaService = new OpenFdaService();
        this.userDao = new UserDao();
        logger.info("MedBot initialized");
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            String username = update.getMessage().getFrom().getUserName();

            // Register/update user
            userDao.upsertUser(chatId, username);

            logger.debug("Received message from {}: {}", chatId, messageText);

            // Handle commands
            if (messageText.startsWith("/")) {
                handleCommand(chatId, messageText, username);
            } else {
                sendMessage(chatId, "Usa /help per vedere i comandi disponibili.");
            }
        } else if (update.hasCallbackQuery()) {
            handleCallback(update);
        }
    }

    private void handleCommand(long chatId, String message, String username) {
        String[] parts = message.split(" ", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        switch (command) {
            case "/start" -> handleStart(chatId, username);
            case "/help" -> handleHelp(chatId);
            case "/searchdrug" -> handleSearchDrug(chatId, args);
            case "/recalls" -> handleRecalls(chatId, args);
            case "/mystats" -> handleMyStats(chatId);
            default -> sendMessage(chatId, "Comando non riconosciuto. Usa /help per la lista comandi.");
        }
    }

    private void handleStart(long chatId, String username) {
        String welcome = String.format(
                "👋 Benvenuto %s su <b>OpenFDA MedBot</b>!\n\n" +
                        "🔬 Questo bot fornisce informazioni su farmaci, richiami e sicurezza " +
                        "utilizzando i dati pubblici della FDA (Food and Drug Administration).\n\n" +
                        "📖 Usa /help per vedere tutti i comandi disponibili.\n\n" +
                        "🔗 Repository GitHub: github.com/FANTON_Telegram_Bot",
                username != null ? username : "utente"
        );
        sendMessage(chatId, welcome + DISCLAIMER);
    }

    private void handleHelp(long chatId) {
        String help = """
            <b>📋 Comandi disponibili:</b>
            
            /start - Messaggio di benvenuto
            /help - Mostra questo messaggio
            /searchdrug <nome> - Cerca un farmaco per nome
                <i>Esempio: /searchdrug aspirin</i>
            /recalls <nome|all> - Mostra richiami FDA
                <i>Esempio: /recalls aspirin</i>
                <i>Esempio: /recalls all</i>
            /mystats - Le tue statistiche personali
            
            <b>🔍 Come usare il bot:</b>
            1. Cerca un farmaco con /searchdrug
            2. Ricevi informazioni dettagliate
            3. Controlla eventuali richiami con /recalls
            
            <b>📊 Fonte dati:</b>
            FDA OpenFDA API - api.fda.gov
            """ + DISCLAIMER;
        sendMessage(chatId, help);
    }

    private void handleSearchDrug(long chatId, String drugName) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Specifica il nome del farmaco.\nEsempio: /searchdrug aspirin");
            return;
        }

        sendMessage(chatId, "🔍 Ricerca di \"" + drugName + "\" in corso...");

        try {
            List<Drug> drugs = fdaService.searchDrug(drugName);

            if (drugs.isEmpty()) {
                sendMessage(chatId, "❌ Nessun risultato trovato per \"" + drugName + "\".\n\n" +
                        "Suggerimenti:\n" +
                        "• Verifica l'ortografia\n" +
                        "• Prova con il nome generico (es. 'ibuprofen' invece di 'Advil')\n" +
                        "• Usa il principio attivo");
                return;
            }

            // Show top results (max 3)
            int count = Math.min(3, drugs.size());
            StringBuilder response = new StringBuilder();
            response.append(String.format("✅ Trovati %d risultati per \"<b>%s</b>\":\n\n",
                    drugs.size(), TextUtils.escapeHtml(drugName)));

            for (int i = 0; i < count; i++) {
                Drug drug = drugs.get(i);
                response.append(formatDrugInfo(drug, i + 1));
                if (i < count - 1) response.append("\n➖➖➖➖➖\n\n");
            }

            if (drugs.size() > 3) {
                response.append(String.format("\n\n<i>... e altri %d risultati</i>", drugs.size() - 3));
            }

            // Add inline keyboard for recalls check
            InlineKeyboardMarkup keyboard = createRecallsKeyboard(drugName);
            sendMessageWithKeyboard(chatId, response.toString() + DISCLAIMER, keyboard);

        } catch (Exception e) {
            logger.error("Error searching drug: " + drugName, e);
            sendMessage(chatId, "❌ Errore durante la ricerca. Riprova più tardi.\n" +
                    "Errore: " + e.getMessage());
        }
    }

    private void handleRecalls(long chatId, String drugName) {
        if (drugName.isEmpty()) {
            sendMessage(chatId, "❌ Specifica il nome del farmaco o 'all'.\nEsempio: /recalls aspirin");
            return;
        }

        sendMessage(chatId, "🔍 Ricerca richiami in corso...");

        try {
            List<Recall> recalls = drugName.equalsIgnoreCase("all")
                    ? fdaService.getRecentRecalls(10)
                    : fdaService.searchRecalls(drugName);

            if (recalls.isEmpty()) {
                sendMessage(chatId, "✅ Nessun richiamo trovato per \"" + drugName + "\".");
                return;
            }

            StringBuilder response = new StringBuilder();
            response.append(String.format("⚠️ <b>Richiami FDA</b> per \"%s\":\n\n",
                    TextUtils.escapeHtml(drugName)));

            int count = Math.min(5, recalls.size());
            for (int i = 0; i < count; i++) {
                Recall recall = recalls.get(i);
                response.append(formatRecallInfo(recall, i + 1));
                if (i < count - 1) response.append("\n➖➖➖➖➖\n\n");
            }

            if (recalls.size() > 5) {
                response.append(String.format("\n\n<i>... e altri %d richiami</i>", recalls.size() - 5));
            }

            sendMessage(chatId, response.toString() + DISCLAIMER);

        } catch (Exception e) {
            logger.error("Error searching recalls: " + drugName, e);
            sendMessage(chatId, "❌ Errore durante la ricerca richiami. Riprova più tardi.");
        }
    }

    private void handleMyStats(long chatId) {
        int searchCount = userDao.getSearchCount(chatId);
        String response = String.format("""
            📊 <b>Le tue statistiche:</b>
            
            🔍 Ricerche effettuate: %d
            👤 ID Telegram: %d
            
            <i>Continua a usare il bot per esplorare più farmaci!</i>
            """, searchCount, chatId);
        sendMessage(chatId, response);
    }

    private String formatDrugInfo(Drug drug, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<b>%d. %s</b>\n", index, TextUtils.escapeHtml(drug.getBrandName())));

        if (drug.getGenericName() != null && !drug.getGenericName().isEmpty()) {
            sb.append(String.format("   <i>Principio attivo:</i> %s\n",
                    TextUtils.escapeHtml(drug.getGenericName())));
        }

        if (drug.getManufacturer() != null && !drug.getManufacturer().isEmpty()) {
            sb.append(String.format("   <i>Produttore:</i> %s\n",
                    TextUtils.escapeHtml(drug.getManufacturer())));
        }

        if (drug.getIndications() != null && !drug.getIndications().isEmpty()) {
            String indications = TextUtils.truncate(drug.getIndications(), 200);
            sb.append(String.format("   <i>Indicazioni:</i> %s\n",
                    TextUtils.escapeHtml(indications)));
        }

        return sb.toString();
    }

    private String formatRecallInfo(Recall recall, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<b>%d. Richiamo del %s</b>\n", index, recall.getRecallDate()));
        sb.append(String.format("   <i>Prodotto:</i> %s\n",
                TextUtils.escapeHtml(recall.getProductDescription())));
        sb.append(String.format("   <i>Motivo:</i> %s\n",
                TextUtils.escapeHtml(TextUtils.truncate(recall.getReasonForRecall(), 150))));
        sb.append(String.format("   <i>Classificazione:</i> Class %s\n", recall.getClassification()));
        return sb.toString();
    }

    private InlineKeyboardMarkup createRecallsKeyboard(String drugName) {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔍 Controlla richiami")
                .callbackData("recalls:" + drugName)
                .build();

        InlineKeyboardRow row = new InlineKeyboardRow(button);
        return InlineKeyboardMarkup.builder()
                .keyboardRow(row)
                .build();
    }

    private void handleCallback(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (callbackData.startsWith("recalls:")) {
            String drugName = callbackData.substring(8);
            handleRecalls(chatId, drugName);
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending message to " + chatId, e);
        }
    }

    private void sendMessageWithKeyboard(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            logger.error("Error sending message with keyboard to " + chatId, e);
        }
    }
}