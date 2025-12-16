package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.example.dao.DatabaseManager;
import org.example.model.Drug;
import org.example.model.Recall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service semplificato per interagire con OpenFDA API.
 * Include caching nel database SQLite.
 */
public class OpenFdaService {
    private static final Logger logger = LoggerFactory.getLogger(OpenFdaService.class);
    private static final String FDA_BASE_URL = "https://api.fda.gov";
    private static final int CACHE_HOURS = 24;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DatabaseManager dbManager;

    public OpenFdaService() {
        this.httpClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build();
        this.objectMapper = new ObjectMapper();
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Cerca farmaci per nome (con cache).
     */
    public List<Drug> searchDrug(String searchTerm) throws IOException {
        logger.info("Searching drug: {}", searchTerm);

        // Controlla cache
        Drug cached = getCachedDrug(searchTerm);
        if (cached != null) {
            logger.info("Cache HIT for: {}", searchTerm);
            return List.of(cached);
        }

        logger.info("Cache MISS, calling FDA API");

        // Chiama API FDA
        String encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        String query = String.format(
                "(openfda.brand_name:\"%s\"+OR+openfda.generic_name:\"%s\")",
                encodedTerm, encodedTerm
        );

        String url = String.format("%s/drug/label.json?search=%s&limit=10", FDA_BASE_URL, query);

        logger.debug("FDA URL: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warn("FDA API returned: {}", response.code());
                if (response.code() == 404) {
                    throw new IOException("404 - Drug not found in FDA database");
                }
                throw new IOException("FDA API error: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);

            List<Drug> drugs = parseDrugResults(root);

            // Salva in cache il primo risultato
            if (!drugs.isEmpty()) {
                saveDrugToCache(drugs.get(0));
            }

            logger.info("Found {} drugs", drugs.size());
            return drugs;
        }
    }

    /**
     * Cerca richiami FDA.
     */
    public List<Recall> searchRecalls(String searchTerm) throws IOException {
        logger.info("Searching recalls for: {}", searchTerm);

        String encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        String url = String.format(
                "%s/drug/enforcement.json?search=product_description:\"%s\"&limit=20",
                FDA_BASE_URL, encodedTerm
        );

        logger.debug("FDA Recalls URL: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    logger.info("No recalls found (404)");
                    return List.of();
                }
                throw new IOException("FDA API error: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);

            List<Recall> recalls = parseRecallResults(root);
            logger.info("Found {} recalls", recalls.size());
            return recalls;
        }
    }

    /**
     * Ottieni ultimi richiami (tutti i farmaci).
     */
    public List<Recall> getRecentRecalls(int limit) throws IOException {
        logger.info("Fetching {} recent recalls", limit);

        String url = String.format(
                "%s/drug/enforcement.json?limit=%d&sort=report_date:desc",
                FDA_BASE_URL, limit
        );

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("FDA API error: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);

            return parseRecallResults(root);
        }
    }

    // ==================== PARSING ====================

    private List<Drug> parseDrugResults(JsonNode root) {
        List<Drug> drugs = new ArrayList<>();

        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return drugs;
        }

        for (JsonNode item : results) {
            try {
                Drug drug = new Drug();

                JsonNode openfda = item.get("openfda");
                if (openfda != null) {
                    // Brand name
                    JsonNode brandNames = openfda.get("brand_name");
                    if (brandNames != null && brandNames.isArray() && brandNames.size() > 0) {
                        drug.setBrandName(brandNames.get(0).asText());
                    }

                    // Generic name
                    JsonNode genericNames = openfda.get("generic_name");
                    if (genericNames != null && genericNames.isArray() && genericNames.size() > 0) {
                        drug.setGenericName(genericNames.get(0).asText());
                    }

                    // Manufacturer
                    JsonNode manufacturers = openfda.get("manufacturer_name");
                    if (manufacturers != null && manufacturers.isArray() && manufacturers.size() > 0) {
                        drug.setManufacturer(manufacturers.get(0).asText());
                    }
                }

                // Indications
                JsonNode indications = item.get("indications_and_usage");
                if (indications != null && indications.isArray() && indications.size() > 0) {
                    drug.setIndications(indications.get(0).asText());
                }

                drug.setLastFetched(LocalDateTime.now());

                // Aggiungi solo se ha un nome
                if (drug.getBrandName() != null || drug.getGenericName() != null) {
                    drugs.add(drug);
                }

            } catch (Exception e) {
                logger.error("Error parsing drug item", e);
            }
        }

        return drugs;
    }

    private List<Recall> parseRecallResults(JsonNode root) {
        List<Recall> recalls = new ArrayList<>();

        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return recalls;
        }

        for (JsonNode item : results) {
            try {
                Recall recall = new Recall();

                if (item.has("product_description")) {
                    recall.setProductDescription(item.get("product_description").asText());
                }

                if (item.has("reason_for_recall")) {
                    recall.setReasonForRecall(item.get("reason_for_recall").asText());
                }

                if (item.has("classification")) {
                    recall.setClassification(item.get("classification").asText());
                }

                if (item.has("report_date")) {
                    recall.setRecallDate(item.get("report_date").asText());
                }

                if (item.has("recall_number")) {
                    recall.setRecallId(item.get("recall_number").asText());
                }

                recalls.add(recall);

            } catch (Exception e) {
                logger.error("Error parsing recall item", e);
            }
        }

        return recalls;
    }

    // ==================== CACHE ====================

    private Drug getCachedDrug(String searchTerm) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT * FROM drugs_cache " +
                             "WHERE (LOWER(brand_name) LIKE LOWER(?) OR LOWER(generic_name) LIKE LOWER(?)) " +
                             "AND last_fetched > datetime('now', '-' || ? || ' hours') " +
                             "ORDER BY last_fetched DESC LIMIT 1")) {

            String pattern = "%" + searchTerm + "%";
            pstmt.setString(1, pattern);
            pstmt.setString(2, pattern);
            pstmt.setInt(3, CACHE_HOURS);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Drug drug = new Drug();
                drug.setDrugId(rs.getString("drug_id"));
                drug.setBrandName(rs.getString("brand_name"));
                drug.setGenericName(rs.getString("generic_name"));
                drug.setManufacturer(rs.getString("manufacturer"));
                drug.setIndications(rs.getString("indications"));
                return drug;
            }

        } catch (Exception e) {
            logger.error("Error getting cached drug", e);
        }

        return null;
    }

    private void saveDrugToCache(Drug drug) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT OR REPLACE INTO drugs_cache (drug_id, brand_name, generic_name, manufacturer, indications, last_fetched) " +
                             "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)")) {

            String drugId = generateDrugId(drug);
            drug.setDrugId(drugId);

            pstmt.setString(1, drugId);
            pstmt.setString(2, drug.getBrandName());
            pstmt.setString(3, drug.getGenericName());
            pstmt.setString(4, drug.getManufacturer());
            pstmt.setString(5, drug.getIndications());

            pstmt.executeUpdate();
            logger.debug("Drug cached: {}", drug.getBrandName());

        } catch (Exception e) {
            logger.error("Error caching drug", e);
        }
    }

    private String generateDrugId(Drug drug) {
        String name = drug.getBrandName() != null ? drug.getBrandName() : drug.getGenericName();
        if (name == null) name = "unknown";

        String normalized = name.toLowerCase().replaceAll("[^a-z0-9]", "-");
        return normalized + "-" + System.currentTimeMillis();
    }
}