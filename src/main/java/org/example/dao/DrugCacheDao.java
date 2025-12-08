package org.example.dao;

import org.example.model.Drug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for drug cache operations.
 */
public class DrugCacheDao {
    private static final Logger logger = LoggerFactory.getLogger(DrugCacheDao.class);
    private final DatabaseManager dbManager;

    public DrugCacheDao() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Save or update drug in cache.
     */
    public void saveDrugToCache(Drug drug) {
        String sql = """
            INSERT INTO drugs_cache (drug_id, brand_name, generic_name, manufacturer, indications, last_fetched)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(drug_id) DO UPDATE SET
                brand_name = excluded.brand_name,
                generic_name = excluded.generic_name,
                manufacturer = excluded.manufacturer,
                indications = excluded.indications,
                last_fetched = excluded.last_fetched
        """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Generate ID if not present
            String drugId = drug.getDrugId();
            if (drugId == null || drugId.isEmpty()) {
                drugId = generateDrugId(drug);
                drug.setDrugId(drugId);
            }

            pstmt.setString(1, drugId);
            pstmt.setString(2, drug.getBrandName());
            pstmt.setString(3, drug.getGenericName());
            pstmt.setString(4, drug.getManufacturer());
            pstmt.setString(5, drug.getIndications());
            pstmt.setTimestamp(6, Timestamp.valueOf(drug.getLastFetched()));

            pstmt.executeUpdate();
            logger.debug("Drug cached: {}", drug.getBrandName());

        } catch (SQLException e) {
            logger.error("Error caching drug: " + drug.getBrandName(), e);
        }
    }

    /**
     * Get cached drug by search term (searches both brand and generic names).
     */
    public Drug getCachedDrug(String searchTerm) {
        String sql = """
            SELECT * FROM drugs_cache 
            WHERE LOWER(brand_name) LIKE LOWER(?) 
               OR LOWER(generic_name) LIKE LOWER(?)
            ORDER BY last_fetched DESC
            LIMIT 1
        """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String pattern = "%" + searchTerm + "%";
            pstmt.setString(1, pattern);
            pstmt.setString(2, pattern);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToDrug(rs);
            }

        } catch (SQLException e) {
            logger.error("Error getting cached drug: " + searchTerm, e);
        }

        return null;
    }

    /**
     * Get all cached drugs (for admin/stats purposes).
     */
    public List<Drug> getAllCachedDrugs() {
        String sql = "SELECT * FROM drugs_cache ORDER BY brand_name";
        List<Drug> drugs = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                drugs.add(mapResultSetToDrug(rs));
            }

        } catch (SQLException e) {
            logger.error("Error getting all cached drugs", e);
        }

        return drugs;
    }

    /**
     * Get count of cached drugs.
     */
    public int getCachedDrugCount() {
        String sql = "SELECT COUNT(*) as total FROM drugs_cache";

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt("total");
            }

        } catch (SQLException e) {
            logger.error("Error getting cached drug count", e);
        }

        return 0;
    }

    /**
     * Clear old cache entries (older than specified hours).
     */
    public int clearOldCache(int hoursOld) {
        String sql = "DELETE FROM drugs_cache WHERE last_fetched < datetime('now', '-' || ? || ' hours')";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, hoursOld);
            int deleted = pstmt.executeUpdate();

            logger.info("Cleared {} old cache entries", deleted);
            return deleted;

        } catch (SQLException e) {
            logger.error("Error clearing old cache", e);
            return 0;
        }
    }

    private Drug mapResultSetToDrug(ResultSet rs) throws SQLException {
        Drug drug = new Drug();
        drug.setDrugId(rs.getString("drug_id"));
        drug.setBrandName(rs.getString("brand_name"));
        drug.setGenericName(rs.getString("generic_name"));
        drug.setManufacturer(rs.getString("manufacturer"));
        drug.setIndications(rs.getString("indications"));

        Timestamp timestamp = rs.getTimestamp("last_fetched");
        if (timestamp != null) {
            drug.setLastFetched(timestamp.toLocalDateTime());
        }

        return drug;
    }

    private String generateDrugId(Drug drug) {
        String name = drug.getBrandName() != null ? drug.getBrandName() : drug.getGenericName();
        if (name == null) name = "unknown";

        // Simple ID generation: lowercase + timestamp
        String normalized = name.toLowerCase().replaceAll("[^a-z0-9]", "-");
        return normalized + "-" + System.currentTimeMillis();
    }
}