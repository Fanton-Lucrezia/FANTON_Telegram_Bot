package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.example.dao.DrugCacheDao;
import org.example.model.Drug;
import org.example.model.Recall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for interacting with the OpenFDA API.
 * Handles drug searches, recalls, and caching.
 */
public class OpenFdaService {
    private static final Logger logger = LoggerFactory.getLogger(OpenFdaService.class);
    private static final String FDA_BASE_URL = "https://api.fda.gov";
    private static final int CACHE_TTL_HOURS = 24;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DrugCacheDao cacheDao;

    public OpenFdaService() {
        this.httpClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build();
        this.objectMapper = new ObjectMapper();
        this.cacheDao = new DrugCacheDao();
    }

    /**
     * Search for drugs by name (brand or generic).
     * Checks cache first, then queries FDA API.
     */
    public List<Drug> searchDrug(String searchTerm) throws IOException {
        logger.info("Searching drug: {}", searchTerm);

        // Check cache first
        Drug cachedDrug = cacheDao.getCachedDrug(searchTerm);
        if (cachedDrug != null && isCacheValid(cachedDrug.getLastFetched())) {
            logger.debug("Cache hit for: {}", searchTerm);
            return List.of(cachedDrug);
        }

        // Build search query - search both brand and generic names
        String encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        String query = String.format(
                "(openfda.brand_name:%s+OR+openfda.generic_name:%s)",
                encodedTerm, encodedTerm
        );

        String url = String.format("%s/drug/label.json?search=%s&limit=10",
                FDA_BASE_URL, query);

        logger.debug("FDA API URL: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("FDA API error: {} - {}", response.code(), response.message());
                throw new IOException("FDA API error: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);

            List<Drug> drugs = parseDrugResults(root);

            // Cache the first result
            if (!drugs.isEmpty()) {
                cacheDao.saveDrugToCache(drugs.get(0));
            }

            return drugs;
        }
    }

    /**
     * Search for FDA enforcement reports (recalls) related to a drug.
     */
    public List<Recall> searchRecalls(String searchTerm) throws IOException {
        logger.info("Searching recalls for: {}", searchTerm);

        String encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        String url = String.format(
                "%s/drug/enforcement.json?search=product_description:%s&limit=20",
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
                    logger.info("No recalls found for: {}", searchTerm);
                    return List.of();
                }
                throw new IOException("FDA API error: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);

            return parseRecallResults(root);
        }
    }

    /**
     * Get recent recalls (all products).
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

    private List<Drug> parseDrugResults(JsonNode root) {
        List<Drug> drugs = new ArrayList<>();

        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return drugs;
        }

        for (JsonNode item : results) {
            try {
                Drug drug = new Drug();

                // Extract brand name
                JsonNode openfda = item.get("openfda");
                if (openfda != null) {
                    JsonNode brandNames = openfda.get("brand_name");
                    if (brandNames != null && brandNames.isArray() && brandNames.size() > 0) {
                        drug.setBrandName(brandNames.get(0).asText());
                    }

                    JsonNode genericNames = openfda.get("generic_name");
                    if (genericNames != null && genericNames.isArray() && genericNames.size() > 0) {
                        drug.setGenericName(genericNames.get(0).asText());
                    }

                    JsonNode manufacturers = openfda.get("manufacturer_name");
                    if (manufacturers != null && manufacturers.isArray() && manufacturers.size() > 0) {
                        drug.setManufacturer(manufacturers.get(0).asText());
                    }
                }

                // Extract indications
                JsonNode indications = item.get("indications_and_usage");
                if (indications != null && indications.isArray() && indications.size() > 0) {
                    drug.setIndications(indications.get(0).asText());
                }

                // Set metadata
                drug.setLastFetched(LocalDateTime.now());

                // Only add if we have at least a name
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

    private boolean isCacheValid(LocalDateTime lastFetched) {
        if (lastFetched == null) return false;
        return lastFetched.plusHours(CACHE_TTL_HOURS).isAfter(LocalDateTime.now());
    }
}