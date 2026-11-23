# syntax=docker/dockerfile:1
FROM node:26.13.1-trixie-slim@sha256:e6bedef217677b276c591795e169840edd9096dbf13f29df8ef8d1856b8820bc AS build

WORKDIR /app/frontend
RUN npm install --global npm@12.2.3
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY frontend/index.html frontend/vite.config.ts frontend/tsconfig*.json ./
COPY frontend/src/ ./src/
COPY brand/canavesio-logo.svg /app/brand/canavesio-logo.svg
COPY docs/contratos/web/ejemplos/history-coolant.json \
    docs/contratos/web/ejemplos/history-rpm-page-1.json \
    docs/contratos/web/ejemplos/history-rpm-page-2.json \
    docs/contratos/web/ejemplos/latest-invalid.json \
    docs/contratos/web/ejemplos/latest-normal.json \
    docs/contratos/web/ejemplos/machine-detail.json \
    /app/docs/contratos/web/ejemplos/
RUN npm run build

FROM nginx:1.30.7-alpine@sha256:caf4e1e032a20f9c3c5e28fc7f25ffd768ad8677b4975c54193075997b3a97e3

COPY infrastructure/platform/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/frontend/dist/ /usr/share/nginx/html/
