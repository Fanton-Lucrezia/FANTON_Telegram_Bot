package org.medBot.model;

import java.time.LocalDateTime;

//Classe che rappresenta un farmaco con tutte le sue informazioni
//Viene usata per memorizzare i dati ricevuti dalle API OpenFDA
public class Drug {
    private String drugId;           //ID univoco del farmaco generato internamente
    private String brandName;        //Nome commerciale del farmaco (es. "Aspirin")
    private String genericName;      //Nome generico/principio attivo (es. "acetylsalicylic acid")
    private String manufacturer;     //Nome della casa farmaceutica produttrice
    private String indications;      //Indicazioni terapeutiche (per cosa serve il farmaco)
    private LocalDateTime lastFetched; //Data e ora dell'ultimo recupero dei dati dalle API

    //Costruttore vuoto necessario per la creazione di oggetti durante il parsing JSON
    public Drug() {
    }

    //Costruttore con i due campi più importanti per creare velocemente un oggetto Drug
    public Drug(String brandName, String genericName) {
        this.brandName = brandName;
        this.genericName = genericName;
    }

    //Getter e Setter per accedere e modificare i campi privati
    //Questi metodi seguono la convenzione JavaBeans
    
    public String getDrugId() {
        return drugId;
    }

    public void setDrugId(String drugId) {
        this.drugId = drugId;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getGenericName() {
        return genericName;
    }

    public void setGenericName(String genericName) {
        this.genericName = genericName;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getIndications() {
        return indications;
    }

    public void setIndications(String indications) {
        this.indications = indications;
    }

    public LocalDateTime getLastFetched() {
        return lastFetched;
    }

    public void setLastFetched(LocalDateTime lastFetched) {
        this.lastFetched = lastFetched;
    }

    //Sovrascrive il metodo toString() per facilitare il debug
    //Restituisce una rappresentazione testuale dell'oggetto Drug
    @Override
    public String toString() {
        return "Drug{" +
                "brandName='" + brandName + '\'' +
                ", genericName='" + genericName + '\'' +
                ", manufacturer='" + manufacturer + '\'' +
                '}';
    }
}