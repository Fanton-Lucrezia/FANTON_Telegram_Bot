package org.example.model;

import java.time.LocalDateTime;

/**
 * Model class representing a pharmaceutical drug.
 */
public class Drug {
    private String drugId;
    private String brandName;
    private String genericName;
    private String manufacturer;
    private String indications;
    private LocalDateTime lastFetched;

    public Drug() {
    }

    public Drug(String brandName, String genericName) {
        this.brandName = brandName;
        this.genericName = genericName;
    }

    // Getters and Setters
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

    @Override
    public String toString() {
        return "Drug{" +
                "brandName='" + brandName + '\'' +
                ", genericName='" + genericName + '\'' +
                ", manufacturer='" + manufacturer + '\'' +
                '}';
    }
}