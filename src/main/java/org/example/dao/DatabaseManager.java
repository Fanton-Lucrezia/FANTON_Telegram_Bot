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
 * Singleton class for managing SQLite database connections.
 * Handles database initialization and provides connections to DAOs.
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;
    private final String dbPath;

    private DatabaseManager() {
        String configPath = MyConfiguration.getInstance().getProperty("DB_PATH");
        this.dbPath = configPath != null ? configPath : "./data/medbot.db";

        // Ensure data directory exists
        File dataDir = new File("./data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
            logger.info("Created data directory");
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Get a database connection.
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    /**
     * Initialize database schema if not exists.
     */
    public void initializeDatabase() {
        logger.info("Initializing database at: {}", dbPath);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Users table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    telegram_id INTEGER PRIMARY KEY,
                    username TEXT,
                    locale TEXT DEFAULT 'en',
                    search_count INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Searches history table
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

            // Drugs cache table
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

            // Recalls cache table
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

            // Create indexes for better performance
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_searches_telegram_id 
                ON searches(telegram_id)
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_searches_created_at 
                ON searches(created_at DESC)
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_drugs_brand_name 
                ON drugs_cache(brand_name)
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_drugs_generic_name 
                ON drugs_cache(generic_name)
            """);

            logger.info("Database schema initialized successfully");

            // Insert some sample data if database is empty
            insertSampleData(conn);

        } catch (SQLException e) {
            logger.error("Error initializing database", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Insert sample data for testing/demonstration purposes.
     */
    private void insertSampleData(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // Check if data already exists
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM drugs_cache");
            if (rs.next() && rs.getInt(1) > 0) {
                logger.debug("Sample data already exists, skipping insertion");
                return;
            }

            // Insert some common drugs for demonstration
            stmt.execute("""
                INSERT OR IGNORE INTO drugs_cache 
                (drug_id, brand_name, generic_name, manufacturer, indications) VALUES
                ('aspirin-001', 'Aspirin', 'Acetylsalicylic acid', 'Bayer', 
                 'Pain reliever and fever reducer. Used for headaches, muscle aches, and reducing fever.'),
                ('ibuprofen-001', 'Advil', 'Ibuprofen', 'Pfizer', 
                 'Nonsteroidal anti-inflammatory drug (NSAID) used to reduce fever and treat pain or inflammation.'),
                ('acetaminophen-001', 'Tylenol', 'Acetaminophen', 'Johnson & Johnson', 
                 'Pain reliever and fever reducer used to treat mild to moderate pain.'),
                ('amoxicillin-001', 'Amoxil', 'Amoxicillin', 'GlaxoSmithKline', 
                 'Antibiotic used to treat bacterial infections including pneumonia, bronchitis, and infections of ear, nose, throat, skin, or urinary tract.')
            """);

            logger.info("Sample data inserted successfully");

        } catch (SQLException e) {
            logger.warn("Could not insert sample data: " + e.getMessage());
        }
    }

    /**
     * Clear all cache data (for testing/admin purposes).
     */
    public void clearCache() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM drugs_cache");
            stmt.execute("DELETE FROM recalls_cache");
            logger.info("Cache cleared");
        }
    }
}