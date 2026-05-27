# Multi-stage build: frontend (chess-portal, sirvió en :80)
FROM node:20-alpine AS builder
WORKDIR /build
# Copia el workspace completo (monorepo packages compartidos)
COPY frontend/package.json frontend/package-lock.json* ./
COPY frontend/packages ./packages
COPY frontend/apps/chess-portal ./apps/chess-portal
WORKDIR /build/apps/chess-portal
RUN npm install --no-audit --no-fund
RUN npm run build

FROM nginx:alpine
COPY infrastructure/docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/apps/chess-portal/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
