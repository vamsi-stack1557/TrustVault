# TrustVault — Requirements and All Commands

This guide puts the requirements and the commands for extracting, running, testing, and (optionally) starting TrustVault with Docker in one place.

## 1. Requirements

### Option A — Run locally (recommended for first try)

- Windows 10/11, macOS, or Linux.
- **JDK 21** (a full JDK, not only a JRE).
- **Apache Maven 3.9+**.
- Internet connection for the first Maven build so dependencies can download.
- A modern browser (Chrome, Edge, Firefox, etc.).
- No separate database installation is required for the local run; the app uses an H2 file database.

Check the installations:

```powershell
java -version
mvn -version
```

Both commands should work. Java should report version 21 and Maven should report 3.9 or later.

### Option B — Run with Docker Compose

- Docker Desktop (Windows/macOS) or Docker Engine (Linux), with Docker Compose v2.
- Internet connection so Docker can pull the PostgreSQL image and build dependencies.

Check Docker:

```powershell
docker --version
docker compose version
```

You do not need Maven installed on the host for the Docker route, but Docker must be running.

---

## 2. Download and extract the project (Windows PowerShell)

Download `TrustVault-Java-Source.zip` from the conversation and save it in your Downloads folder. Then run:

```powershell
Set-Location "$HOME\Downloads"
Expand-Archive -Path ".\TrustVault-Java-Source.zip" -DestinationPath "." -Force
Set-Location ".\trustvault-java"
Get-ChildItem
```

If you saved the ZIP somewhere else, change the folder path in the first command. The folder you enter must contain `pom.xml`.

## 3. Run locally (H2 database, easiest setup)

Run these commands from the `trustvault-java` project folder:

```powershell
mvn clean test
mvn spring-boot:run
```

- The first command downloads dependencies, compiles the project, and runs automated tests.
- The second command starts the web application.
- Keep that terminal open while using the application.
- Open this address in your browser: **http://localhost:8080**.
- Press **Ctrl+C** in the terminal to stop the application.

### Local demo accounts

| Role | Email | Password |
|---|---|---|
| Administrator | admin@trustvault.local | Admin@12345 |
| Document owner | owner@trustvault.local | Owner@12345 |
| Viewer | viewer@trustvault.local | Viewer@12345 |

The local profile seeds a sample document. The owner account owns it and the viewer account has READ permission. Try revoking the viewer's grant as the owner, then sign in as the viewer and try opening/downloading the sample document again.

**Demo-only warning:** These credentials and local development keys are not for confidential or production data. Do not publish a live server with these defaults.

---

## 4. Useful Maven commands

Run from the project folder:

```powershell
# Run all automated tests
mvn test

# Clean old build files, compile, and run tests
mvn clean test

# Build the executable Spring Boot JAR
mvn clean package

# Start the application (local H2 profile)
mvn spring-boot:run

# Run the already-built JAR after "mvn clean package"
java -jar .\target\trustvault-1.0.0.jar
```

To use another local port, stop the application and run:

```powershell
$env:PORT = "8081"
mvn spring-boot:run
```

Then open **http://localhost:8081**.

---

## 5. Run with Docker Compose (PostgreSQL)

Use this option when you want to try the PostgreSQL deployment configuration. Run from the project folder.

### 5.1 Create the environment file

```powershell
Copy-Item .env.example .env
notepad .env
```

In `.env`, replace every placeholder. Generate two independent cryptographic keys in PowerShell with:

```powershell
# Generate a Base64 AES-256 file-encryption key (32 random bytes)
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))

# Generate a separate 32-byte HMAC audit key in hexadecimal
-join ([Security.Cryptography.RandomNumberGenerator]::GetBytes(32) | ForEach-Object { $_.ToString('x2') })
```

Paste the first output into `TRUSTVAULT_FILE_ENCRYPTION_KEY` and the second into `TRUSTVAULT_AUDIT_HMAC_KEY`.

Set unique, strong values for all of these `.env` entries:

- `DB_ADMIN_PASSWORD`
- `DB_PASSWORD`
- `DB_MIGRATION_PASSWORD`
- `TRUSTVAULT_AUDIT_HMAC_KEY`
- `TRUSTVAULT_FILE_ENCRYPTION_KEY`
- `TRUSTVAULT_BOOTSTRAP_ADMIN_EMAIL`
- `TRUSTVAULT_BOOTSTRAP_ADMIN_PASSWORD` (at least 16 characters)
- `TRUSTVAULT_COOKIE_SECURE` (`false` only for local HTTP testing; use `true` behind HTTPS)

Keep `.env` private. Do not commit it to Git or share it publicly.

### 5.2 Validate and start containers

```powershell
# Validate the Compose configuration and environment substitutions
docker compose config

# Build the app image and start PostgreSQL + TrustVault in the background
docker compose up --build -d

# Check container status
docker compose ps

# Follow application logs (press Ctrl+C to stop following logs)
docker compose logs -f app
```

Open **http://localhost:8080**. For this Docker configuration, the first start creates only the bootstrap ADMIN account specified in `.env`; it does not create the local demo OWNER and VIEWER accounts. Sign in as the bootstrap administrator and create OWNER and VIEWER accounts in the administration screen.

### 5.3 Stop or restart Docker

```powershell
# Stop containers; keep named database and file volumes
docker compose down

# Start again
docker compose up -d

# Follow logs
docker compose logs -f
```

**Destructive reset — use carefully:** this removes the containers and named volumes, including the PostgreSQL database and stored document files:

```powershell
docker compose down -v
```

---

## 6. Quick end-to-end test checklist

1. Sign in as the local OWNER (`owner@trustvault.local`).
2. Upload a small PDF, PNG, JPG/JPEG, or UTF-8 TXT file (maximum 10 MiB).
3. Share that document with the local VIEWER (`viewer@trustvault.local`) using READ permission.
4. Sign out and sign in as the VIEWER; confirm the shared document can be viewed/downloaded.
5. Sign back in as the OWNER and revoke the VIEWER's permission.
6. Sign in as the VIEWER again; confirm the document is no longer accessible.
7. Sign in as ADMIN and inspect document activity and audit-chain verification.
8. Run the automated tests with `mvn clean test`.

---

## 7. Troubleshooting commands

```powershell
# Confirm Java and Maven are on PATH
java -version
mvn -version

# Confirm Docker and Compose are available
docker --version
docker compose version

# Check whether port 8080 is already in use (Windows)
netstat -ano | findstr :8080

# Start locally on a different port
$env:PORT = "8081"
mvn spring-boot:run

# Inspect Docker service status and logs
docker compose ps
docker compose logs --tail=200 app
docker compose logs --tail=200 db
```

If Maven cannot resolve dependencies, check your internet connection and try `mvn clean test` again. If Docker commands cannot connect to the daemon, start Docker Desktop/Engine first.

---

## 8. Important before challenge submission

- Replace all demonstration credentials and keys.
- Deploy behind HTTPS and set `TRUSTVAULT_COOKIE_SECURE=true`.
- Keep the database, encrypted file volume, and HMAC key protected; preserve backups.
- Run `mvn clean test` and test the deployed system yourself.
- Create a real public Git repository with meaningful commits across the work period; do not fabricate history.
- Finish `PROMPTS.md` with actual prompts, mistakes, fixes, and real commit links.
- Record the required five-minute demo video and add the real deployment URL and credentials to the README.
- Read `THREATS.md`, `API.md`, and `WRITEUP.md`; document any remaining gaps honestly.

This source package is not deployed or independently penetration-tested. Use only demo data until you have completed your own security review.
