FROM node:20-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime

RUN apk add --no-cache curl openssl

COPY nginx/entrypoint/40-generate-default-conf.sh /opt/buy01-nginx/40-generate-default-conf.sh
RUN sed -i 's/\r$//' /opt/buy01-nginx/40-generate-default-conf.sh
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80 443

CMD ["/bin/sh", "-c", "sh /opt/buy01-nginx/40-generate-default-conf.sh && exec nginx -g 'daemon off;'"]
