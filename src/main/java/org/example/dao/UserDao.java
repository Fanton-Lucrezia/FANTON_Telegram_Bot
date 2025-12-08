package org.example.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object for user-related database operations.
 */
public class UserDao {
    private static final Logger logger = LoggerFactory.getLogger(UserDao.class);
    private final DatabaseManager dbManager;

    public UserDao() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Insert or update user information.
     */
    public void upsertUser(long telegramId, String username) {
        String sql = """
            INSERT INTO users (telegram_id, username, last_active) 
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(telegram_id) DO UPDATE SET
                username = excluded.username,
                last_active = CURRENT_TIMESTAMP
        """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, telegramId);
            pstmt.setString(2, username);
            pstmt.executeUpdate();

            logger.debug("User upserted: {}", telegramId);

        } catch (SQLException e) {
            logger.error("Error upserting user: " + telegramId, e);
        }
    }

    /**
     * Record a search query for a user.
     */
    public void recordSearch(long telegramId, String query, int resultCount) {
        String insertSearchSql = """
            INSERT INTO searches (telegram_id, query_text, result_count) 
            VALUES (?, ?, ?)
        """;

        String updateCountSql = """
            UPDATE users 
            SET search_count = search_count + 1,
                last_active = CURRENT_TIMESTAMP
            WHERE telegram_id = ?
        """;

        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement pstmt1 = conn.prepareStatement(insertSearchSql);
                 PreparedStatement pstmt2 = conn.prepareStatement(updateCountSql)) {

                // Insert search record
                pstmt1.setLong(1, telegramId);
                pstmt1.setString(2, query);
                pstmt1.setInt(3, resultCount);
                pstmt1.executeUpdate();

                // Update user search count
                pstmt2.setLong(1, telegramId);
                pstmt2.executeUpdate();

                conn.commit();
                logger.debug("Search recorded for user: {}", telegramId);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            logger.error("Error recording search for user: " + telegramId, e);
        }
    }

    /**
     * Get the total number of searches for a user.
     */
    public int getSearchCount(long telegramId) {
        String sql = "SELECT search_count FROM users WHERE telegram_id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, telegramId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("search_count");
            }

        } catch (SQLException e) {
            logger.error("Error getting search count for user: " + telegramId, e);
        }

        return 0;
    }

    /**
     * Get user information.
     */
    public String getUsername(long telegramId) {
        String sql = "SELECT username FROM users WHERE telegram_id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, telegramId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("username");
            }

        } catch (SQLException e) {
            logger.error("Error getting username for: " + telegramId, e);
        }

        return null;
    }

    /**
     * Get total number of users.
     */
    public int getTotalUsers() {
        String sql = "SELECT COUNT(*) as total FROM users";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("total");
            }

        } catch (SQLException e) {
            logger.error("Error getting total users", e);
        }

        return 0;
    }
}