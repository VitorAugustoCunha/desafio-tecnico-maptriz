# syntax=docker/dockerfile:1

# ---------- build ----------
FROM node:22-alpine AS build

WORKDIR /build

# `npm ci` a partir do lockfile: instalacao identica em qualquer maquina.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .

RUN npm run build

# ---------- runtime ----------
FROM nginx:1.27-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html

# Nginx sem root: o processo mestre normalmente sobe como root para abrir a
# porta 80. Aqui ele roda como o usuario `nginx` e escuta na 8080 (porta alta,
# que nao exige privilegio). Os diretorios de cache e o pid precisam pertencer
# ao usuario, senao o Nginx nao inicia.
RUN touch /var/run/nginx.pid \
    && chown -R nginx:nginx /var/run/nginx.pid /var/cache/nginx /usr/share/nginx/html

USER nginx

EXPOSE 8080

# `127.0.0.1` e nao `localhost`: dentro do container, `localhost` resolve para
# ::1 (IPv6) primeiro, e o Nginx faz bind so em IPv4 — o healthcheck levava
# "connection refused" enquanto a aplicacao servia normalmente.
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
