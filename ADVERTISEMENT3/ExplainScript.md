# 🎤 Advertising Management System — Code Explanation Script
### Four Presenters · Each Explains Their Files in Full Detail

---

## 👤 PERSON 1 — ALEX
### Files: `Advertisement.java`, `Advertiser.java`, `Campaign.java`, `Payment.java`
### **Topic: Core Data Model (OOP Foundations)**

---

**ALEX:** Hello everyone! I'm Alex, and I'll be explaining the **core data model** of our Advertising Management System — the building blocks that every other part of the system depends on.

---

### 📄 File 1: `Advertisement.java` — The Abstract Base Class

```java
public abstract class Advertisement {
    private int adId;
    private String title;
    private String description;
    private double budget;
    private String status;

    static int totalAds = 0;
    final double TAX = 0.18;
    ...
}
```

This is an **abstract class** — meaning you **cannot create an object directly** from it. It's like a blueprint.

- `adId`, `title`, `description`, `budget`, `status` — these are **private fields**. They use **encapsulation**, meaning outside classes cannot access them directly. They must use the **getter methods** like `getAdId()`, `getTitle()`, etc.
- `static int totalAds = 0` — This is a **class-level variable** shared across ALL instances. Every time a new Advertisement object is created, the constructor does `totalAds++`, tracking the total count.
- `final double TAX = 0.18` — A constant (18% GST tax). `final` means it **cannot be changed** after it's set.
- The constructor sets `status = "Active"` by default.
- `getCategory()` is **abstract** — every subclass MUST override it.
- `getTotalCost()` computes: **budget + 18% tax**.
- `showTotalAds()` is a **static method** called on the class: `Advertisement.showTotalAds()`.

---

### 📄 File 2: `Advertiser.java` — The Business Entity

A simple POJO (Plain Old Java Object) holding data about the person/company who owns an advertisement. All fields are `private` with getters. Used when someone registers a new advertiser; data is saved to the PostgreSQL `advertiser` table via `AdvertisingSystem.addAdvertiser()`.

---

### 📄 File 3: `Campaign.java` — Grouping Multiple Ads

A Campaign groups multiple advertisements together. `adIds` is an `ArrayList<Integer>` — a dynamic list that grows as ads are added. `addAd(int adId)` appends to the list. When saved to the DB, a **transaction** inserts the campaign then all its ad links atomically.

---

### 📄 File 4: `Payment.java` — Tracking Ad Payments

Represents a single payment record. The constructor automatically sets `status = "Paid"`. Created in `Main.java` option 4, then passed to `AdvertisingSystem.addPayment()` to insert into the PostgreSQL `payment` table.

---

**ALEX (Summary):** These four files form the **data layer** — `Advertisement` is the abstract base, `Advertiser` tracks ownership, `Campaign` groups ads, and `Payment` records transactions. Core concepts: **encapsulation**, **abstraction**, **static/final**, **ArrayList**.

---
---

## 👤 PERSON 2 — PRIYA
### Files: `DigitalAdvertisement.java`, `TVAdvertisement.java`, `NewspaperAdvertisement.java`, `ErrorHandlingDemo.java`, `InvalidAdvertisementException.java`
### **Topic: Inheritance, Polymorphism & Exception Handling**

---

**PRIYA:** Hi everyone! I'm Priya. I'll explain **inheritance** and **polymorphism** through three advertisement types, and then **error and exception handling**.

---

### 📄 File 1: `DigitalAdvertisement.java`

```java
public class DigitalAdvertisement extends Advertisement {
    private String platform;
    private String audience;
}
```

- `extends Advertisement` — inherits all base class fields and methods.
- Constructor calls `super(adId, title, description, budget)` — invokes the **parent constructor**.
- `@Override getCategory()` returns `"Digital"`.
- `@Override display()` calls `super.display()` first, then adds platform, audience, and total cost.

---

### 📄 File 2: `TVAdvertisement.java`

Same pattern — adds `seconds` (duration) and `channel`. `getCategory()` returns `"TV"`.

---

### 📄 File 3: `NewspaperAdvertisement.java`

Adds `newspaper` name and `pages` count. `getCategory()` returns `"Newspaper"`.

**Why three classes?** Open/Closed Principle — add new ad types without changing the base class. All three work anywhere `Advertisement` is accepted — that's **polymorphism**.

---

### 📄 File 4: `InvalidAdvertisementException.java`

```java
public class InvalidAdvertisementException extends Exception {
    public InvalidAdvertisementException(String message) {
        super(message);
    }
}
```

A **custom checked exception** — Java forces you to handle it or declare `throws`. Thrown in `WebAdRuntime.validate()` when title is empty, budget is zero, or duration is invalid.

---

### 📄 File 5: `ErrorHandlingDemo.java` — Six Exception Scenarios

| # | Scenario | Exception Type |
|---|----------|---------------|
| 1 | Parsing `"abc"` as a number | `NumberFormatException` |
| 2 | Negative budget | Custom `InvalidBudgetException` |
| 3 | Dividing by zero clicks | `ArithmeticException` (multiple catch) |
| 4 | Out-of-bounds array / null string | `ArrayIndexOutOfBoundsException`, `NullPointerException` |
| 5 | Code after a crash | `finally` block (always runs) |
| 6 | Wrong DB password | `SQLException` |

Key concept: `finally` always executes — used to close connections, files, etc.

---

**PRIYA (Summary):** Inheritance + method overriding for three ad types, custom exceptions for validation, and six exception-handling patterns that keep the system safe.

---
---

## 👤 PERSON 3 — RAHUL
### Files: `DBConnection.java`, `DBFeatures.java`, `AdvertisingSystem.java`, `advertising_db.sql`
### **Topic: Database Layer — PostgreSQL Integration & CRUD**

---

**RAHUL:** Hey! I'm Rahul. I'll explain how Java connects to PostgreSQL, executes queries, manages transactions, and uses advanced SQL features.

---

### 📄 File 1: `DBConnection.java` — The Database Gateway

```java
public static String URL = System.getenv().getOrDefault(
    "ADWISE_DB_URL", "jdbc:postgresql://localhost:5432/AdvertisingManagementSystem");
```

- Reads credentials from **environment variables** first — credentials NOT hardcoded (best practice).
- `getConnection()` uses **JDBC** (`DriverManager.getConnection()`).
- `setCredentials()` is `synchronized` — only one thread can update credentials at a time.
- `main()` is a standalone test: connect → INSERT (upsert with `ON CONFLICT DO NOTHING`) → SELECT with JOIN.

---

### 📄 File 2: `DBFeatures.java` — Five PostgreSQL Features

1. **SQL View** (`ad_dashboard`): A saved SELECT query that joins tables and computes `days_left`. Java queries it like a regular table.
2. **Transactions** (`setAutoCommit(false)` → `commit()` / `rollback()`): Payment is saved atomically or not at all.
3. **Trigger**: Updating `end_date` automatically fires a DB trigger that logs the change to `ad_update_log`.
4. **Stored Function** (`expire_old_ads()`): Called via `SELECT expire_old_ads()` — sets past-deadline ads to "Expired".
5. **Audit Log**: Reads `ad_update_log` to show full history of status changes.

---

### 📄 File 3: `AdvertisingSystem.java` — The Business Logic Layer

**`one(String sql)`** — runs any COUNT/SUM query and returns the single result as a String.

**`addAdvertisement()`:**
- Uses `instanceof` to detect `DigitalAdvertisement`.
- Calls `platformId()` to find or create the platform row.
- Uses `PreparedStatement` with 9 `?` parameters — prevents **SQL injection**.
- Sets `end_date = CURRENT_DATE + 30`.

**`addCampaign()` — Transaction:**
- Inserts campaign, then loops through `adIds` inserting into `campaign_ad`.
- Full `commit()` / `rollback()` wrapping.

**`deleteAd()` — Cascading Delete:**
```java
"DELETE FROM campaign_ad WHERE ad_id = ?"
"DELETE FROM payment WHERE ad_id = ?"
"DELETE FROM advertisement WHERE ad_id = ?"
```
Must delete child rows before parent due to **foreign key constraints** — all in one transaction.

---

### 📄 File 4: `advertising_db.sql` — The Database Schema

- **Tables**: `advertiser`, `platform`, `advertisement`, `payment`, `campaign`, `campaign_ad`, `ad_update_log`
- **Foreign Keys**: Link tables with referential integrity.
- **VIEW** `ad_dashboard`: Computed `days_left`, payment status.
- **FUNCTION** `expire_old_ads()`: Batch-expires old ads.
- **TRIGGER** `log_ad_status_update`: Auto-logs every status change.

---

**RAHUL (Summary):** The full data persistence layer — JDBC connection, transactions, PreparedStatements, views, triggers, stored functions — all backed by a well-structured SQL schema.

---
---

## 👤 PERSON 4 — SARA
### Files: `AdServer.java`, `WebAdRuntime.java`, `ThreadDemo.java`, `Main.java`, `index.html` / `styles.css` / `script.js`
### **Topic: Web Server, Multithreading, Runtime Cache & UI**

---

**SARA:** Hi! I'm Sara. I'll explain the **runtime layer** — the web server, threading, in-memory caching, and the browser UI.

---

### 📄 File 1: `Main.java` — The Console Interface

- `do-while` loop: menu always shows at least once.
- `Scanner` reads keyboard input. `sc.nextLine()` after `sc.nextInt()` flushes the leftover newline.
- Option 2: creates `DigitalAdvertisement` → `system.addAdvertisement()`.
- Option 3: creates `Campaign` → `campaign.addAd(id)` → `system.addCampaign()`.
- Option 18 exits; `sc.close()` releases the resource.

---

### 📄 File 2: `ThreadDemo.java` — Four Threading Patterns

**Part 1 — Extend `Thread`:** `AdDisplayThread` runs three ad displays concurrently. `t.start()` launches; `t.join()` makes main wait for completion. `Thread.sleep(500)` pauses 0.5s between shows.

**Part 2 — Implement `Runnable`:** `PaymentReminder` wraps logic in `run()`. Preferred over extending Thread because Java has no multiple inheritance — `Runnable` classes can still extend another class.

**Part 3 — `synchronized` Shared Counter:** `ViewCounter.addView()` is synchronized. Three threads each add 1000 views. Without `synchronized`, a **race condition** could corrupt the count. With it, the result is always exactly 3000.

**Part 4 — Daemon Thread:** `ExpiryChecker` runs forever checking for expired ads. `checker.setDaemon(true)` makes it auto-terminate when the main program ends — no manual cleanup needed.

---

### 📄 File 3: `WebAdRuntime.java` — In-Memory Cache & Background Scheduler

```java
private static final Map<Integer, Advertisement> CACHE =
    Collections.synchronizedMap(new HashMap<>());
private static final ArrayList<Advertisement> ORDERED_ADS = new ArrayList<>();
private static final Vector<String> EVENTS = new Vector<>();
```

| Structure | Purpose |
|-----------|---------|
| `synchronized HashMap` | O(1) ad lookup by ID — thread-safe |
| `ArrayList` | Insertion-ordered list for display |
| `Vector` | Thread-safe event log |

- `EXPIRY_THREAD` — `ScheduledExecutorService` runs `expireAds()` every **5 minutes** on a daemon thread.
- `validate()` — throws `InvalidAdvertisementException` for bad title, zero budget, or invalid duration.
- `createDomainAdvertisement()` — **Factory Pattern**: instantiates the right subclass (Digital/TV/Newspaper) based on the `type` string.
- `cacheRow(ResultSet rs)` — reads a DB row and places the Java object into both `CACHE` and `ORDERED_ADS`.
- `latestEvent()` — returns the last `Vector` element (most recent system event).
- Shutdown: `AdServer` registers a JVM shutdown hook that calls `WebAdRuntime.stop()` on Ctrl+C.

---

### 📄 File 4: `AdServer.java` — The HTTP Web Server (15+ Routes)

```java
HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
server.setExecutor(Executors.newFixedThreadPool(8));
```

- **No external framework** — uses Java's built-in `com.sun.net.httpserver`.
- **Thread pool of 8**: up to 8 concurrent requests handled simultaneously.
- **CORS headers** on every response so browser JavaScript can call the API.
- **`parseParams()`**: reads GET query string AND POST body, URL-decodes values.

| Route | Method | Purpose |
|-------|--------|---------|
| `/api/ads` | GET/POST | List all ads / create new ad |
| `/api/ads/update` | POST | Update ad fields |
| `/api/ads/delete` | POST | Delete ad from DB + cache |
| `/api/functions/expire` | POST | Trigger expire_old_ads() |
| `/api/logs` | GET | Trigger audit log |
| `/api/stats` | GET | Aggregate counts & budget sum |
| `/api/payments` | GET | List all payments |
| `/api/campaigns` | GET/POST | List or create campaigns |
| `/api/advertisers` | GET | List all advertisers |
| `/api/db/status` | GET | Check DB connection |
| `/api/db/connect` | POST | Update DB credentials live |
| `/api/runtime` | GET | Show HashMap/ArrayList/Vector stats |
| `/` | GET | Serve static files (HTML/CSS/JS) |

---

### 📄 Files 5-7: `index.html`, `styles.css`, `script.js` — Browser UI

- **`index.html`**: Tabbed interface — Ads, Advertisers, Campaigns, Payments, Reports, DB Settings panels.
- **`styles.css`**: CSS variables, dark/professional theme, flexbox/grid layout.
- **`script.js`**: `fetch()` + async/await calls to all API endpoints. Dynamically renders tables. Handles form submits. Displays HashMap count, ArrayList count, and latest Vector event from `/api/runtime`.

---

**SARA (Summary):** `Main.java` provides a console menu, `ThreadDemo.java` shows four threading patterns (extend Thread, Runnable, synchronized, daemon), `WebAdRuntime.java` maintains a thread-safe in-memory cache with a scheduled background expiry worker, and `AdServer.java` is a full REST HTTP server with 15+ routes, a thread pool, CORS, and static file serving for the browser UI.

---
---

## 🏁 GROUP SUMMARY TABLE

| Person | Files Covered | Key Concepts |
|--------|--------------|--------------|
| **Alex** | `Advertisement`, `Advertiser`, `Campaign`, `Payment` | Abstract class, Encapsulation, Static/Final, ArrayList |
| **Priya** | `Digital/TV/NewspaperAdvertisement`, `ErrorHandlingDemo`, `InvalidAdvertisementException` | Inheritance, Method Overriding, Polymorphism, Custom Exceptions, try-catch-finally |
| **Rahul** | `DBConnection`, `DBFeatures`, `AdvertisingSystem`, `advertising_db.sql` | JDBC, PreparedStatement, Transactions, Views, Triggers, Stored Functions |
| **Sara** | `AdServer`, `WebAdRuntime`, `ThreadDemo`, `Main`, HTML/CSS/JS | HTTP Server, Thread Pool, synchronized, Daemon Thread, HashMap Cache, REST API |

> **Total: 18 Java files + 1 SQL file + 3 web files = 22 files across 4 presenters**
