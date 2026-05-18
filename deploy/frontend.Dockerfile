FROM node:22-alpine AS builder

WORKDIR /app

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/index.html ./
COPY frontend/components.json ./
COPY frontend/postcss.config.js ./
COPY frontend/tailwind.config.js ./
COPY frontend/tsconfig.json ./
COPY frontend/tsconfig.app.json ./
COPY frontend/tsconfig.node.json ./
COPY frontend/vite.config.js ./
COPY frontend/vite.config.server.js ./
COPY frontend/client ./client
COPY frontend/public ./public
COPY frontend/server ./server
COPY frontend/shared ./shared
COPY frontend/src ./src

# Vite reads these at build time, so the image accepts them as Docker build args.
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_GOOGLE_CLIENT_ID=
ARG VITE_STUN_URLS=
ARG VITE_TURN_CREDENTIALS_URL=
ARG VITE_TURN_API_KEY=
ARG VITE_TURN_REGION=
ARG VITE_TURN_URLS=
ARG VITE_TURN_USERNAME=
ARG VITE_TURN_CREDENTIAL=

ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID
ENV VITE_STUN_URLS=$VITE_STUN_URLS
ENV VITE_TURN_CREDENTIALS_URL=$VITE_TURN_CREDENTIALS_URL
ENV VITE_TURN_API_KEY=$VITE_TURN_API_KEY
ENV VITE_TURN_REGION=$VITE_TURN_REGION
ENV VITE_TURN_URLS=$VITE_TURN_URLS
ENV VITE_TURN_USERNAME=$VITE_TURN_USERNAME
ENV VITE_TURN_CREDENTIAL=$VITE_TURN_CREDENTIAL

RUN npm run build

FROM node:22-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=5173

# Keep the full installed dependency tree because the bundled server imports
# runtime packages such as express and cors from node_modules.
COPY --from=builder /app/package*.json ./
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/dist ./dist

EXPOSE 5173

CMD ["node", "dist/server/node-build.mjs"]
