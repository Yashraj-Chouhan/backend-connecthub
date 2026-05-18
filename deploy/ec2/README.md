# Backend EC2 Deployment

Use this when you want:

- one Docker deployment for backend services only
- a separate Docker deployment for the frontend
- plain HTTP on EC2

The frontend deployment is documented separately in [frontend/deploy/ec2/README.md](</C:/Users/yashr/Desktop/Projects/ConnectHub/frontend/deploy/ec2/README.md>).

## Backend container layout

- gateway-service serves the backend API on port `8080`
- other backend services stay behind the gateway

This guide is for the backend half only.

## Required `.env` values on EC2

Set these in the backend `.env` file on the EC2 machine:

```env
CONNECTHUB_FRONTEND_ORIGIN=http://<ec2-public-ip>
CONNECTHUB_FRONTEND_ALT_ORIGIN=
GATEWAY_PUBLIC_PORT=8080
GOOGLE_AUTH_ALLOWED_CLIENT_IDS=your_google_web_client_id.apps.googleusercontent.com
```

Example:

```env
CONNECTHUB_FRONTEND_ORIGIN=http://16.170.18.188
CONNECTHUB_FRONTEND_ALT_ORIGIN=
GATEWAY_PUBLIC_PORT=8080
GOOGLE_AUTH_ALLOWED_CLIENT_IDS=524012515071-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com
```

## Start command

Run this from the `backend` folder on EC2:

```powershell
docker compose up --build -d
```

## URL

- backend gateway: `http://<ec2-public-ip>:8080`

## Google login note

If you use Google sign-in, add this to Google Cloud OAuth authorized JavaScript origins:

- `http://<ec2-public-ip>`

Without that, Google login will fail with `403 origin not allowed`.
