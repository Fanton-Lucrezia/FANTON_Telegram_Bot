package org.medBot.model;

//Classe che rappresenta un richiamo FDA (enforcement report)
//Un richiamo è un provvedimento per ritirare o correggere un farmaco problematico
public class Recall {
    private String recallId;            //ID univoco del richiamo assegnato dalla FDA
    private String productDescription;  //Descrizione del prodotto richiamato
    private String reasonForRecall;     //Motivo del richiamo (es. contaminazione, errore di dosaggio)
    private String classification;      //Gravità del richiamo (Class I, II, III)
    private String recallDate;         //Data del richiamo in formato YYYYMMDD

    //Costruttore vuoto per la creazione di oggetti durante il parsing JSON
    public Recall() {
    }

    //Costruttore con i campi principali per creare un oggetto Recall
    public Recall(String recallId, String productDescription, String reasonForRecall) {
        this.recallId = recallId;
        this.productDescription = productDescription;
        this.reasonForRecall = reasonForRecall;
    }

    //Getter e Setter per accedere e modificare i campi privati
    
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

    //Sovrascrive toString() per facilitare il debug
    //Mostra i campi principali del richiamo in formato leggibile
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