# 💊 MedBot - Bot Telegram per Informazioni Farmaci

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.java.com)
[![Telegram](https://img.shields.io/badge/Telegram-Bot-blue.svg)](https://telegram.org)
[![OpenFDA](https://img.shields.io/badge/API-OpenFDA-green.svg)](https://open.fda.gov)
[![License](https://img.shields.io/badge/License-Educational-yellow.svg)](LICENSE)

> Bot Telegram per ricercare informazioni su farmaci utilizzando le API ufficiali della FDA (Food and Drug Administration) americana.

---

## 📝 Descrizione Progetto

**MedBot** è un bot Telegram educativo che permette di:
- 🔍 **Cercare farmaci** per nome con informazioni dettagliate
- ⚠️ **Verificare richiami FDA** per controllare la sicurezza
- 🔬 **Controllare interazioni** tra più farmaci
- 🚨 **Verificare sostanze controllate** (rischio dipendenza)
- 🔴 **Consultare effetti collaterali** segnalati
- ⭐ **Salvare farmaci preferiti** per accesso rapido
- 📊 **Visualizzare statistiche** personali di utilizzo

Il bot utilizza un'**interfaccia moderna** con:
- 🟢 Tastiera permanente per comandi rapidi
- 🔘 Bottoni inline per azioni contestuali
- 📋 Paginazione per navigare tra i risultati
- 💾 Database SQLite per caching e storico

---

## 🔗 API Utilizzate

Il bot si basa sulle **OpenFDA API** ufficiali della FDA americana:

### 1. Drug Label API
- **Endpoint**: `https://api.fda.gov/drug/label.json`
- **Documentazione**: [https://open.fda.gov/apis/drug/label/](https://open.fda.gov/apis/drug/label/)
- **Uso**: Informazioni dettagliate sui farmaci (indicazioni, produttore, principio attivo)

### 2. Drug Enforcement API
- **Endpoint**: `https://api.fda.gov/drug/enforcement.json`
- **Documentazione**: [https://open.fda.gov/apis/drug/enforcement/](https://open.fda.gov/apis/drug/enforcement/)
- **Uso**: Richiami e ritiri di farmaci dal mercato

### 3. Drug Event API (FAERS)
- **Endpoint**: `https://api.fda.gov/drug/event.json`
- **Documentazione**: [https://open.fda.gov/apis/drug/event/](https://open.fda.gov/apis/drug/event/)
- **Uso**: Eventi avversi (effetti collaterali) e interazioni tra farmaci

### Parametri di Ricerca OpenFDA
- **Documentazione generale**: [https://open.fda.gov/apis/query-parameters/](https://open.fda.gov/apis/query-parameters/)
- **Esempi di query**: [https://open.fda.gov/apis/drug/label/example-api-queries/](https://open.fda.gov/apis/drug/label/example-api-queries/)

---

## 🛠️ Setup e Installazione

### Prerequisiti

- **Java 17+** installato ([Download](https://www.oracle.com/java/technologies/downloads/))
- **Maven 3.6+** installato ([Download](https://maven.apache.org/download.cgi))
- **Bot Telegram** creato tramite [@BotFather](https://t.me/BotFather)

### 1️⃣ Clona il Repository

```bash
git clone https://github.com/Fanton-Lucrezia/FANTON_Telegram_Bot.git
cd FANTON_Telegram_Bot
```

### 2️⃣ Crea il File di Configurazione

Crea il file `config.properties` nella root del progetto:

```properties
# Token del bot ottenuto da @BotFather
BOT_TOKEN=il_tuo_bot_token_qui

# Percorso del database SQLite
DB_PATH=./data/medbot.db
```

⚠️ **Importante**: Il file `config.properties` è già nel `.gitignore` per proteggere il token.

### 3️⃣ Installa le Dipendenze

```bash
mvn clean install
```

Il `pom.xml` include tutte le dipendenze necessarie:
- `telegrambots-longpolling` (7.10.0) - Gestione bot Telegram
- `sqlite-jdbc` (3.47.2.0) - Database SQLite
- `okhttp` (4.12.0) - Client HTTP per API
- `jackson-databind` (2.18.2) - Parsing JSON

### 4️⃣ Inizializza il Database

Il database viene creato **automaticamente** al primo avvio del bot con le tabelle necessarie.

### 5️⃣ Compila ed Esegui

```bash
# Compila il progetto
mvn clean package

# Esegui il bot
java -jar target/FANTON_Telegram_Bot-1.0-SNAPSHOT.jar
```

Dovresti vedere:
```
Database inizializzato correttamente!
MedBot inizializzato con 10 comandi
Bot avviato con successo!
```

---

## 📱 Guida all'Utilizzo

### Comandi Disponibili

#### 🎯 Comandi Base
- `/start` - Messaggio di benvenuto e attivazione menù
- `/help` - Guida completa ai comandi

#### 🔍 Ricerca Farmaci
```
/cerca <nome>
```
Ricerca farmaci per nome (brand o generico).

**Esempio**: `/cerca aspirin`

#### ⚠️ Sicurezza e Richiami
```
/richiami <nome|all>
```
Verifica richiami FDA per un farmaco o mostra tutti i richiami recenti.

**Esempi**: 
- `/richiami aspirin`
- `/richiami all`

#### 🚨 Sostanze Controllate
```
/farmacolegale <nome>
```
Verifica se un farmaco è classificato come sostanza controllata (DEA Schedule).

**Esempio**: `/farmacolegale oxycodone`

**Classificazioni**:
- **Schedule I**: Alto potenziale abuso, nessun uso medico
- **Schedule II**: Alto potenziale abuso (morfina, oxycodone, metadone)
- **Schedule III**: Moderato potenziale abuso (codeina, ketamina)
- **Schedule IV**: Basso potenziale abuso (xanax, valium)
- **Schedule V**: Molto basso potenziale abuso

#### 🔴 Effetti Collaterali
```
/effetticollaterali <nome>
```
Mostra effetti collaterali segnalati al sistema FAERS della FDA.

**Esempio**: `/effetticollaterali ibuprofen`

#### 🔬 Interazioni tra Farmaci
```
/interazioni <farmaco1 + farmaco2 + ...>
```
Verifica se ci sono segnalazioni di eventi avversi quando i farmaci sono usati insieme.

**Esempio**: `/interazioni aspirin + ibuprofen`

#### 📊 Statistiche Personali
```
/mystats
```
Mostra le tue statistiche: ricerche totali e farmaci più cercati.

```
/recenti
```
Visualizza le ultime 10 ricerche effettuate.

#### ⭐ Gestione Preferiti
```
/bookmarks                    # Mostra lista preferiti
/bookmarks add <nome>         # Aggiungi farmaco
/bookmarks remove <nome>      # Rimuovi farmaco
```

**Esempi**:
- `/bookmarks add aspirin`
- `/bookmarks`
- `/bookmarks remove aspirin`

---

## 💬 Esempi di Conversazione

### Esempio 1: Ricerca Farmaco

```
👤 Utente: /cerca aspirin

🤖 Bot:
✅ 3 risultati per "aspirin":

1. Aspirin
   📋 Principio attivo: Aspirin
   🏭 Produttore: Bayer Healthcare
   💊 Indicazioni:
   • For temporary relief of minor aches and pains.
   • Reduces risk of heart attack and stroke.

[🔍 Richiami] [⭐ Salva]
```

### Esempio 2: Verifica Interazioni

```
👤 Utente: /interazioni aspirin + warfarin

🤖 Bot:
⚠️ POSSIBILI INTERAZIONI

Farmaci: aspirin + warfarin

📊 247 segnalazioni di eventi avversi quando questi 
farmaci sono usati insieme.

🔴 Reazioni più comuni:
• Haemorrhage
• International normalised ratio increased
• Gastrointestinal haemorrhage

🚨 IMPORTANTE:
• NON interrompere i farmaci senza consultare un medico
• Consulta un medico o farmacista per informazioni accurate
```

### Esempio 3: Sostanza Controllata

```
👤 Utente: /farmacolegale oxycodone

🤖 Bot:
🚨 SOSTANZA CONTROLLATA

Farmaco: oxycodone
Classificazione: 🔴 Schedule II

📋 Alto potenziale di abuso, rischio grave dipendenza

⚠️ Richiede prescrizione speciale.
```

---

## 💾 Schema Database

Il bot utilizza **SQLite** con 4 tabelle principali:

### Tabella `users`
Memorizza informazioni sugli utenti del bot.

```sql
CREATE TABLE users (
    telegram_id INTEGER PRIMARY KEY,
    username TEXT,
    search_count INTEGER DEFAULT 0,
    first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Campi**:
- `telegram_id` - ID univoco Telegram dell'utente
- `username` - Username Telegram
- `search_count` - Numero totale di ricerche effettuate
- `first_seen` - Data prima interazione
- `last_active` - Data ultima attività

### Tabella `searches`
Storia completa delle ricerche effettuate.

```sql
CREATE TABLE searches (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    telegram_id INTEGER NOT NULL,
    query_text TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (telegram_id) REFERENCES users(telegram_id)
);

CREATE INDEX idx_searches_telegram_id ON searches(telegram_id);
CREATE INDEX idx_searches_created_at ON searches(created_at);
```

**Campi**:
- `telegram_id` - Riferimento all'utente
- `query_text` - Testo della ricerca
- `created_at` - Timestamp ricerca

### Tabella `drugs_cache`
Cache delle informazioni sui farmaci per ridurre chiamate API.

```sql
CREATE TABLE drugs_cache (
    drug_id TEXT PRIMARY KEY,
    brand_name TEXT,
    generic_name TEXT,
    manufacturer TEXT,
    indications TEXT,
    last_fetched TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_drugs_brand ON drugs_cache(brand_name);
CREATE INDEX idx_drugs_generic ON drugs_cache(generic_name);
```

**Campi**:
- `drug_id` - ID univoco generato
- `brand_name` - Nome commerciale
- `generic_name` - Nome generico
- `manufacturer` - Produttore
- `indications` - Indicazioni terapeutiche
- `last_fetched` - Data recupero da API

**Cache Duration**: 24 ore

### Tabella `bookmarks`
Farmaci preferiti salvati dagli utenti.

```sql
CREATE TABLE bookmarks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    telegram_id INTEGER NOT NULL,
    drug_name TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(telegram_id, drug_name),
    FOREIGN KEY (telegram_id) REFERENCES users(telegram_id)
);

CREATE INDEX idx_bookmarks_telegram_id ON bookmarks(telegram_id);
```

**Campi**:
- `telegram_id` - Riferimento all'utente
- `drug_name` - Nome farmaco salvato
- `created_at` - Data aggiunta

### Relazioni tra Tabelle

```
users (1) ----< (*) searches
  |  
  └-----------< (*) bookmarks
```

---

## 📊 Esempi di Query SQL

### Query 1: Top 10 farmaci più cercati
```sql
SELECT 
    query_text, 
    COUNT(*) as search_count
FROM searches
GROUP BY LOWER(query_text)
ORDER BY search_count DESC
LIMIT 10;
```

### Query 2: Utenti più attivi
```sql
SELECT 
    username, 
    search_count,
    DATE(last_active) as ultima_attivita
FROM users
ORDER BY search_count DESC
LIMIT 10;
```

### Query 3: Ricerche per giorno
```sql
SELECT 
    DATE(created_at) as data,
    COUNT(*) as ricerche_giornaliere
FROM searches
GROUP BY DATE(created_at)
ORDER BY data DESC
LIMIT 30;
```

### Query 4: Farmaci più salvati nei preferiti
```sql
SELECT 
    drug_name,
    COUNT(*) as utenti_che_lhanno_salvato
FROM bookmarks
GROUP BY LOWER(drug_name)
ORDER BY utenti_che_lhanno_salvato DESC
LIMIT 10;
```

### Query 5: Efficacia cache
```sql
SELECT 
    COUNT(*) as farmaci_in_cache,
    COUNT(CASE WHEN last_fetched > datetime('now', '-24 hours') 
          THEN 1 END) as cache_valida
FROM drugs_cache;
```

---

## 📚 Struttura Progetto

```
FANTON_Telegram_Bot/
├── src/main/java/org/medBot/
│   ├── bot/
│   │   └── MedBot.java              # Bot principale (semplificato)
│   ├── handler/                  # Handler per ogni comando
│   │   ├── CommandHandler.java      # Interfaccia base
│   │   ├── StartHandler.java
│   │   ├── HelpHandler.java
│   │   ├── SearchHandler.java
│   │   ├── RecallsHandler.java
│   │   ├── ControlledSubstanceHandler.java
│   │   ├── AdverseEventsHandler.java
│   │   ├── InteractionsHandler.java
│   │   ├── StatsHandler.java
│   │   ├── RecentHandler.java
│   │   └── BookmarksHandler.java
│   ├── service/
│   │   └── OpenFdaService.java      # Gestione API FDA
│   ├── dao/
│   │   └── DatabaseManager.java     # Gestione database
│   ├── model/
│   │   ├── Drug.java               # Modello farmaco
│   │   └── Recall.java             # Modello richiamo
│   ├── util/
│   │   └── MessageSender.java      # Utility invio messaggi
│   └── MyConfiguration.java      # Configurazione
├── config.properties             # Configurazione (NON su Git)
├── pom.xml                       # Dipendenze Maven
└── README.md                     # Questo file
```

### Architettura

1. **Handler Pattern**: Ogni comando ha il suo handler dedicato per separazione delle responsabilità
2. **Service Layer**: `OpenFdaService` gestisce tutte le chiamate API
3. **DAO Layer**: `DatabaseManager` gestisce accesso database
4. **Utility Classes**: `MessageSender` centralizza invio messaggi

---

## ⚙️ Caratteristiche Tecniche

### Caching Intelligente
- Le informazioni sui farmaci vengono salvate nel database per **24 ore**
- Riduce le chiamate API e migliora le performance
- Cache trasparente all'utente

### Gestione Errori
- Try-catch su tutte le operazioni critiche
- Messaggi di errore user-friendly
- Log essenziali con `System.out.println`

### Paginazione
- Risultati divisi in pagine navigabili
- Bottoni "Altri risultati" per caricare altre pagine
- Limite configurabile per tipo di contenuto

### Sicurezza
- Token bot in file di configurazione separato (non su Git)
- Validazione input utente
- Prepared statements per prevenire SQL injection

---

## 🚧 Note Importanti

⚠️ **Disclaimer Medico**

Questo bot è **solo a scopo educativo**. Le informazioni fornite:
- NON costituiscono consulenza medica
- NON sostituiscono il parere di un medico
- Provengono da database FDA che potrebbero non essere completi
- I dati sono in **inglese** (database FDA)

**Consultare sempre un professionista sanitario qualificato.**

🇺🇸 **Lingua dei Dati**

I dati provengono dalla FDA americana, quindi:
- Cercare farmaci con **nomi inglesi** (es. "aspirin" non "aspirina")
- Risultati e descrizioni sono in **inglese**
- Alcuni farmaci potrebbero non essere disponibili in Italia

---

## 👩‍💻 Autore

**Fanton Lucrezia**  
Progetto scolastico - TPSIT - 5° Superiore

GitHub: [@Fanton-Lucrezia](https://github.com/Fanton-Lucrezia)

---

## 📜 Licenza

Progetto educativo per scopi didattici.

---

## 🔗 Link Utili

- [OpenFDA Documentation](https://open.fda.gov/apis/)
- [Telegram Bot API](https://core.telegram.org/bots/api)
- [TelegramBots Java Library](https://github.com/rubenlagus/TelegramBots)
- [FDA Drug Information](https://www.fda.gov/drugs)

---

<p align="center">
  <i>Made with ❤️ for educational purposes</i>
</p>