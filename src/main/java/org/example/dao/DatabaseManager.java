package org.example.dao;

import org.example.MyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gestione del database SQLite.
 * Si occupa di inizializzare il DB e fornire le connessioni.
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;
    private final String dbPath;

    private DatabaseManager() {
        // Leggiamo il percorso dal config, altrimenti usiamo il default
        String configPath = MyConfiguration.getInstance().getProperty("DB_PATH");
        this.dbPath = configPath != null ? configPath : "./data/medbot.db";

        // Assicuriamoci che la cartella esista
        File dataDir = new File("./data");
        if (!dataDir.exists()) {
            boolean created = dataDir.mkdirs();
            if (created)
                logger.info("Cartella dati creata.");
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public void initializeDatabase() {
        logger.info("Inizializzazione database: {}", dbPath);

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            // Tabella Utenti
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS users (
                            telegram_id INTEGER PRIMARY KEY,
                            username TEXT,
                            locale TEXT DEFAULT 'it',
                            search_count INTEGER DEFAULT 0,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);

            // Tabella Ricerche
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS searches (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            telegram_id INTEGER,
                            query_text TEXT NOT NULL,
                            result_count INTEGER DEFAULT 0,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY(telegram_id) REFERENCES users(telegram_id)
                        )
                    """);

            // Cache Farmaci (per evitare troppe chiamate API)
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS drugs_cache (
                            drug_id TEXT PRIMARY KEY,
                            brand_name TEXT,
                            generic_name TEXT,
                            manufacturer TEXT,
                            indications TEXT,
                            last_fetched TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);

            // Cache Richiami
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS recalls_cache (
                            recall_id TEXT PRIMARY KEY,
                            product_description TEXT,
                            reason_for_recall TEXT,
                            classification TEXT,
                            recall_date TEXT,
                            fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);

            logger.info("Database pronto.");

            // Inseriamo dati di prova se vuoto
            insertSampleData(conn);

        } catch (SQLException e) {
            logger.error("Errore inizializzazione DB", e);
            throw new RuntimeException("Impossibile inizializzare il database", e);
        }
    }

    private void insertSampleData(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM drugs_cache");
            if (rs.next() && rs.getInt(1) > 0) {
                return; // Dati già presenti
            }

            // Dati iniziali per testare senza internet
            stmt.execute("""
                        INSERT OR IGNORE INTO drugs_cache
                        (drug_id, brand_name, generic_name, manufacturer, indications) VALUES
                        ('aspirin-001', 'Aspirin', 'Acetylsalicylic acid', 'Bayer',
                         'Antidolorifico e antipiretico. Usato per mal di testa e dolori muscolari.'),
                        ('ibuprofen-001', 'Advil', 'Ibuprofen', 'Pfizer',
                         'Antinfiammatorio non steroideo (FANS).'),
                        ('tachipirina-001', 'Tachipirina', 'Paracetamol', 'Angelini',
                         'Antipiretico e analgesico.')
                    """);

            logger.info("Dati di esempio inseriti.");

        } catch (SQLException e) {
            logger.warn("Errore inserimento dati sample: " + e.getMessage());
        }
    }
}
