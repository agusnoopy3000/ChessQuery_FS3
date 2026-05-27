# Multi-stage build: bff-organizer (puerto 3002)
FROM node:20-alpine AS builder
WORKDIR /build
COPY bff-organizer/package.json bff-organizer/package-lock.json* ./
RUN npm ci || npm install
COPY bff-organizer/tsconfig*.json bff-organizer/nest-cli.json ./
COPY bff-organizer/src ./src
RUN npm run build

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY bff-organizer/package.json bff-organizer/package-lock.json* ./
RUN npm ci --omit=dev || npm install --omit=dev
RUN npm cache clean --force
COPY --from=builder /build/dist ./dist
EXPOSE 3002
CMD ["node", "dist/main"]
