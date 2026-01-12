package org.medBot;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import java.io.File;

//Classe che gestisce la configurazione dell'applicazione leggendo dal file config.properties
//Utilizza il pattern Singleton per garantire una sola istanza in tutta l'applicazione
public class MyConfiguration {
    private static MyConfiguration instance;
    private final Configuration config;

    //Costruttore privato per impedire la creazione diretta di istanze
    //Carica il file di configurazione all'inizializzazione
    private MyConfiguration() {
        try {
            //Usa Configurations che semplifica il caricamento del file properties
            //Questo metodo è compatibile con Apache Commons Configuration 2.x
            Configurations configs = new Configurations();
            
            //Carica il file config.properties dalla root del progetto
            config = configs.properties(new File("config.properties"));
        } catch (ConfigurationException e) {
            //Se il file non esiste o è malformato, lancia un'eccezione
            throw new RuntimeException("Errore caricamento config.properties", e);
        }
    }

    //Metodo statico per ottenere l'unica istanza della classe (Singleton pattern)
    //Se l'istanza non esiste ancora, la crea; altrimenti restituisce quella esistente
    public static synchronized MyConfiguration getInstance() {
        if (instance == null) {
            instance = new MyConfiguration();
        }
        return instance;
    }

    //Restituisce il valore di una proprietà dato il suo nome
    //Esempio: getProperty("BOT_TOKEN") restituisce il token del bot
    public String getProperty(String key) {
        return config.getString(key);
    }
}