## ConnectHub Backend - Docker Setup

Detailed system and deployment documentation:

- `docs/PRODUCTION_DOCUMENTATION.md`

### Start the local stack
Run from `backend` folder:

```powershell
docker compose up --build -d
```

For translation, the backend now tries Gemini first so it can translate and fix obvious typos in the same request. If Gemini is unavailable or returns an unusable response, it automatically falls back to LibreTranslate and then to the existing offline dictionary fallback.

Use [`.env.example`](</C:/Users/yashr/Desktop/Projects/ConnectHub/backend/.env.example>) as the template and create a `.env` file in the `backend` folder with:

```text
GEMINI_API_KEY=your_gemini_api_key
GEMINI_API_MODEL=gemini-2.5-flash
GEMINI_API_URL=https://generativelanguage.googleapis.com/v1beta/models
TRANSLATION_API_URL=https://libretranslate.de/translate
TRANSLATION_API_KEY=
SPEECH_API_KEY=your_groq_api_key
SPEECH_PROVIDER=groq-speech-to-text
SPEECH_API_URL=https://api.groq.com/openai/v1/audio/transcriptions
SPEECH_API_MODEL=whisper-large-v3-turbo
```

Only `GEMINI_API_KEY` and `SPEECH_API_KEY` are required for the default setup. LibreTranslate keeps a public default URL, so you only need `TRANSLATION_API_KEY` when you are using a protected LibreTranslate instance.

This now starts the backend services plus the frontend container on `http://localhost:5173`. The frontend image is built from the sibling `../frontend` project and uses `VITE_*` values from the backend-side `.env` or `.env.example`.

The default Docker build points the browser app to `http://localhost:8080` for local development. For the HTTP-only EC2 deployment path in [deploy/caddy/README.md](/C:/Users/yashr/Desktop/Projects/ConnectHub/backend/deploy/caddy/README.md), use the Caddy override so the browser app and backend API share the same origin on port `80`. That deploy flow intentionally clears `VITE_API_BASE_URL` during the frontend build so the production app calls the EC2 host it was loaded from instead of a separate API domain or raw `:8080` URL. Set the optional `VITE_TURN_*` variables if you want the same TURN/STUN config that exists in the standalone frontend project.

### HTTP EC2 deployment

Run this from the `backend` folder on the EC2 machine:

```powershell
docker compose -f docker-compose.yml -f deploy/caddy/docker-compose.public.yml up --build -d
```

Then open:

```text
http://<ec2-public-ip>
```

SonarQube is isolated behind an optional profile so normal startup behavior does not change.

### Stop all services

```powershell
docker compose down
```

### Stop and remove volumes (resets MySQL/Redis/upload data)

```powershell
docker compose down -v
```

### Start SonarQube locally

Run this only when you want the quality tooling:

```powershell
docker compose --profile quality up -d sonarqube sonarqube-db
```

Open `http://localhost:9030` after the containers are healthy. SonarQube will ask you to change the default `admin` password on first login.

### Run SonarQube for all services

From the `backend` folder you can now use the repo helper script:

```powershell
.\run_sonarqube.ps1
```

The script:
- starts the SonarQube containers
- waits for SonarQube to report `UP`
- can publish either one combined backend project or one SonarQube project per microservice

Token resolution order:
- `-Token <token>`
- `SONAR_TOKEN` or `SONAR_LOGIN` environment variable
- default local `admin` / `admin` login on a fresh SonarQube instance

Useful options:

```powershell
.\run_sonarqube.ps1 -StartOnly
.\run_sonarqube.ps1 -NoClean
.\run_sonarqube.ps1 -Token <token>
.\run_sonarqube.ps1 -Mode aggregate
.\run_sonarqube.ps1 -Mode services
.\run_sonarqube.ps1 -Mode both
```

Mode behavior:
- `aggregate`: one combined `ConnectHub-Backend` project
- `services`: one SonarQube project per microservice so each service appears on the `Projects` page
- `both`: publishes the combined project and the per-service projects

### Run analysis

From the `backend` folder:

```powershell
mvn --% -f pom.xml clean verify sonar:sonar -Dsonar.host.url=http://localhost:9030 -Dsonar.login=<token>
```

The root Maven build now collects JaCoCo XML reports from every service, so the Sonar analysis runs across the full backend without changing service runtime logic.

### Core exposed ports
- `5173` frontend
- `8080` gateway-service
- `9000` eureka-server
- `9002` auth-service
- `9003` room-service
- `9004` message-service
- `9007` notification-service
- `9012` presence-service
- `9013` translation-service
- `9014` websocket-service
- `9015` payment-service
- `9030` sonarqube (`quality` profile only)
- `3306` mysql
- `6379` redis
- `9092` kafka (host)

### Notes
- `payment-service` is wired to MySQL container with default root password `root` in `docker-compose.yml`.
- Services register to Eureka through `http://eureka-server:9000/eureka/`.
- Kafka-based services use internal broker address `kafka:29092` inside the Docker network.
