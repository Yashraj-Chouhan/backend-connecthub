## ConnectHub Backend - Docker Setup

Detailed system and deployment documentation:

- `docs/PRODUCTION_DOCUMENTATION.md`

### Start the backend stack
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

This starts only the backend services. The frontend is deployed separately from the sibling `../frontend` project.

For an EC2 deployment with separate frontend and backend containers over plain HTTP, use the backend settings documented in [deploy/ec2/README.md](/C:/Users/yashr/Desktop/Projects/ConnectHub/backend/deploy/ec2/README.md) and the frontend settings documented in [frontend/deploy/ec2/README.md](</C:/Users/yashr/Desktop/Projects/ConnectHub/frontend/deploy/ec2/README.md>).

### HTTP EC2 deployment

Set these values in the backend `.env` on EC2:

```text
CONNECTHUB_FRONTEND_ORIGIN=http://<ec2-public-ip>
GATEWAY_PUBLIC_PORT=8080
```

Then run:

```powershell
docker compose up --build -d
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
