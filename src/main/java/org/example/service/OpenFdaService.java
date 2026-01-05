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

    // ==================== CACHE ====================

    /**
     * Cerca farmaci per nome (con cache).
     */
    public List<Drug> searchDrug(String searchTerm) throws IOException {
        logger.info("Ricerca farmaco: {}", searchTerm);

        // Controlla cache
        Drug cached = getCachedDrug(searchTerm);
        if (cached != null) {
            logger.info("Cache HIT per: {}", searchTerm);
            return List.of(cached);
        }

        logger.info("Cache MISS, chiamata API FDA");

        // Chiama API FDA
        String encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        String query = String.format(
                "(openfda.brand_name:\"%s\"+OR+openfda.generic_name:\"%s\")",
                encodedTerm, encodedTerm);

        String url = String.format("%s/drug/label.json?search=%s&limit=10", FDA_BASE_URL, query);

        logger.debug("URL FDA: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warn("API FDA ha risposto con: {}", response.code());
                if (response.code() == 404) {
                    throw new IOException("404 - Farmaco non trovato nel database FDA");
                }
                throw new IOException("Errore API FDA: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);

            List<Drug> drugs = parseDrugResults(root);

            // Salva in cache il primo risultato
            if (!drugs.isEmpty()) {
                saveDrugToCache(drugs.get(0));
            }

            logger.info("Trovati {} farmaci", drugs.size());
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
                FDA_BASE_URL, encodedTerm);

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
                FDA_BASE_URL, limit);

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

    /**
     * Ottieni eventi avversi per un farmaco.
     * Restituisce una mappa con: total, topReactions, serious.
     */
    public java.util.Map<String, Object> getAdverseEvents(String drugName) throws IOException {
        logger.info("Ricerca eventi avversi per: {}", drugName);

        String encodedTerm = URLEncoder.encode(drugName, StandardCharsets.UTF_8);
        String url = String.format(
                "%s/drug/event.json?search=patient.drug.medicinalproduct:\"%s\"&limit=100",
                FDA_BASE_URL, encodedTerm);

        logger.debug("URL FDA Events: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    logger.info("Nessun evento avverso trovato (404)");
                    return java.util.Map.of();
                }
                throw new IOException("Errore API FDA: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);

            return parseAdverseEvents(root);
        }
    }

    /**
     * Ottieni informazioni sulla salute da MyHealthfinder API.
     */
    public java.util.Map<String, Object> getHealthInfo(String topic) throws IOException {
        logger.info("Ricerca informazioni salute per: {}", topic);

        String encodedTopic = URLEncoder.encode(topic, StandardCharsets.UTF_8);
        String url = String.format(
                "https://health.gov/myhealthfinder/api/v3/topicsearch.json?keyword=%s",
                encodedTopic);

        logger.debug("URL MyHealthfinder: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warn("MyHealthfinder API ha risposto con: {}", response.code());
                return null;
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);

            return parseHealthInfo(root);
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

    private java.util.Map<String, Object> parseAdverseEvents(JsonNode root) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();

        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return result;
        }

        result.put("total", results.size());

        // Conta reazioni più comuni
        java.util.Map<String, Integer> reactions = new java.util.HashMap<>();
        int serious = 0;
        int nonSerious = 0;

        for (JsonNode event : results) {
            // Conta gravità
            if (event.has("serious") && event.get("serious").asInt() == 1) {
                serious++;
            } else {
                nonSerious++;
            }

            // Estrai reazioni
            if (event.has("patient")) {
                JsonNode patient = event.get("patient");
                if (patient.has("reaction")) {
                    JsonNode reactionsNode = patient.get("reaction");
                    if (reactionsNode.isArray()) {
                        for (JsonNode reaction : reactionsNode) {
                            if (reaction.has("reactionmeddrapt")) {
                                String reactionName = reaction.get("reactionmeddrapt").asText().toLowerCase();
                                reactions.put(reactionName, reactions.getOrDefault(reactionName, 0) + 1);
                            }
                        }
                    }
                }
            }
        }

        // Ordina reazioni per frequenza
        var sortedReactions = reactions.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .collect(java.util.LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        java.util.Map::putAll);

        result.put("topReactions", sortedReactions);
        result.put("serious", java.util.Map.of("serious", serious, "nonSerious", nonSerious));

        return result;
    }

    private java.util.Map<String, Object> parseHealthInfo(JsonNode root) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();

        JsonNode resultNode = root.get("Result");
        if (resultNode == null) {
            return null;
        }

        JsonNode resources = resultNode.get("Resources");
        if (resources == null || !resources.has("Resource") || !resources.get("Resource").isArray()) {
            return null;
        }

        JsonNode resourceArray = resources.get("Resource");
        if (resourceArray.size() == 0) {
            return null;
        }

        // Prendi il primo risultato più rilevante
        JsonNode resource = resourceArray.get(0);

        if (resource.has("Title")) {
            result.put("title", resource.get("Title").asText());
        }

        if (resource.has("AccessibleVersion")) {
            result.put("url", resource.get("AccessibleVersion").asText());
        }

        // Estrai sezioni
        java.util.List<java.util.Map<String, String>> sections = new java.util.ArrayList<>();

        if (resource.has("Sections")) {
            JsonNode sectionsNode = resource.get("Sections");
            if (sectionsNode.has("section") && sectionsNode.get("section").isArray()) {
                for (JsonNode section : sectionsNode.get("section")) {
                    java.util.Map<String, String> sectionData = new java.util.HashMap<>();

                    if (section.has("Title")) {
                        String title = section.get("Title").asText();
                        // Salta titoli vuoti o "null"
                        if (title == null || title.equalsIgnoreCase("null") || title.trim().isEmpty()) {
                            continue;
                        }
                        sectionData.put("title", title);
                    }

                    if (section.has("Content")) {
                        String content = section.get("Content").asText();

                        // Salta contenuti nulli o vuoti
                        if (content == null || content.equalsIgnoreCase("null") || content.trim().isEmpty()) {
                            continue;
                        }

                        // Pulisci HTML tags e entità
                        content = content
                                .replaceAll("<[^>]+>", " ") // Rimuovi tag HTML
                                .replaceAll("&nbsp;", " ") // Sostituisci &nbsp;
                                .replaceAll("&amp;", "&") // Sostituisci &amp;
                                .replaceAll("&lt;", "<")
                                .replaceAll("&gt;", ">")
                                .replaceAll("&quot;", "\"")
                                .replaceAll("&#39;", "'")
                                .replaceAll("\\s+", " ") // Normalizza spazi
                                .trim();

                        // Salta se il contenuto pulito è troppo corto
                        if (content.length() < 20) {
                            continue;
                        }

                        // Limita a 350 caratteri per sezione
                        if (content.length() > 350) {
                            content = content.substring(0, 347) + "...";
                        }

                        sectionData.put("content", content);
                    }

                    // Aggiungi solo se ha sia titolo che contenuto validi
                    if (sectionData.containsKey("title") && sectionData.containsKey("content")) {
                        sections.add(sectionData);
                    }

                    // Limita a 4 sezioni
                    if (sections.size() >= 4)
                        break;
                }
            }
        }

        // Se non ci sono sezioni valide, ritorna null
        if (sections.isEmpty()) {
            return null;
        }

        result.put("sections", sections);

        return result;
    }

    /**
     * Verifica se un farmaco è una sostanza controllata (droga).
     * Restituisce il livello Schedule (I-V) o null se non è controllato.
     */
    public String checkDrugSchedule(String drugName) throws IOException {
        logger.info("Checking drug schedule for: {}", drugName);

        String encodedTerm = URLEncoder.encode(drugName, StandardCharsets.UTF_8);

        // Cerca prima nel database label per ottenere info sulla sostanza
        String url = String.format(
                "%s/drug/label.json?search=(openfda.brand_name:\"%s\"+OR+openfda.generic_name:\"%s\")+AND+_exists_:openfda.dea_schedule&limit=1",
                FDA_BASE_URL, encodedTerm, encodedTerm);

        logger.debug("FDA Schedule URL: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.code() == 404) {
                logger.info("No controlled substance info found");
                return null;
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode results = root.get("results");
            if (results != null && results.isArray() && results.size() > 0) {
                JsonNode openfda = results.get(0).get("openfda");
                if (openfda != null && openfda.has("dea_schedule")) {
                    JsonNode schedules = openfda.get("dea_schedule");
                    if (schedules.isArray() && schedules.size() > 0) {
                        String schedule = schedules.get(0).asText();
                        // Rimuovi "C" prefix se presente (es. "CII" -> "II")
                        schedule = schedule.replaceAll("^C", "");
                        logger.info("Drug is Schedule {}", schedule);
                        return schedule;
                    }
                }
            }

            return null;
        }
    }

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
                        "INSERT OR REPLACE INTO drugs_cache (drug_id, brand_name, generic_name, manufacturer, indications, last_fetched) "
                                +
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
        if (name == null)
            name = "unknown";

        String normalized = name.toLowerCase().replaceAll("[^a-z0-9]", "-");
        return normalized + "-" + System.currentTimeMillis();
    }
}