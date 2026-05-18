# Optional Single-Origin Proxy Deployment

For the default EC2 setup with separate frontend and backend containers over
plain HTTP, use [deploy/ec2/README.md](/C:/Users/yashr/Desktop/Projects/ConnectHub/backend/deploy/ec2/README.md).

This folder is only for the alternate setup where you want one extra reverse
proxy container in front of the frontend and gateway. In this mode the
override file temporarily adds a frontend service back into the combined stack
just for that proxy deployment.

Use this setup when the frontend and backend both run on the same EC2 machine
and you want one public origin instead of separate frontend and backend ports.

## What this proxy does

This Caddy setup:

- listens on port `80`
- serves the frontend app at `http://<ec2-public-ip>/`
- forwards backend API and WebSocket paths to `gateway-service:8080`
- keeps browser traffic on one origin so login and API calls do not depend on CORS

## Why this fixes the deployment issue

Your earlier deployment mixed:

- frontend on `http://<ec2-ip>`
- API calls to another host such as `http://api-...`

That created browser CORS and DNS issues. This proxy removes that split. The
browser now loads the UI and calls the API from the same host.

## Files used

- `deploy/caddy/Caddyfile`
- `deploy/caddy/docker-compose.public.yml`

The override also rebuilds the frontend with an empty `VITE_API_BASE_URL` so
the production app automatically calls the same origin that served it.

## EC2 prerequisites

1. Open inbound port `80` in the EC2 security group.
2. Make sure no other service on the EC2 instance is already using port `80`.
3. Run Docker from the `backend` folder on the EC2 machine.

## Deploy command

Run this from the `backend` folder:

```powershell
docker compose -f docker-compose.yml -f deploy/caddy/docker-compose.public.yml up --build -d
```

## How to access the app

Open:

- `http://<ec2-public-ip>`

Do not use:

- `http://<ec2-public-ip>:5173` for normal browser usage
- `http://<ec2-public-ip>:8080` directly from the frontend
- `https://...` URLs unless you separately add TLS

## Environment notes

For this HTTP-only EC2 path:

- `VITE_API_BASE_URL` should be left empty in the deploy override
- `CONNECTHUB_FRONTEND_ORIGIN` can stay set, but the backend now also accepts
  broader HTTP origins to avoid raw-IP deployment failures

## Health checks

After startup:

- frontend should open at `http://<ec2-public-ip>`
- API health should work at `http://<ec2-public-ip>/actuator/health`
- login should call `POST /auth/login` on the same host
