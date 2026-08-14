# =============================================================================
# Frontend production image
#
# Build-time args (optional):
#   VITE_API_BASE_URL  → injected into the SPA so it knows where the API lives.
#                       Defaults to `/api`, which the bundled nginx.conf proxies
#                       to the `backend` service in docker-compose.
#   VITE_FRONTEND_URL  → public URL of this app (used for OAuth redirects, etc.)
# =============================================================================

# Build stage
FROM node:20-alpine AS build
WORKDIR /app

ARG VITE_API_BASE_URL=/api
ARG VITE_FRONTEND_URL=http://localhost
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_FRONTEND_URL=${VITE_FRONTEND_URL}

COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]