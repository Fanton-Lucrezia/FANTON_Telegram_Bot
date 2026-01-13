# MedBot - Bot Telegram per Informazioni sui Farmaci

Bot Telegram sviluppato in Java che fornisce informazioni sui farmaci utilizzando le API OpenFDA.

## Funzionalità

- Ricerca farmaci: cerca informazioni complete sui farmaci
- Richiami FDA: verifica se un farmaco è stato richiamato
- Eventi avversi: consulta gli effetti collaterali riportati
- Interazioni: verifica interazioni tra più farmaci
- Bookmark: salva i farmaci preferiti
- Statistiche: monitora le tue ricerche

## Tecnologie Utilizzate

- **Java 17+**
- **TelegramBots API** - per l'interazione con Telegram
- **OkHttp** - per le chiamate HTTP alle API FDA
- **Jackson** - per il parsing JSON
- **SQLite** - per il database locale
- **Maven** - per la gestione delle dipendenze

## Installazione

### Prerequisiti

- Java 17 o superiore
- Maven
- Un bot Telegram (ottieni il token da @BotFather)

### Configurazione

1. **Clona il repository**
   ```bash
   git clone https://github.com/Fanton-Lucrezia/FANTON_Telegram_Bot.git
   cd FANTON_Telegram_Bot
   ```

2. **Crea il file di configurazione**
   
   Crea `config.properties` nella root del progetto:
   ```properties
   BOT_TOKEN=il_tuo_token_qui
   DB_PATH=./data/medbot.db
   ```

3. **Compila il progetto**
   ```bash
   mvn clean install
   ```

4. **Avvia il bot**
   ```bash
   mvn exec:java -Dexec.mainClass="org.medBot.Main"
   ```
   
   Oppure esegui direttamente da IntelliJ IDEA la classe `Main.java`

## Struttura del Progetto

```
FANTON_Telegram_Bot/
├── src/main/java/org/medBot/
│   ├── Main.java
│   ├── MyConfiguration.java
│   ├── bot/
│   │   ├── MedBot.java
│   │   ├── CommandDispatcher.java
│   │   └── MessageSender.java
│   ├── handler/
│   │   ├── CommandHandler.java
│   │   ├── StartHandler.java
│   │   ├── SearchHandler.java
│   │   └── ...
│   ├── service/
│   │   ├── OpenFdaService.java
│   │   ├── BookmarkService.java
│   │   └── StatisticsService.java
│   ├── dao/
│   │   └── DatabaseManager.java
│   └── model/
│       ├── Drug.java
│       └── Recall.java
├── data/
│   └── medbot.db (creato automaticamente)
├── config.properties (da creare)
├── pom.xml
└── README.md
```

## Database

Il bot utilizza SQLite per memorizzare dati localmente. Il database viene creato automaticamente in `./data/medbot.db`.

### Tabelle

- **users**: utenti che hanno usato il bot
- **searches**: storico ricerche effettuate
- **bookmarks**: farmaci salvati nei preferiti
- **drugs_cache**: cache farmaci (riduce chiamate API)

### Visualizzare il Database in IntelliJ IDEA

1. Apri **Database** tool (View → Tool Windows → Database)
2. Clicca **+** → Data Source → SQLite
3. Seleziona il file `data/medbot.db`
4. Clicca **Test Connection** → **Apply** → **OK**
5. Espandi le tabelle per visualizzare i dati
6. Fai **click destro** → **Refresh** per aggiornare i dati

**Nota**: Il database è nel `.gitignore` e non viene caricato su GitHub.

## Comandi Disponibili

| Comando | Descrizione |
|---------|-------------|
| `/start` | Avvia il bot e registra l'utente |
| `/help` | Mostra l'elenco dei comandi |
| `/cerca <nome>` | Cerca informazioni su un farmaco |
| `/richiami <nome>` | Verifica richiami FDA per un farmaco |
| `/effetti <nome>` | Consulta eventi avversi riportati |
| `/interazioni <farmaco1> <farmaco2> ...` | Verifica interazioni tra farmaci |
| `/bookmarks` | Visualizza i tuoi farmaci preferiti |
| `/bookmarks add <nome>` | Aggiungi un farmaco ai preferiti |
| `/bookmarks remove <nome>` | Rimuovi un farmaco dai preferiti |
| `/mystats` | Le tue statistiche personali |
| `/stats` | Statistiche globali del bot |
| `/recenti` | Le tue ultime ricerche |

## Utilizzo

1. Avvia una chat con il bot su Telegram
2. Invia `/start` per iniziare
3. Usa `/cerca aspirin` per cercare un farmaco
4. Clicca sui bottoni inline per azioni rapide
5. Salva i farmaci preferiti con il bottone "Salva"
6. Monitora le tue statistiche con `/mystats`

## API Utilizzate

- **OpenFDA Drug Label API**: informazioni sui farmaci approvati FDA
- **OpenFDA Drug Enforcement API**: richiami e provvedimenti FDA
- **OpenFDA Drug Adverse Events API**: segnalazioni effetti collaterali

Tutte le API sono gratuite e pubbliche (nessuna chiave richiesta).

## Architettura

### Pattern Command con Dispatcher

Il bot utilizza il pattern **Command** con un **Dispatcher centrale**:

```
MedBot → CommandDispatcher → Handler Specifico
```

Ogni handler implementa l'interfaccia `CommandHandler` e gestisce un comando specifico.

### Service Layer

I servizi sono separati dalla logica dei comandi:
- `OpenFdaService`: tutte le chiamate API
- `BookmarkService`: gestione preferiti
- `StatisticsService`: tracciamento e statistiche

### Data Access Object (DAO)

`DatabaseManager` gestisce tutte le operazioni sul database con pattern Singleton.

## Note Importanti

- Il file `config.properties` con il token del bot NON è nel repository (per sicurezza)
- Il database `./data/medbot.db` è locale e NON sincronizzato su GitHub
- Le API FDA hanno limiti di rate (240 richieste/minuto)
- I dati della cache vengono mantenuti per 24 ore

## Autore

Progetto scolastico per la materia TPSIT - 5° superiore

**Repository**: https://github.com/Fanton-Lucrezia/FANTON_Telegram_Bot
