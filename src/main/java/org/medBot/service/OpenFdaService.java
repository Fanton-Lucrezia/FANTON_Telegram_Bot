package org.medBot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.medBot.dao.DatabaseManager;
import org.medBot.model.Drug;
import org.medBot.model.Recall;

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
 * Service per interagire con le API OpenFDA.
 * Gestisce richieste HTTP e parsing JSON con caching su database.
 */
public class OpenFdaService {
    private static final String FDA_BASE_URL = "https://api.fda.gov";
    private static final int CACHE_HOURS = 24;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DatabaseManager dbManager;

    public OpenFdaService() {
        //Crea il client HTTP per le richieste alle API
        this.httpClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build();
        this.objectMapper = new ObjectMapper();
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Cerca farmaci per nome utilizzando la cache del database.
     */
    public List<Drug> searchDrug(String searchTerm) throws IOException {
        System.out.println("Ricerca farmaco: " + searchTerm);

        //Controlla se il farmaco è in cache
        Drug cached = getCachedDrug(searchTerm);
        if (cached != null) {
            System.out.println("Cache HIT");
            return List.of(cached);
        }

        System.out.println("Cache MISS, chiamata API");

        //Chiama l'API OpenFDA
        String encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        String query = String.format(
                "(openfda.brand_name:\"%s\"+OR+openfda.generic_name:\"%s\")",
                encodedTerm, encodedTerm);

        String url = String.format("%s/drug/label.json?search=%s&limit=10", FDA_BASE_URL, query);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    throw new IOException("404 - Farmaco non trovato");
                }
                throw new IOException("Errore API FDA: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);

            List<Drug> drugs = parseDrugResults(root);

            //Salva il primo risultato in cache
            if (!drugs.isEmpty()) {
                saveDrugToCache(drugs.get(0));
            }

            return drugs;
        }
    }

    /**
     * Cerca richiami FDA per un farmaco specifico.
     */
    public List<Recall> searchRecalls(String searchTerm) throws IOException {
        String encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        String url = String.format(
                "%s/drug/enforcement.json?search=product_description:\"%s\"&limit=20",
                FDA_BASE_URL, encodedTerm);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    return List.of();
                }
                throw new IOException("Errore API FDA: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            return parseRecallResults(root);
        }
    }

    /**
     * Ottiene gli ultimi richiami FDA (tutti i farmaci).
     */
    public List<Recall> getRecentRecalls(int limit) throws IOException {
        String url = String.format(
                "%s/drug/enforcement.json?limit=%d&sort=report_date:desc",
                FDA_BASE_URL, limit);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Errore API FDA: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            return parseRecallResults(root);
        }
    }

    /**
     * Ottiene eventi avversi (effetti collaterali) per un farmaco.
     */
    public java.util.Map<String, Object> getAdverseEvents(String drugName) throws IOException {
        String encodedTerm = URLEncoder.encode(drugName, StandardCharsets.UTF_8);
        String url = String.format(
                "%s/drug/event.json?search=patient.drug.medicinalproduct:\"%s\"&limit=100",
                FDA_BASE_URL, encodedTerm);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
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
     * Verifica interazioni tra più farmaci.
     * Cerca eventi avversi quando i farmaci sono usati insieme.
     */
    public java.util.Map<String, Object> checkDrugInteractions(String[] drugs) throws IOException {
        //Costruisce la query per cercare eventi che coinvolgono tutti i farmaci
        StringBuilder queryBuilder = new StringBuilder();
        for (int i = 0; i < drugs.length; i++) {
            if (i > 0) queryBuilder.append("+AND+");
            String encoded = URLEncoder.encode(drugs[i].trim(), StandardCharsets.UTF_8);
            queryBuilder.append("patient.drug.medicinalproduct:\"").append(encoded).append("\"");
        }

        String url = String.format(
                "%s/drug/event.json?search=%s&limit=100",
                FDA_BASE_URL, queryBuilder.toString());

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    return java.util.Map.of("count", 0);
                }
                throw new IOException("Errore API FDA: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            return parseInteractionResults(root);
        }
    }

    /**
     * Verifica se un farmaco è una sostanza controllata.
     * FIX: Cerca in modo più ampio e controlla tutti i risultati.
     */
    public String checkDrugSchedule(String drugName) throws IOException {
        System.out.println("Verifica sostanza controllata: " + drugName);
        
        String encodedTerm = URLEncoder.encode(drugName, StandardCharsets.UTF_8);
        
        //Prova diverse strategie di ricerca
        String[] queries = {
            //Query 1: Cerca con brand o generic name (ampia)
            String.format("(openfda.brand_name:\"%s\"+OR+openfda.generic_name:\"%s\")", 
                encodedTerm, encodedTerm),
            //Query 2: Cerca solo per generic name (più specifica)
            String.format("openfda.generic_name:\"%s\"", encodedTerm),
            //Query 3: Cerca senza openfda (cerca direttamente nel testo)
            String.format("\"%s\"", encodedTerm)
        };
        
        for (int attempt = 0; attempt < queries.length; attempt++) {
            String url = String.format("%s/drug/label.json?search=%s&limit=10", FDA_BASE_URL, queries[attempt]);
            System.out.println("Tentativo " + (attempt + 1) + ": " + url);
            
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "MedBot/1.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    JsonNode root = objectMapper.readTree(responseBody);

                    JsonNode results = root.get("results");
                    if (results != null && results.isArray() && results.size() > 0) {
                        //Cerca in TUTTI i risultati se c'è un dea_schedule
                        for (JsonNode result : results) {
                            JsonNode openfda = result.get("openfda");
                            if (openfda != null && openfda.has("dea_schedule")) {
                                JsonNode schedules = openfda.get("dea_schedule");
                                if (schedules.isArray() && schedules.size() > 0) {
                                    String schedule = schedules.get(0).asText();
                                    //Rimuove il prefisso "C" se presente (es. "CII" -> "II")
                                    schedule = schedule.replaceAll("^C", "").replaceAll("^IV$|^V$|^III$|^II$|^I$", "$0");
                                    System.out.println("✅ Trovato Schedule: " + schedule);
                                    return schedule;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("❌ Errore query: " + e.getMessage());
            }
        }
        
        System.out.println("❌ Nessun schedule trovato");
        return null;
    }

    // ==================== PARSING METHODS ====================

    /**
     * Converte la risposta JSON dell'API in oggetti Drug.
     */
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
                    //Estrae il brand name
                    JsonNode brandNames = openfda.get("brand_name");
                    if (brandNames != null && brandNames.isArray() && brandNames.size() > 0) {
                        drug.setBrandName(brandNames.get(0).asText());
                    }

                    //Estrae il generic name
                    JsonNode genericNames = openfda.get("generic_name");
                    if (genericNames != null && genericNames.isArray() && genericNames.size() > 0) {
                        drug.setGenericName(genericNames.get(0).asText());
                    }

                    //Estrae il produttore
                    JsonNode manufacturers = openfda.get("manufacturer_name");
                    if (manufacturers != null && manufacturers.isArray() && manufacturers.size() > 0) {
                        drug.setManufacturer(manufacturers.get(0).asText());
                    }
                }

                //Estrae le indicazioni terapeutiche
                JsonNode indications = item.get("indications_and_usage");
                if (indications != null && indications.isArray() && indications.size() > 0) {
                    drug.setIndications(indications.get(0).asText());
                }

                drug.setLastFetched(LocalDateTime.now());

                //Aggiunge solo se ha almeno un nome
                if (drug.getBrandName() != null || drug.getGenericName() != null) {
                    drugs.add(drug);
                }

            } catch (Exception e) {
                System.out.println("Errore parsing farmaco: " + e.getMessage());
            }
        }

        return drugs;
    }

    /**
     * Converte la risposta JSON dei richiami in oggetti Recall.
     */
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
                System.out.println("Errore parsing richiamo: " + e.getMessage());
            }
        }

        return recalls;
    }

    /**
     * Converte la risposta JSON degli eventi avversi in una mappa di statistiche.
     */
    private java.util.Map<String, Object> parseAdverseEvents(JsonNode root) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();

        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return result;
        }

        result.put("total", results.size());

        //Conta le reazioni più comuni
        java.util.Map<String, Integer> reactions = new java.util.HashMap<>();
        int serious = 0;
        int nonSerious = 0;

        for (JsonNode event : results) {
            //Conta la gravità
            if (event.has("serious") && event.get("serious").asInt() == 1) {
                serious++;
            } else {
                nonSerious++;
            }

            //Estrae le reazioni avverse
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

        //Ordina le reazioni per frequenza
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

    /**
     * Converte i risultati delle interazioni in una mappa di statistiche.
     */
    private java.util.Map<String, Object> parseInteractionResults(JsonNode root) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();

        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            result.put("count", 0);
            return result;
        }

        result.put("count", results.size());

        //Estrae le reazioni più comuni nelle interazioni
        java.util.List<String> commonReactions = new java.util.ArrayList<>();
        java.util.Map<String, Integer> reactionCounts = new java.util.HashMap<>();

        for (JsonNode event : results) {
            if (event.has("patient")) {
                JsonNode patient = event.get("patient");
                if (patient.has("reaction")) {
                    JsonNode reactionsNode = patient.get("reaction");
                    if (reactionsNode.isArray()) {
                        for (JsonNode reaction : reactionsNode) {
                            if (reaction.has("reactionmeddrapt")) {
                                String reactionName = reaction.get("reactionmeddrapt").asText();
                                reactionCounts.put(reactionName,
                                        reactionCounts.getOrDefault(reactionName, 0) + 1);
                            }
                        }
                    }
                }
            }
        }

        //Ordina e prende le prime 10 reazioni
        reactionCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .forEach(e -> commonReactions.add(e.getKey()));

        result.put("commonReactions", commonReactions);

        return result;
    }

    // ==================== CACHE METHODS ====================

    /**
     * Cerca un farmaco nella cache del database.
     */
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
            System.out.println("Errore cache: " + e.getMessage());
        }

        return null;
    }

    /**
     * Salva un farmaco nella cache del database.
     */
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

        } catch (Exception e) {
            System.out.println("Errore salvataggio cache: " + e.getMessage());
        }
    }

    /**
     * Genera un ID univoco per il farmaco.
     */
    private String generateDrugId(Drug drug) {
        String name = drug.getBrandName() != null ? drug.getBrandName() : drug.getGenericName();
        if (name == null) name = "unknown";

        String normalized = name.toLowerCase().replaceAll("[^a-z0-9]", "-");
        return normalized + "-" + System.currentTimeMillis();
    }
}