FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG NEXT_PUBLIC_SITE_URL
ENV NEXT_PUBLIC_SITE_URL=${NEXT_PUBLIC_SITE_URL}
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/out /usr/share/nginx/html
# A template, not a static file: nginx's entrypoint runs envsubst on it at container start
# (see gender-reveal.conf.template's header comment) so the API upstream can be overridden per
# platform without a rebuild. Defaults match docker-compose's `api` service/port.
COPY nginx/gender-reveal.conf.template /etc/nginx/templates/default.conf.template
ENV API_HOST=api
ENV API_PORT=8080
EXPOSE 80
# TEMP DEBUG: /docker-entrypoint.d/*.sh scripts run automatically (in numeric order) as part of
# nginx's normal startup — unlike overriding CMD, this doesn't skip the entrypoint's own
# envsubst step. Remove once the Railway upstream issue is diagnosed.
RUN printf '#!/bin/sh\necho "DEBUG API_HOST=[$API_HOST] API_PORT=[$API_PORT]"\n' > /docker-entrypoint.d/05-debug-env.sh && \
    chmod +x /docker-entrypoint.d/05-debug-env.sh
RUN printf '#!/bin/sh\necho "DEBUG generated default.conf:"; cat /etc/nginx/conf.d/default.conf\n' > /docker-entrypoint.d/25-debug-conf.sh && \
    chmod +x /docker-entrypoint.d/25-debug-conf.sh
