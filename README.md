# 💊 MedBot - Bot Telegram per Informazioni su Farmaci

![Java](https://img.shields.io/badge/Java-21-orange)
![Telegram](https://img.shields.io/badge/Telegram-Bot-blue)
![FDA](https://img.shields.io/badge/API-OpenFDA-green)
![Database](https://img.shields.io/badge/Database-SQLite-lightgrey)

## 📝 Descrizione Progetto

**MedBot** è un bot Telegram sviluppato in Java che fornisce informazioni su farmaci e medicine utilizzando le API pubbliche della **FDA (Food and Drug Administration)** americana. Il bot permette agli utenti di cercare farmaci, verificare richiami di sicurezza, controllare effetti collaterali e molto altro.

Il progetto è stato sviluppato per la materia **TPSIT** (Tecnologie e Progettazione di Sistemi Informatici e di Telecomunicazioni) come progetto scolastico.

### ✨ Caratteristiche Principali

- 🔍 **Ricerca farmaci** con informazioni dettagliate
- ⚠️ **Verifica richiami FDA** per la sicurezza
- 💊 **Controllo effetti collaterali** segnalati
- 🚨 **Verifica sostanze controllate** (farmaci con rischio dipendenza)
- 🔬 **Analisi interazioni tra farmaci** (NUOVO!)
- 📊 **Statistiche personali** e cronologia ricerche
- ⭐ **Bookmark** per salvare farmaci preferiti
- 🎹 **Menù con bottoni** per facilità d'uso
- 💾 **Database SQLite** per caching e persistenza dati

---

## 🔗 API Utilizzate

### 1. OpenFDA API

**Link:** [https://open.fda.gov/apis/](https://open.fda.gov/apis/)

La **OpenFDA API** è l'API pubblica della Food and Drug Administration americana. Fornisce accesso a diversi database governativi:

- **Drug Labels** - Informazioni sui farmaci approvati
- **Drug Enforcement** - Richiami di sicurezza
- **Drug Adverse Events** - Segnalazioni di effetti collaterali

#### Documentazione API OpenFDA:
- [Drug Labels API](https://open.fda.gov/apis/drug/label/)
- [Drug Enforcement API](https://open.fda.gov/apis/drug/enforcement/)
- [Drug Adverse Events API](https://open.fda.gov/apis/drug/event/)

#### Note Importanti:
- L'API è **gratuita** e non richiede API key
- I dati sono in **lingua inglese**
- Limite di 1000 richieste al giorno per IP

### 2. Telegram Bot API

**Link:** [https://core.telegram.org/bots/api](https://core.telegram.org/bots/api)

Utilizzata tramite la libreria **telegrambots** per l'interazione con gli utenti.

---

## 🛠️ Setup e Installazione

### Prerequisiti

- **Java 21** o superiore
- **Maven 3.x**
- Account Telegram

### 1. Crea il Bot Telegram

1. Apri Telegram e cerca **@BotFather**
2. Invia il comando `/newbot`
3. Segui le istruzioni per scegliere un nome e username
4. **Salva il token** che ricevi (es. `123456789:ABCdefGHIjklMNOpqrsTUVwxyz`)

### 2. Clona il Repository

```bash
git clone https://github.com/Fanton-Lucrezia/FANTON_Telegram_Bot.git
cd FANTON_Telegram_Bot
```

### 3. Configura il Bot

Crea un file `config.properties` nella directory principale del progetto:

```properties
BOT_TOKEN=il_tuo_token_qui
DB_PATH=./data/medbot.db
```

**Importante:** Il file `config.properties` è ignorato da git (`.gitignore`) per sicurezza.

### 4. Compila il Progetto

```bash
mvn clean package
```

Questo comando:
- Scarica le dipendenze necessarie
- Compila il codice Java
- Crea il file JAR eseguibile in `target/`

### 5. Avvia il Bot

```bash
java -jar target/FANTON_Telegram_Bot-1.0-SNAPSHOT.jar
```

Il bot sarà attivo e risponderà ai comandi su Telegram!

---

## 📚 Guida all'Utilizzo

### Menù Principale

Appena avvii il bot con `/start`, apparirà una **tastiera permanente** con i comandi principali:

```
[🔍 Cerca]  [⚠️ Richiami]  [🔬 Interazioni]
[📊 Stats]  [⭐ Preferiti]  [❓ Help]
```

Puoi usare sia i bottoni che digitare i comandi manualmente.

### 📝 Comandi Disponibili

#### Comandi Base

- `/start` - Messaggio di benvenuto e menù
- `/help` - Mostra la guida completa dei comandi

#### 🔍 Ricerca Farmaci

```
/cerca <nome_farmaco>
```

**Esempio:**
```
/cerca aspirin
```

**Cosa restituisce:**
- Nome commerciale (brand name)
- Principio attivo (generic name)
- Produttore
- Indicazioni terapeutiche
- Bottoni per azioni rapide (richiami, salva preferiti)

#### ⚠️ Sicurezza e Legalità

**1. Verifica Richiami FDA**
```
/richiami <nome_farmaco>
/richiami all
```

**Esempi:**
```
/richiami aspirin
/richiami all
```

Mostra i richiami di sicurezza con classificazione:
- **Class I**: Rischio grave per la salute
- **Class II**: Rischio temporaneo
- **Class III**: Rischio minimo

**2. Verifica Sostanza Controllata**
```
/farmacolegale <nome_farmaco>
```

**Esempio:**
```
/farmacolegale oxycodone
```

Verifica se un farmaco è classificato come sostanza controllata dalla DEA (Drug Enforcement Administration) e mostra la classificazione Schedule (I-V).

**3. Effetti Collaterali**
```
/effetticollaterali <nome_farmaco>
```

**Esempio:**
```
/effetticollaterali aspirin
```

Mostra gli effetti collaterali più segnalati dagli utenti nel database FDA.

**4. Interazioni tra Farmaci** 🆕
```
/interazioni <farmaco1 + farmaco2>
```

**Esempi:**
```
/interazioni aspirin + ibuprofen
/interazioni warfarin + aspirin
/interazioni omeprazole + clopidogrel
```

Verifica se ci sono segnalazioni di eventi avversi quando i farmaci vengono usati insieme. **Questo comando sostituisce il vecchio `/informazioni` che non era affidabile.**

#### 📊 Statistiche Personali

**1. Le Tue Statistiche**
```
/mystats
```

Mostra:
- Numero totale di ricerche
- Farmaci più cercati
- Il tuo ID Telegram

**2. Ricerche Recenti**
```
/recenti
```

Mostra gli ultimi 10 farmaci che hai cercato.

**3. Gestione Preferiti**
```
/bookmarks
/bookmarks add <farmaco>
/bookmarks remove <farmaco>
```

**Esempi:**
```
/bookmarks
/bookmarks add aspirin
/bookmarks remove aspirin
```

---

## 📸 Esempi di Conversazioni

### Esempio 1: Ricerca Farmaco

**Utente:**
```
/cerca ibuprofen
```

**Bot:**
```
✅ 3 risultati per "ibuprofen":

1. ADVIL
   📋 Principio attivo: ibuprofen
   🏭 Produttore: Pfizer Consumer Healthcare
   💊 Indicazioni:
   • Relief of minor aches and pains
   • Reduction of fever
   • Temporary relief of minor pain of arthritis

[⬇️ Altri 2 risultati] [🔍 Richiami] [⭐ Salva]
```

### Esempio 2: Verifica Interazioni

**Utente:**
```
/interazioni aspirin + ibuprofen
```

**Bot:**
```
⚠️ POSSIBILI INTERAZIONI

Farmaci: aspirin + ibuprofen

📊 45 segnalazioni di eventi avversi quando 
questi farmaci sono usati insieme.

🔴 Reazioni più comuni:
• Gastrointestinal haemorrhage
• Nausea
• Vomiting
• Abdominal pain

🚨 IMPORTANTE:
• NON interrompere i farmaci senza consultare un medico
• Queste sono segnalazioni, non certezze scientifiche
• Consulta un medico o farmacista per info accurate

Fonte: FDA Adverse Event Reporting System (FAERS)
```

### Esempio 3: Statistiche Personali

**Utente:**
```
/mystats
```

**Bot:**
```
📊 Le tue statistiche:

🔍 Ricerche totali: 15
👤 ID Telegram: 123456789

💊 Farmaci più cercati:
1. aspirin (5 volte)
2. ibuprofen (3 volte)
3. paracetamol (2 volte)

💡 Usa /recenti per le ultime ricerche!
```

---

## 💾 Schema Database

Il bot utilizza **SQLite** per memorizzare dati degli utenti e cache delle ricerche.

### Tabelle

#### 1. **users**
Memorizza informazioni sugli utenti del bot.

| Campo | Tipo | Descrizione |
|-------|------|-------------|
| `telegram_id` | INTEGER | ID Telegram (PRIMARY KEY) |
| `username` | TEXT | Username Telegram |
| `search_count` | INTEGER | Numero totale ricerche |
| `last_active` | TIMESTAMP | Ultima attività |
| `created_at` | TIMESTAMP | Data registrazione |

#### 2. **searches**
Memorizza tutte le ricerche effettuate.

| Campo | Tipo | Descrizione |
|-------|------|-------------|
| `id` | INTEGER | ID ricerca (AUTOINCREMENT) |
| `telegram_id` | INTEGER | ID utente (FOREIGN KEY) |
| `query_text` | TEXT | Testo cercato |
| `created_at` | TIMESTAMP | Data ricerca |

#### 3. **drugs_cache**
Cache dei farmaci per ridurre chiamate API.

| Campo | Tipo | Descrizione |
|-------|------|-------------|
| `drug_id` | TEXT | ID farmaco (PRIMARY KEY) |
| `brand_name` | TEXT | Nome commerciale |
| `generic_name` | TEXT | Principio attivo |
| `manufacturer` | TEXT | Produttore |
| `indications` | TEXT | Indicazioni terapeutiche |
| `last_fetched` | TIMESTAMP | Ultimo aggiornamento |

#### 4. **bookmarks**
Farmaci preferiti degli utenti.

| Campo | Tipo | Descrizione |
|-------|------|-------------|
| `id` | INTEGER | ID bookmark (AUTOINCREMENT) |
| `telegram_id` | INTEGER | ID utente (FOREIGN KEY) |
| `drug_name` | TEXT | Nome farmaco |
| `created_at` | TIMESTAMP | Data salvataggio |

### Relazioni

```
users (1) ----< (*) searches
users (1) ----< (*) bookmarks
```

### Indici per Performance

```sql
CREATE INDEX idx_searches_telegram_id ON searches(telegram_id);
CREATE INDEX idx_searches_created_at ON searches(created_at);
CREATE INDEX idx_bookmarks_telegram_id ON bookmarks(telegram_id);
CREATE INDEX idx_drugs_cache_names ON drugs_cache(brand_name, generic_name);
```

---

## 📊 Esempi di Query e Statistiche

### Query 1: Top 10 Farmaci Più Cercati (Globale)

```sql
SELECT 
    query_text, 
    COUNT(*) as search_count
FROM searches
GROUP BY LOWER(query_text)
ORDER BY search_count DESC
LIMIT 10;
```

### Query 2: Utenti Più Attivi

```sql
SELECT 
    u.username, 
    u.search_count, 
    u.last_active
FROM users u
ORDER BY u.search_count DESC
LIMIT 10;
```

### Query 3: Ricerche Per Giorno

```sql
SELECT 
    DATE(created_at) as date, 
    COUNT(*) as searches
FROM searches
GROUP BY DATE(created_at)
ORDER BY date DESC;
```

### Query 4: Farmaci Preferiti Più Salvati

```sql
SELECT 
    drug_name, 
    COUNT(*) as bookmark_count
FROM bookmarks
GROUP BY LOWER(drug_name)
ORDER BY bookmark_count DESC
LIMIT 10;
```

---

## 📚 Struttura del Progetto

```
FANTON_Telegram_Bot/
├── src/
│   └── main/
│       └── java/
│           └── org/
│               └── medBot/
│                   ├── Main.java                  # Entry point
│                   ├── MyConfiguration.java       # Gestione config
│                   ├── bot/
│                   │   └── MedBot.java            # Logica principale bot
│                   ├── dao/
│                   │   └── DatabaseManager.java   # Gestione database
│                   ├── model/
│                   │   ├── Drug.java              # Modello Farmaco
│                   │   └── Recall.java            # Modello Richiamo
│                   ├── service/
│                   │   └── OpenFdaService.java    # Chiamate API FDA
│                   └── util/
├── pom.xml                            # Dipendenze Maven
├── config.properties                  # Configurazione (non in git)
├── .gitignore
└── README.md
```

---

## 📦 Dipendenze Maven

Il progetto utilizza le seguenti librerie (da `pom.xml`):

```xml
<!-- Telegram Bot API -->
<dependency>
    <groupId>org.telegram</groupId>
    <artifactId>telegrambots-longpolling</artifactId>
    <version>9.2.0</version>
</dependency>

<dependency>
    <groupId>org.telegram</groupId>
    <artifactId>telegrambots-client</artifactId>
    <version>9.2.0</version>
</dependency>

<!-- Database SQLite -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.47.1.0</version>
</dependency>

<!-- HTTP Client -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>

<!-- JSON Processing -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.18.2</version>
</dependency>

<!-- Logging -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.16</version>
</dependency>

<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.15</version>
</dependency>

<!-- Configuration Management -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-configuration2</artifactId>
    <version>2.12.0</version>
</dependency>
```

---

## 👩‍💻 Sviluppo e Miglioramenti Futuri

### Possibili Estensioni

- 🌍 Aggiungere supporto multilingua (traduzione automatica)
- 📊 Grafici e visualizzazioni dati sulle statistiche
- 🔔 Sistema di notifiche per nuovi richiami
- 👥 Funzionalità social (condivisione ricerche)
- 🤖 Suggerimenti intelligenti basati su cronologia
- 📱 App mobile companion

---

## ⚠️ Disclaimer Importante

**Questo bot fornisce informazioni a scopo educativo e informativo. NON sostituisce il parere di un medico o farmacista professionista.**

- Le informazioni provengono da database pubblici FDA
- I dati potrebbero non essere completi o aggiornati
- In caso di dubbi medici, consulta sempre un professionista sanitario
- Non interrompere o modificare terapie senza consulto medico

---

## 📜 Licenza

Questo progetto è stato sviluppato per scopi educativi come progetto scolastico per la materia TPSIT.

---

## 👤 Autore

**Lucrezia Fanton**
- GitHub: [@Fanton-Lucrezia](https://github.com/Fanton-Lucrezia)
- Progetto: TPSIT 5° Superiore

---

## 🔗 Link Utili

- [OpenFDA API Documentation](https://open.fda.gov/apis/)
- [Telegram Bot API](https://core.telegram.org/bots/api)
- [SQLite Documentation](https://www.sqlite.org/docs.html)
- [Maven Guide](https://maven.apache.org/guides/)

---

**Buon utilizzo! 💊🤖**