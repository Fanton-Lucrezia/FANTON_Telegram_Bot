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

/*Servizio per interagire con le API OpenFDA
Gestisce richieste HTTP, parsing JSON e caching su database per ridurre chiamate API*/
public class OpenFdaService {
    //URL base delle API FDA
    private static final String FDA_BASE_URL = "https://api.fda.gov";
    //Durata della cache: i dati vengono riutilizzati per 24 ore prima di richiamare l'API
    private static final int CACHE_HOURS = 24;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DatabaseManager dbManager;

    //Costruttore che inizializza il client HTTP e il parser JSON
    public OpenFdaService() {
        //Crea il client HTTP con retry automatico in caso di problemi di connessione
        this.httpClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build();
        //ObjectMapper di Jackson per convertire JSON in oggetti Java
        this.objectMapper = new ObjectMapper();
        this.dbManager = DatabaseManager.getInstance();
    }

    /*Cerca farmaci per nome chiamando sempre l'API OpenFDA
    Non usa cache per le ricerche perché deve restituire TUTTI i risultati trovati*/
    public List<Drug> searchDrug(String searchTerm) throws IOException {
        //Codifica il termine di ricerca per URL (es. spazi diventano %20)
        String encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        //Costruisce la query per cercare sia nel brand name che nel generic name
        String query = String.format(
                "(openfda.brand_name:\"%s\"+OR+openfda.generic_name:\"%s\")",
                encodedTerm, encodedTerm);

        //URL completo con query e limite di 100 risultati per avere tutti i farmaci disponibili
        String url = String.format("%s/drug/label.json?search=%s&limit=100", FDA_BASE_URL, query);

        //Costruisce la richiesta HTTP con header User-Agent personalizzato
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        //Esegue la richiesta HTTP e gestisce la risposta
        try (Response response = httpClient.newCall(request).execute()) {
            //Controlla se la richiesta è andata a buon fine
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    return new ArrayList<>();
                }
                throw new IOException("Errore API FDA: " + response.code());
            }

            //Legge il corpo della risposta come stringa JSON
            String responseBody = response.body().string();
            //Converte la stringa JSON in un albero di nodi per facilitare il parsing
            JsonNode root = objectMapper.readTree(responseBody);

            //Converte i risultati JSON in oggetti Drug
            List<Drug> drugs = parseDrugResults(root);

            return drugs;
        }
    }

    /*Cerca richiami FDA per un farmaco specifico
    I richiami sono provvedimenti della FDA per ritirare o correggere farmaci problematici*/
    public List<Recall> searchRecalls(String searchTerm) throws IOException {
        //Codifica il termine per l'URL
        String encodedTerm = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        //Cerca nella descrizione del prodotto richiamato
        String url = String.format(
                "%s/drug/enforcement.json?search=product_description:\"%s\"&limit=20",
                FDA_BASE_URL, encodedTerm);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MedBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                //404 significa nessun richiamo trovato (è ok, non è un errore)
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

    /*Ottiene gli ultimi richiami FDA generali (non per un farmaco specifico)
    Utile per vedere quali farmaci sono stati richiamati recentemente*/
    public List<Recall> getRecentRecalls(int limit) throws IOException {
        //Ordina per data di richiamo (i più recenti prima)
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

    /*Ottiene eventi avversi (effetti collaterali) segnalati per un farmaco
    Gli eventi avversi sono reazioni negative riportate dai pazienti o medici*/
    public java.util.Map<String, Object> getAdverseEvents(String drugName) throws IOException {
        String encodedTerm = URLEncoder.encode(drugName, StandardCharsets.UTF_8);
        //Cerca nei dati sui pazienti il nome del farmaco medicinale
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

    /*Verifica interazioni tra più farmaci
    Cerca eventi avversi quando i farmaci specificati sono usati insieme
    Questo aiuta a identificare combinazioni potenzialmente pericolose*/
    public java.util.Map<String, Object> checkDrugInteractions(String[] drugs) throws IOException {
        //Costruisce una query che cerca eventi dove TUTTI i farmaci sono presenti
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

    /*Verifica se un farmaco è una sostanza controllata (DEA Schedule)
    Le sostanze controllate sono farmaci regolamentati (es. oppioidi, anfetamine)
    Schedule I-V indicano diversi livelli di controllo e potenziale abuso*/
    public String checkDrugSchedule(String drugName) throws IOException {
        String encodedTerm = URLEncoder.encode(drugName, StandardCharsets.UTF_8);
        
        //Prova diverse strategie di ricerca per massimizzare le possibilità di trovare il farmaco
        String[] queries = {
            //Query 1: Cerca sia nel brand name che nel generic name (ricerca ampia)
            String.format("(openfda.brand_name:\"%s\"+OR+openfda.generic_name:\"%s\")", 
                encodedTerm, encodedTerm),
            //Query 2: Cerca solo nel generic name (più specifica)
            String.format("openfda.generic_name:\"%s\"", encodedTerm),
            //Query 3: Ricerca generica nel testo (fallback)
            String.format("\"%s\"", encodedTerm)
        };
        
        //Prova ogni strategia di ricerca fino a trovare un risultato con dea_schedule
        for (int attempt = 0; attempt < queries.length; attempt++) {
            String url = String.format("%s/drug/label.json?search=%s&limit=10", FDA_BASE_URL, queries[attempt]);
            
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
                        //Alcuni farmaci hanno schedule solo in certi risultati
                        for (JsonNode result : results) {
                            JsonNode openfda = result.get("openfda");
                            if (openfda != null && openfda.has("dea_schedule")) {
                                JsonNode schedules = openfda.get("dea_schedule");
                                if (schedules.isArray() && schedules.size() > 0) {
                                    String schedule = schedules.get(0).asText();
                                    //Rimuove il prefisso "C" se presente (es. "CII" diventa "II")
                                    schedule = schedule.replaceAll("^C", "").replaceAll("^IV$|^V$|^III$|^II$|^I$", "$0");
                                    return schedule;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                //Continua con la prossima strategia
            }
        }
        
        //Se nessuna strategia ha trovato uno schedule, il farmaco non è controllato
        return null;
    }

    //==================== METODI DI PARSING JSON ====================

    /*Converte la risposta JSON dell'API in una lista di oggetti Drug
    Estrae brand name, generic name, produttore e indicazioni terapeutiche*/
    private List<Drug> parseDrugResults(JsonNode root) {
        List<Drug> drugs = new ArrayList<>();

        //Ottiene l'array "results" dal JSON
        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return drugs;
        }

        //Itera su ogni risultato e crea un oggetto Drug
        for (JsonNode item : results) {
            try {
                Drug drug = new Drug();

                //I dati OpenFDA sono in un sotto-oggetto chiamato "openfda"
                JsonNode openfda = item.get("openfda");
                if (openfda != null) {
                    //Estrae il brand name (nome commerciale)
                    //È un array perché un farmaco può avere più nomi commerciali
                    JsonNode brandNames = openfda.get("brand_name");
                    if (brandNames != null && brandNames.isArray() && brandNames.size() > 0) {
                        drug.setBrandName(brandNames.get(0).asText());
                    }

                    //Estrae il generic name (principio attivo)
                    JsonNode genericNames = openfda.get("generic_name");
                    if (genericNames != null && genericNames.isArray() && genericNames.size() > 0) {
                        drug.setGenericName(genericNames.get(0).asText());
                    }

                    //Estrae il nome del produttore
                    JsonNode manufacturers = openfda.get("manufacturer_name");
                    if (manufacturers != null && manufacturers.isArray() && manufacturers.size() > 0) {
                        drug.setManufacturer(manufacturers.get(0).asText());
                    }
                }

                //Estrae le indicazioni terapeutiche (per cosa serve il farmaco)
                JsonNode indications = item.get("indications_and_usage");
                if (indications != null && indications.isArray() && indications.size() > 0) {
                    drug.setIndications(indications.get(0).asText());
                }

                //Imposta la data di recupero per la cache
                drug.setLastFetched(LocalDateTime.now());

                //Aggiunge il farmaco alla lista solo se ha almeno un nome
                if (drug.getBrandName() != null || drug.getGenericName() != null) {
                    drugs.add(drug);
                }

            } catch (Exception e) {
                //Salta i farmaci con dati malformati
            }
        }

        return drugs;
    }

    /*Converte la risposta JSON dei richiami in una lista di oggetti Recall
    Estrae descrizione prodotto, motivo, classificazione e data del richiamo*/
    private List<Recall> parseRecallResults(JsonNode root) {
        List<Recall> recalls = new ArrayList<>();

        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return recalls;
        }

        //Itera su ogni richiamo e crea un oggetto Recall
        for (JsonNode item : results) {
            try {
                Recall recall = new Recall();

                //Descrizione del prodotto richiamato
                if (item.has("product_description")) {
                    recall.setProductDescription(item.get("product_description").asText());
                }

                //Motivo del richiamo (es. contaminazione, errore etichettatura)
                if (item.has("reason_for_recall")) {
                    recall.setReasonForRecall(item.get("reason_for_recall").asText());
                }

                //Classificazione: Class I (grave), Class II (moderato), Class III (lieve)
                if (item.has("classification")) {
                    recall.setClassification(item.get("classification").asText());
                }

                //Data del richiamo in formato YYYYMMDD
                if (item.has("report_date")) {
                    recall.setRecallDate(item.get("report_date").asText());
                }

                //ID univoco del richiamo assegnato dalla FDA
                if (item.has("recall_number")) {
                    recall.setRecallId(item.get("recall_number").asText());
                }

                recalls.add(recall);

            } catch (Exception e) {
                //Salta i richiami con dati malformati
            }
        }

        return recalls;
    }

    /*Converte la risposta JSON degli eventi avversi in una mappa di statistiche
    Calcola: numero totale eventi, reazioni più comuni, gravità*/
    private java.util.Map<String, Object> parseAdverseEvents(JsonNode root) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();

        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return result;
        }

        //Numero totale di eventi avversi trovati
        result.put("total", results.size());

        //Conta le reazioni più comuni e la loro gravità
        java.util.Map<String, Integer> reactions = new java.util.HashMap<>();
        int serious = 0;     //Eventi gravi
        int nonSerious = 0;  //Eventi non gravi

        //Itera su ogni evento avverso
        for (JsonNode event : results) {
            //Verifica se l'evento è grave (può causare morte, ospedalizzazione, ecc.)
            if (event.has("serious") && event.get("serious").asInt() == 1) {
                serious++;
            } else {
                nonSerious++;
            }

            //Estrae le reazioni avverse dal paziente
            if (event.has("patient")) {
                JsonNode patient = event.get("patient");
                if (patient.has("reaction")) {
                    JsonNode reactionsNode = patient.get("reaction");
                    if (reactionsNode.isArray()) {
                        //Ogni evento può avere multiple reazioni
                        for (JsonNode reaction : reactionsNode) {
                            if (reaction.has("reactionmeddrapt")) {
                                //MedDRA è il dizionario medico standard per terminologia
                                String reactionName = reaction.get("reactionmeddrapt").asText().toLowerCase();
                                //Conta quante volte appare ogni reazione
                                reactions.put(reactionName, reactions.getOrDefault(reactionName, 0) + 1);
                            }
                        }
                    }
                }
            }
        }

        //Ordina le reazioni per frequenza (le più comuni prima) e limita a 10
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

    /*Converte i risultati delle interazioni in una mappa di statistiche
    Mostra quanti eventi avversi sono stati riportati con la combinazione di farmaci*/
    private java.util.Map<String, Object> parseInteractionResults(JsonNode root) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();

        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            result.put("count", 0);
            return result;
        }

        //Numero totale di eventi con la combinazione di farmaci
        result.put("count", results.size());

        //Estrae le reazioni più comuni quando i farmaci sono usati insieme
        java.util.List<String> commonReactions = new java.util.ArrayList<>();
        java.util.Map<String, Integer> reactionCounts = new java.util.HashMap<>();

        //Conta la frequenza di ogni reazione
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

        //Ordina per frequenza e prende le prime 10 reazioni
        reactionCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .forEach(e -> commonReactions.add(e.getKey()));

        result.put("commonReactions", commonReactions);

        return result;
    }
}