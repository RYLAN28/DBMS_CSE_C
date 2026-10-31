# Adwise

## Run with pgAdmin and PostgreSQL

1. Open pgAdmin and create a database named `AdvertisingManagementSystem` under
   your PostgreSQL server.
2. Select that database, open **Tools > Query Tool**, open `advertising_db.sql`,
   and execute the complete file. It creates the tables, sample data, live
   dashboard view, trigger, and expiry function used by the application.
3. In PowerShell, from this directory, set the password for the PostgreSQL
   `postgres` user:

   ```powershell
   $env:ADWISE_DB_PASSWORD = "your-postgres-password"
   ```

   If your setup uses another user or port, also set `ADWISE_DB_USER` and
   `ADWISE_DB_URL`, for example:

   ```powershell
   $env:ADWISE_DB_USER = "postgres"
   $env:ADWISE_DB_URL = "jdbc:postgresql://localhost:5432/AdvertisingManagementSystem"
   ```

4. Run `.\start.ps1`, then open http://localhost:8080. The **Ad Status** table
   should show `Live from PostgreSQL`; new advertisements and campaigns are
   saved into the selected database.

Requires a JDK (Java 11 or newer) and PostgreSQL for live data.

## Database connection

Set `ADWISE_DB_PASSWORD` in the terminal before starting the server. Optionally set
`ADWISE_DB_USER` (default: `postgres`) and `ADWISE_DB_URL` (default:
`jdbc:postgresql://localhost:5432/AdvertisingManagementSystem`). Restart the server
after changing these settings. Do not commit credentials.

The existing SQL file recreates tables and deletes their previous contents; only
use it to initialize a disposable database or after backing up existing data.

## Current data behavior

- The advertisement table reads `/api/ads`. Connection failures display clearly
  labeled sample data. Advertisement creation requires valid advertiser/platform
  IDs from PostgreSQL.
- The **Advertiser** navigation opens a PostgreSQL-backed form. Submitting it
  calls `/api/advertisers`, inserts the advertiser, and refreshes the advertiser
  selector used by the advertisement form.
- Campaign creation saves the name through `/api/campaigns` and only adds a row
  after a successful response. The existing schema stores campaign names only:
  channel, budget, row status changes and row removal are session-only UI features.
- The **Campaigns (Projects)** navigation opens an infinite, hover-pausable project
  rail. Selecting a project loads its connected advertisements through
  `/api/campaigns/details`; **Create Campaign** writes the campaign and selected
  advertisement links to PostgreSQL through `/api/campaigns`.
- Summary cards, campaign examples, platform mix, and the performance chart are
  demonstration data, not live analytics.
- `index.html` loads `script.js`. The older `script .js` file is not loaded.

## Java concepts used by the web application

The web server now uses the Java concepts demonstrated by the project while
keeping PostgreSQL as the source of truth:

- `DigitalAdvertisement`, `TVAdvertisement`, and `NewspaperAdvertisement`
  inherit from the new abstract `Advertisement` class. SQL advertisement rows
  are converted into the matching subtype when `/api/ads` is read.
- `WebAdRuntime` uses a synchronized `HashMap` as a short-lived domain cache.
  Inserts, reads, updates, and deletes keep the cache aligned with SQL.
- The runtime also keeps an ordered `ArrayList<Advertisement>` for web-loaded
  objects and a thread-safe `Vector<String>` event history.
- The current runtime state can be inspected at `/api/runtime`.
- `InvalidAdvertisementException` validates title, budget, and duration before
  an insert or update is sent to PostgreSQL.
- A daemon scheduled thread periodically calls the PostgreSQL
  `expire_old_ads()` function. It stops cleanly when the server shuts down.

The separate console demos remain runnable, but the concepts above are now
part of the browser-to-Java-to-PostgreSQL path.

## Verification

Java sources compile with `javac -encoding UTF-8 *.java`; JavaScript syntax can be
checked with `node --check script.js`.
