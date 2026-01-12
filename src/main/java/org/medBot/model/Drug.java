package org.medBot.model;

import java.time.LocalDateTime;

/*Classe modello che rappresenta un farmaco
Contiene tutte le informazioni principali recuperate dalle API FDA*/
public class Drug {
    private String drugId;              //Identificativo univoco generato dal sistema
    private String brandName;           //Nome commerciale del farmaco (es. Aspirin)
    private String genericName;         //Principio attivo (es. Acetylsalicylic Acid)
    private String manufacturer;        //Casa farmaceutica produttrice
    private String indications;         //Indicazioni terapeutiche (per cosa serve)
    private LocalDateTime lastFetched;  //Quando sono stati recuperati i dati (per cache)

    //Costruttore vuoto necessario per la creazione dinamica degli oggetti
    public Drug() {}

    //Getter e setter per l'ID del farmaco
    public String getDrugId() {
        return drugId;
    }

    public void setDrugId(String drugId) {
        this.drugId = drugId;
    }

    //Getter e setter per il nome commerciale
    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    //Getter e setter per il nome generico (principio attivo)
    public String getGenericName() {
        return genericName;
    }

    public void setGenericName(String genericName) {
        this.genericName = genericName;
    }

    //Getter e setter per il produttore
    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    //Getter e setter per le indicazioni terapeutiche
    public String getIndications() {
        return indications;
    }

    public void setIndications(String indications) {
        this.indications = indications;
    }

    //Getter e setter per la data di ultimo recupero dati
    public LocalDateTime getLastFetched() {
        return lastFetched;
    }

    public void setLastFetched(LocalDateTime lastFetched) {
        this.lastFetched = lastFetched;
    }
}