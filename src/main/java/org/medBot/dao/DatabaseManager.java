package org.medBot.dao;

import org.medBot.MyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

//Gestisce la connessione al database SQLite e crea le tabelle necessarie
//Utilizza il pattern Singleton per garantire una sola connessione al database
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;
    private final String dbPath;

    //Costruttore privato per impedire la creazione diretta di istanze
    private DatabaseManager() {
        //Legge il percorso del database dal file di configurazione
        this.dbPath = MyConfiguration.getInstance().getProperty("DB_PATH");
        initializeDatabase();
    }

    //Restituisce l'istanza unica del DatabaseManager (Singleton pattern)
    //Synchronized garantisce che sia thread-safe
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    //Restituisce una nuova connessione al database SQLite
    //Ogni volta che serve interagire con il DB, si chiama questo metodo
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    //Crea le tabelle del database se non esistono già
    //Questo metodo viene chiamato all'avvio dell'applicazione
    public void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            //Tabella users: memorizza informazioni sugli utenti che usano il bot
            //telegram_id è la chiave primaria e identifica univocamente ogni utente
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "telegram_id INTEGER PRIMARY KEY, " +
                            "username TEXT, " +
                            "search_count INTEGER DEFAULT 0, " +
                            "last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")");

            //Tabella searches: memorizza tutte le ricerche effettuate dagli utenti
            //Serve per le statistiche e per mostrare le ricerche recenti
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS searches (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "telegram_id INTEGER NOT NULL, " +
                            "query_text TEXT NOT NULL, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (telegram_id) REFERENCES users(telegram_id)" +
                            ")");

            //Tabella drugs_cache: cache dei farmaci per ridurre chiamate alle API FDA
            //Memorizza i dati dei farmaci già cercati per 24 ore
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS drugs_cache (" +
                            "drug_id TEXT PRIMARY KEY, " +
                            "brand_name TEXT, " +
                            "generic_name TEXT, " +
                            "manufacturer TEXT, " +
                            "indications TEXT, " +
                            "last_fetched TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")");

            //Tabella bookmarks: farmaci preferiti salvati dagli utenti
            //UNIQUE garantisce che un utente non possa salvare lo stesso farmaco due volte
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS bookmarks (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "telegram_id INTEGER NOT NULL, " +
                            "drug_name TEXT NOT NULL, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "UNIQUE(telegram_id, drug_name), " +
                            "FOREIGN KEY (telegram_id) REFERENCES users(telegram_id)" +
                            ")");

            //Crea indici per velocizzare le query più comuni
            //Gli indici funzionano come un indice di un libro: permettono di trovare i dati più velocemente
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_searches_telegram_id ON searches(telegram_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_searches_created_at ON searches(created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bookmarks_telegram_id ON bookmarks(telegram_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_drugs_cache_names ON drugs_cache(brand_name, generic_name)");

            logger.info("Database inizializzato: {}", dbPath);

        } catch (SQLException e) {
            //Se c'è un errore nella creazione del database, termina l'applicazione
            logger.error("Errore inizializzazione database", e);
            throw new RuntimeException("Impossibile inizializzare il database", e);
        }
    }
}