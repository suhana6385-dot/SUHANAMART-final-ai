# SUHANAMART - Setup Guide

## Prerequisites

- Java 17 (JDK)
- Maven 3.6+
- Apache Tomcat 9
- MySQL 8+

## 1. Database

```bash
mysql -u your_mysql_username -p < database/suhanamart.sql
```

This creates the `suhanamart` database, all 6 tables, one seeded **admin account** (`admin@suhanamart.com` / `ChangeMe_Admin#2026` - see `docs/project-overview.md`'s Admin module), and a few optional sample products. See `database/README.md` for details.

**Already have a database from before these modules?** Run the migrations you are missing, in order (each is safe to re-run):

```bash
mysql -u your_mysql_username -p < database/migrations/V2__order_status.sql              # Seller module + order status
mysql -u your_mysql_username -p < database/migrations/V3__reviews.sql                   # Review module
mysql -u your_mysql_username -p < database/migrations/V4__admin_and_account_status.sql  # Admin module
```

Login reads the `users.role` column from V2, so nobody can log in until it exists; product pages read the `reviews` table from V3; the Admin module needs `users.account_status` and its seeded account from V4.

## 2. Database credentials

No password is ever hardcoded in this project. `DBConnection.java` (`backend/src/main/java/com/suhanamart/util/DBConnection.java`) resolves credentials in this order:

1. Environment variables `SUHANAMART_DB_USER` / `SUHANAMART_DB_PASSWORD` (must be set for the process running Tomcat - not just your terminal)
2. JVM system properties `-Ddb.user=... -Ddb.password=...`
3. A local `db.properties` file on the classpath

The easiest option for local development is #3:

```bash
cp backend/src/main/resources/db.properties.example backend/src/main/resources/db.properties
```

Then edit `db.properties` and fill in your real MySQL username/password. **Never commit the real `db.properties` file** - it's already listed in `.gitignore`.

Optional - the **AI chatbot** (`/chat`) follows the exact same three-tier pattern (`SUHANAMART_AI_API_URL` / `SUHANAMART_AI_API_KEY` / `SUHANAMART_AI_MODEL` environment variables, then `-Dai.apiUrl=...` etc., then `ai.properties` copied from `ai.properties.example`). Leaving it unconfigured is fine - the chatbot still answers from the SUHANAMART database directly, without an AI model.

## 3. Build

```bash
mvn clean package
```

This produces `target/suhanamart.war`. (The project's Java sources live under `backend/src/main/java` and the web root under `frontend/` rather than Maven's defaults - this is configured in `pom.xml`, so a plain `mvn clean package` is all you need.)

## 4. Deploy to Tomcat

Copy the WAR into Tomcat's `webapps/` folder:

```bash
cp target/suhanamart.war <TOMCAT_HOME>/webapps/
```

Start (or restart) Tomcat:

```bash
<TOMCAT_HOME>/bin/startup.sh      # Linux/Mac
<TOMCAT_HOME>/bin/startup.bat     # Windows
```

## 5. Test in a browser

```
http://localhost:8080/suhanamart/
```

This redirects to the login page. Register a new account, then log in - you'll land on the dashboard.

## Troubleshooting

If registration/login fails with "Something went wrong. Please try again later.", check Tomcat's console/log (`<TOMCAT_HOME>/logs/catalina.out`) for a line starting with `[SUHANAMART] Registration failed due to a database error: ...` - it names the specific cause (missing credentials, wrong DB name, missing table, etc.) without ever showing that detail to the browser.
