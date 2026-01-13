package org.medBot.dao;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/*Gestore del database SQLite che implementa il pattern Singleton
Crea e gestisce la connessione al database e inizializza le tabelle necessarie*/
public class DatabaseManager {
    private static DatabaseManager instance;
    private static final String DB_URL = "jdbc:sqlite:medbot.db";

    //Costruttore privato per impedire istanziazione diretta (pattern Singleton)
    private DatabaseManager() {}

    //Restituisce l'unica istanza del DatabaseManager (pattern Singleton)
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /*Restituisce una nuova connessione al database
    Ogni chiamata crea una nuova connessione che deve essere chiusa dopo l'uso*/
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /*Inizializza il database creando tutte le tabelle necessarie
    Se le tabelle esistono già, non fa nulla grazie a CREATE TABLE IF NOT EXISTS*/
    public void initializeDatabase() {
        //Mostra il percorso assoluto del database per debug
        File dbFile = new File("medbot.db");
        System.out.println("💾 Database location: " + dbFile.getAbsolutePath());
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            //Tabella users: memorizza gli utenti che hanno usato il bot
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "telegram_id INTEGER PRIMARY KEY, " +
                            "username TEXT, " +
                            "search_count INTEGER DEFAULT 0, " +
                            "last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")");

            //Tabella searches: memorizza tutte le ricerche effettuate
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS searches (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "telegram_id INTEGER, " +
                            "query_text TEXT, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY(telegram_id) REFERENCES users(telegram_id)" +
                            ")");

            //Tabella bookmarks: memorizza i farmaci preferiti di ogni utente
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS bookmarks (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "telegram_id INTEGER, " +
                            "drug_name TEXT, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "UNIQUE(telegram_id, drug_name), " +
                            "FOREIGN KEY(telegram_id) REFERENCES users(telegram_id)" +
                            ")");

            /*Tabella drugs_cache: cache dei farmaci per ridurre chiamate API
            I dati vengono mantenuti per un periodo limitato prima di essere richiesti nuovamente*/
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS drugs_cache (" +
                            "drug_id TEXT PRIMARY KEY, " +
                            "brand_name TEXT, " +
                            "generic_name TEXT, " +
                            "manufacturer TEXT, " +
                            "indications TEXT, " +
                            "last_fetched TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")");

            System.out.println("✓ Database inizializzato con successo");

        } catch (SQLException e) {
            System.err.println("Errore inizializzazione database: " + e.getMessage());
            System.exit(-1);
        }
    }
}