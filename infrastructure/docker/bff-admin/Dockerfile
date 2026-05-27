# Multi-stage build: bff-admin (puerto 3003)
FROM node:20-alpine AS builder
WORKDIR /build
COPY bff-admin/package.json bff-admin/package-lock.json* ./
RUN npm ci || npm install
COPY bff-admin/tsconfig*.json bff-admin/nest-cli.json ./
COPY bff-admin/src ./src
RUN npm run build

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY bff-admin/package.json bff-admin/package-lock.json* ./
RUN npm ci --omit=dev || npm install --omit=dev
RUN npm cache clean --force
COPY --from=builder /build/dist ./dist
EXPOSE 3003
CMD ["node", "dist/main"]
