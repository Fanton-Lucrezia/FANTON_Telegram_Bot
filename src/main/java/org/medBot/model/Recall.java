package org.medBot.model;

/*Classe modello che rappresenta un richiamo FDA
I richiami sono provvedimenti per ritirare o correggere farmaci con problemi*/
public class Recall {
    private String recallId;              //Identificativo univoco del richiamo FDA
    private String productDescription;    //Descrizione del prodotto richiamato
    private String reasonForRecall;       //Motivo del richiamo (es. contaminazione)
    private String classification;        //Gravità: Class I (grave), II (moderato), III (lieve)
    private String recallDate;            //Data del richiamo in formato YYYYMMDD

    //Costruttore vuoto necessario per la creazione dinamica degli oggetti
    public Recall() {}

    //Getter e setter per l'ID del richiamo
    public String getRecallId() {
        return recallId;
    }

    public void setRecallId(String recallId) {
        this.recallId = recallId;
    }

    //Getter e setter per la descrizione del prodotto
    public String getProductDescription() {
        return productDescription;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }

    //Getter e setter per il motivo del richiamo
    public String getReasonForRecall() {
        return reasonForRecall;
    }

    public void setReasonForRecall(String reasonForRecall) {
        this.reasonForRecall = reasonForRecall;
    }

    //Getter e setter per la classificazione di gravità
    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    //Getter e setter per la data del richiamo
    public String getRecallDate() {
        return recallDate;
    }

    public void setRecallDate(String recallDate) {
        this.recallDate = recallDate;
    }
}