# Public HTTPS Setup For Vercel Frontend

Use this when your frontend is deployed on Vercel and your backend runs on a VM.

## Why this is needed

Your frontend is served over `https://...vercel.app`. Browsers block calls from that page to plain `http://...` backend URLs as mixed content. The backend must be reachable over HTTPS, and WebSocket traffic must use WSS.

## What this proxy does

This Caddy setup:

- terminates HTTPS on ports `80` and `443`
- obtains and renews TLS certificates automatically
- proxies both normal HTTP traffic and WebSocket traffic to `gateway-service:8080`

## Server prerequisites

1. Point a public DNS name to your backend server IP.

Example:

- `api.connecthub.example.com -> 16.170.18.188`

Quick no-domain test option:

- `api-16-170-18-188.nip.io`
- `16-170-18-188.sslip.io`

`nip.io` and `sslip.io` map hostnames containing your IP address back to that IP and are commonly used for quick HTTPS testing.

2. Open inbound ports `80` and `443` on the VM / cloud firewall.

3. In the backend `.env` on the server, set:

```env
CONNECTHUB_FRONTEND_ORIGIN=https://connect-hub-frontend-ot329n5oq.vercel.app
PUBLIC_API_DOMAIN=api.connecthub.example.com
```

For a quick test without buying a domain, you can use:

```env
CONNECTHUB_FRONTEND_ORIGIN=https://connect-hub-frontend-ot329n5oq.vercel.app
PUBLIC_API_DOMAIN=api-16-170-18-188.nip.io
```

## Deploy commands

Run from the backend folder on the server:

```powershell
docker compose up -d --build gateway-service websocket-service
docker compose -f docker-compose.yml -f deploy/caddy/docker-compose.public.yml up -d caddy
```

## Frontend configuration

Your frontend must call the HTTPS gateway URL, not Eureka and not the raw HTTP IP.

Use:

- API base URL: `https://api.connecthub.example.com`
- WebSocket URL: `wss://api.connecthub.example.com/ws`

If the frontend uses environment variables, update the existing API / WebSocket variables to these values.

For the quick test option above, use:

- API base URL: `https://api-16-170-18-188.nip.io`
- WebSocket URL: `wss://api-16-170-18-188.nip.io/ws`
