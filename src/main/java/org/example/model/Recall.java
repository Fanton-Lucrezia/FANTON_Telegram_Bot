package org.example.model;

/**
 * Model class representing an FDA enforcement report (recall).
 */
public class Recall {
    private String recallId;
    private String productDescription;
    private String reasonForRecall;
    private String classification;
    private String recallDate;

    public Recall() {
    }

    public Recall(String recallId, String productDescription, String reasonForRecall) {
        this.recallId = recallId;
        this.productDescription = productDescription;
        this.reasonForRecall = reasonForRecall;
    }

    // Getters and Setters
    public String getRecallId() {
        return recallId;
    }

    public void setRecallId(String recallId) {
        this.recallId = recallId;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }

    public String getReasonForRecall() {
        return reasonForRecall;
    }

    public void setReasonForRecall(String reasonForRecall) {
        this.reasonForRecall = reasonForRecall;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getRecallDate() {
        return recallDate;
    }

    public void setRecallDate(String recallDate) {
        this.recallDate = recallDate;
    }

    @Override
    public String toString() {
        return "Recall{" +
                "recallId='" + recallId + '\'' +
                ", productDescription='" + productDescription + '\'' +
                ", classification='" + classification + '\'' +
                ", recallDate='" + recallDate + '\'' +
                '}';
    }
}