# Multi-stage build: bff-player (puerto 3001)
FROM node:20-alpine AS builder
WORKDIR /build
COPY bff-player/package.json bff-player/package-lock.json* ./
RUN npm ci || npm install
COPY bff-player/tsconfig*.json bff-player/nest-cli.json ./
COPY bff-player/src ./src
RUN npm run build

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY bff-player/package.json bff-player/package-lock.json* ./
RUN npm ci --omit=dev || npm install --omit=dev
RUN npm cache clean --force
COPY --from=builder /build/dist ./dist
EXPOSE 3001
CMD ["node", "dist/main"]
