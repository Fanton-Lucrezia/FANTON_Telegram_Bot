# 🏥 OpenFDA MedBot - Telegram Bot

Un bot Telegram sviluppato in Java che fornisce informazioni su farmaci, richiami FDA e sicurezza farmaceutica utilizzando l'API pubblica OpenFDA.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

## 📋 Descrizione del Progetto

OpenFDA MedBot è un bot Telegram educativo che permette agli utenti di:
- 🔍 Cercare informazioni dettagliate sui farmaci
- ⚠️ Verificare richiami FDA (enforcement reports)
- 📊 Visualizzare statistiche personali di utilizzo
- 💾 Beneficiare di un sistema di cache locale per prestazioni ottimali

**⚠️ DISCLAIMER IMPORTANTE**: Questo bot fornisce **solo informazioni informative** e **non costituisce consulenza medica**. In caso di dubbi sulla salute o emergenze mediche, consultare sempre un professionista sanitario qualificato.

## 🔗 API Utilizzata

**OpenFDA API**: [https://api.fda.gov](https://api.fda.gov)

L'API OpenFDA fornisce accesso pubblico a dati della FDA (Food and Drug Administration) statunitense, tra cui:
- Etichette e informazioni sui farmaci
- Segnalazioni di eventi avversi
- Richiami e enforcement reports

**Documentazione**: [https://open.fda.gov/apis/](https://open.fda.gov/apis/)

**Nota**: L'API OpenFDA **non richiede una API key** per la maggior parte delle richieste, ma ha limiti di rate limiting (240 richieste/minuto, 120.000 richieste/giorno).

## 🛠️ Requisiti

- **JDK 21** o superiore
- **Maven 3.9+**
- **SQLite** (driver JDBC incluso nelle dipendenze)
- Account Telegram e un Bot Token (vedi sezione Setup)

## 📦 Dipendenze Principali

- `telegrambots-longpolling` 9.2.0 - Libreria Telegram Bot
- `sqlite-jdbc` 3.47.1.0 - Database SQLite
- `okhttp` 4.12.0 - Client HTTP per chiamate API
- `jackson-databind` 2.18.2 - Parsing JSON
- `slf4j` + `logback` - Logging
- `commons-configuration2` - Gestione configurazione

## 🚀 Setup e Installazione

### 1. Clona la Repository

```bash
git clone https://github.com/tuoaccount/FANTON_Telegram_Bot.git
cd FANTON_Telegram_Bot
```

### 2. Configura il Bot

1. Copia il file di esempio:
   ```bash
   cp src/main/resources/config.properties.example config.properties
   ```

2. Modifica `config.properties` e inserisci il tuo Bot Token:
   ```properties
   BOT_TOKEN=IL_TUO_BOT_TOKEN_QUI
   DB_PATH=./data/medbot.db
   ```

### 3. Compila il Progetto

```bash
mvn clean package
```

### 4. Esegui il Bot

```bash
mvn exec:java -Dexec.mainClass="org.medBot.Main"
```

Oppure esegui il JAR compilato:

```bash
java -jar target/FANTON_Telegram_Bot-1.0-SNAPSHOT.jar
```

Se tutto funziona correttamente, vedrai:
```
✓ MedBot is running. Press Ctrl+C to stop.
```

## 📱 Comandi Disponibili

| Comando | Descrizione | Esempio |
|---------|-------------|---------|
| `/start` | Messaggio di benvenuto e introduzione | `/start` |
| `/help` | Lista completa dei comandi | `/help` |
| `/cerca <nome>` | Cerca un farmaco per nome o principio attivo | `/cerca aspirina`<br>`/cerca ibuprofen` |
| `/richiami <nome\|all>` | Verifica richiami FDA per un farmaco specifico o recenti | `/richiami aspirina`<br>`/richiami all` |
| `/stats` | Visualizza le tue statistiche personali | `/stats` |

**Nota**: I comandi funzionano anche in inglese: `/searchdrug`, `/recalls`, `/mystats`

## 💬 Esempi di Conversazioni

### Esempio 1: Ricerca Farmaco

**Utente**: `/cerca aspirina`

**Bot**: 
```
🔍 Cerco "aspirina"...

✅ 3 risultati per "aspirina":

1. Aspirin
   📋 Principio attivo: Acetylsalicylic acid
   🏭 Produttore: Bayer
   💊 Indicazioni: Pain reliever and fever reducer. Used for 
   temporary relief of minor aches and pains...

[🔍 Controlla richiami per questo farmaco]
```

### Esempio 2: Farmaco Non Trovato

**Utente**: `/cerca farmaco-inesistente`

**Bot**:
```
❌ Nessun risultato per "farmaco-inesistente".

💡 Suggerimenti:
• Controlla l'ortografia
• Prova con il nome generico (es. 'ibuprofen')
• Usa il principio attivo del farmaco
• Prova in inglese (es. 'aspirin' invece di 'aspirina')
```

### Esempio 3: Verifica Richiami

**Utente**: `/richiami aspirina`

**Bot**:
```
✅ Nessun richiamo trovato per "aspirina".

🎉 Buone notizie! Non ci sono richiami recenti per questo farmaco.
```

### Esempio 4: Statistiche (Aggiornate)

**Utente**: `/mystats`

**Bot**:
```
📊 Le tue statistiche:

🔍 Ricerche effettuate: 5
👤 ID Telegram: 123456789

💡 Continua a usare il bot per esplorare più farmaci!
```

## 🗄️ Schema Database

Il bot utilizza SQLite con il seguente schema:

```sql
-- Tabella utenti
CREATE TABLE users (
    telegram_id INTEGER PRIMARY KEY,
    username TEXT,
    locale TEXT DEFAULT 'en',
    search_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabella storico ricerche
CREATE TABLE searches (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    telegram_id INTEGER,
    query_text TEXT NOT NULL,
    result_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(telegram_id) REFERENCES users(telegram_id)
);

-- Cache farmaci
CREATE TABLE drugs_cache (
    drug_id TEXT PRIMARY KEY,
    brand_name TEXT,
    generic_name TEXT,
    manufacturer TEXT,
    indications TEXT,
    last_fetched TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Cache richiami FDA
CREATE TABLE recalls_cache (
    recall_id TEXT PRIMARY KEY,
    product_description TEXT,
    reason_for_recall TEXT,
    classification TEXT,
    recall_date TEXT,
    fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Relazioni:
- `searches.telegram_id` → `users.telegram_id` (1:N)

### Indici:
- `idx_searches_telegram_id` su `searches(telegram_id)`
- `idx_searches_created_at` su `searches(created_at)`
- `idx_drugs_brand_name` su `drugs_cache(brand_name)`
- `idx_drugs_generic_name` su `drugs_cache(generic_name)`

## 📊 Statistiche e Query

Il database permette di estrarre statistiche interessanti:

```sql
-- Top 10 farmaci più cercati
SELECT query_text, COUNT(*) as count 
FROM searches 
GROUP BY LOWER(query_text) 
ORDER BY count DESC 
LIMIT 10;

-- Utenti più attivi
SELECT u.username, u.search_count 
FROM users u 
ORDER BY u.search_count DESC 
LIMIT 10;

-- Ricerche per giorno
SELECT DATE(created_at) as day, COUNT(*) as searches 
FROM searches 
GROUP BY DATE(created_at) 
ORDER BY day DESC;

-- Farmaci in cache più vecchi
SELECT brand_name, generic_name, last_fetched 
FROM drugs_cache 
ORDER BY last_fetched ASC 
LIMIT 10;
```

## 🏗️ Struttura del Progetto

```
FANTON_Telegram_Bot/
├── src/
│   ├── main/
│   │   ├── java/org/example/
│   │   │   ├── Main.java                  # Entry point
│   │   │   ├── MyConfiguration.java       # Config manager
│   │   │   ├── bot/
│   │   │   │   └── MedBot.java           # Logic bot principale
│   │   │   ├── service/
│   │   │   │   └── OpenFdaService.java   # Client API FDA
│   │   │   ├── dao/
│   │   │   │   └── DatabaseManager.java  # Gestione DB
│   │   │   ├── model/
│   │   │   │   ├── Drug.java             # Model farmaco
│   │   │   │   └── Recall.java           # Model richiamo
│   │   │   └── util/
│   │   │       └── TextUtils.java        # Utility testo
│   │   └── resources/
│   │       └── config.properties.example  # Template config
│   └── test/
│       └── java/org/example/
│           └── dao/
│               └── DatabaseManagerTest.java
├── data/                                  # Directory database
│   └── medbot.db                         # Database SQLite
├── pom.xml                               # Config Maven
├── .gitignore                            # File da ignorare
├── README.md                             # Questa guida
└── config.properties                     # Config personale (NON committare!)
```

## 🔒 Sicurezza e Privacy

### API Keys e Token
- ❌ **MAI** committare `config.properties` con token reali
- ✅ Usa `config.properties.example` come template
- ✅ Aggiungi `config.properties` al `.gitignore`

### Privacy Utenti
- Il bot memorizza solo: `telegram_id`, `username`, ricerche effettuate
- NON vengono salvati messaggi completi o dati sensibili
- Gli utenti possono richiedere la rimozione dei propri dati (funzionalità future: `/deletemydata`)

### GDPR Compliance
Il bot è progettato per essere conforme al GDPR:
- Dati minimali memorizzati
- Trasparenza su cosa viene salvato
- Possibilità di eliminazione dati

## 🧪 Testing

Esegui i test con:

```bash
mvn test
```

I test includono:
- Test di inizializzazione database
- Test utility (TextUtils)

## 🐛 Troubleshooting

### Il bot non risponde?
- Verifica che il token sia corretto in `config.properties`
- Controlla che il bot sia in esecuzione (finestra terminale aperta)
- Guarda i log per eventuali errori

### Non trovo il farmaco che cerco?
- **Prova in inglese**: L'API FDA usa nomi inglesi (es. "aspirin" invece di "aspirina")
- Usa il nome generico (es. "ibuprofen" invece di "Advil")
- Controlla l'ortografia
- Prova con il principio attivo

### Errore 404 durante la ricerca?
- **MIGLIORATO**: Ora ricevi un messaggio chiaro con suggerimenti
- L'errore 404 significa che il farmaco non è nel database FDA
- Prova con un nome diverso o più specifico

## 📝 Licenza

Questo progetto è distribuito con licenza MIT. Vedi file `LICENSE` per dettagli.

## 👨‍💻 Autore

**[FANTON]** - Progetto per corso universitario
