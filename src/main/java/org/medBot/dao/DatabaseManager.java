package org.medBot.dao;

import org.medBot.MyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gestisce la connessione al database SQLite e crea le tabelle necessarie.
 * Utilizza il pattern Singleton per garantire una sola istanza.
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;
    private final String dbPath;

    private DatabaseManager() {
        //Legge il percorso del database dal file di configurazione
        this.dbPath = MyConfiguration.getInstance().getProperty("DB_PATH");
        initializeDatabase();
    }

    /**
     * Restituisce l'istanza unica del DatabaseManager (Singleton).
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Restituisce una nuova connessione al database SQLite.
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    /**
     * Crea le tabelle del database se non esistono.
     */
    public void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            //Tabella users: memorizza informazioni sugli utenti del bot
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "telegram_id INTEGER PRIMARY KEY, " +
                            "username TEXT, " +
                            "search_count INTEGER DEFAULT 0, " +
                            "last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")");

            //Tabella searches: memorizza tutte le ricerche effettuate
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS searches (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "telegram_id INTEGER NOT NULL, " +
                            "query_text TEXT NOT NULL, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (telegram_id) REFERENCES users(telegram_id)" +
                            ")");

            //Tabella drugs_cache: cache dei farmaci per ridurre chiamate API
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS drugs_cache (" +
                            "drug_id TEXT PRIMARY KEY, " +
                            "brand_name TEXT, " +
                            "generic_name TEXT, " +
                            "manufacturer TEXT, " +
                            "indications TEXT, " +
                            "last_fetched TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")");

            //Tabella bookmarks: farmaci preferiti degli utenti
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS bookmarks (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "telegram_id INTEGER NOT NULL, " +
                            "drug_name TEXT NOT NULL, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "UNIQUE(telegram_id, drug_name), " +
                            "FOREIGN KEY (telegram_id) REFERENCES users(telegram_id)" +
                            ")");

            //Indici per migliorare le performance delle query
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_searches_telegram_id ON searches(telegram_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_searches_created_at ON searches(created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bookmarks_telegram_id ON bookmarks(telegram_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_drugs_cache_names ON drugs_cache(brand_name, generic_name)");

            logger.info("Database inizializzato: {}", dbPath);

        } catch (SQLException e) {
            logger.error("Errore inizializzazione database", e);
            throw new RuntimeException("Impossibile inizializzare il database", e);
        }
    }
}