# Buy-01 Marketplace

This repository contains the marketplace frontend plus the Spring Boot microservices backend:

- `frontend`: Angular web app served through Nginx in Docker
- `discovery-service`: Eureka service registry
- `gateway-service`: API gateway with CORS, JWT validation on protected routes, auth header propagation, and in-memory rate limiting for auth/media writes
- `user-service`: registration, login, JWT issuance, profile management, seller avatar upload delegation, and optional admin bootstrap
- `product-service`: public product reads plus seller/admin CRUD with ownership enforcement and media ownership synchronization
- `media-service`: seller/admin image upload/list/delete, public image download, MIME sniffing, 2 MB limit, MinIO-backed object storage, and product ownership validation

## One-Command Docker Run

From the repository root:

```bash
docker compose up --build
```

That starts the full stack:

- Angular frontend on `https://localhost`
- API gateway on `http://localhost:8080`
- Eureka dashboard on `http://localhost:8761`
- MongoDB on `localhost:27017`
- MinIO API on `http://localhost:9000`
- MinIO console on `http://localhost:9001`

To stop it:

```bash
docker compose down
```

To stop it and also remove the named MongoDB and MinIO volumes:

```bash
docker compose down -v
```

Notes:

- the first build can take a while because Docker has to build the Angular app and package each Spring Boot service
- the frontend proxies `/api/*` to the gateway, so the browser only needs `https://localhost`
- the first browser visit will warn because the local certificate is self-signed
- media URLs resolve through the frontend proxy as `https://localhost/api/media/images/...`
- the backend service ports remain exposed over HTTP for direct debugging on `8080` to `8083`

## Docker Hub Fallback

If Docker Desktop starts failing to resolve `node:20-alpine` with an `auth.docker.io` `522` error, use the Windows PowerShell fallback instead:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-stack.ps1
```

That fallback:

- builds the Angular frontend with the locally installed `node` and `npm`
- starts the backend services with Docker Compose
- serves the built frontend assets from `nginx:1.27-alpine` through `docker-compose.frontend-static.yml`

If `frontend/node_modules` is missing, add `-InstallFrontendDependencies` once:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-stack.ps1 -InstallFrontendDependencies
```

## HTTPS

Local HTTPS is now the default Docker Compose path. If you want a small PowerShell wrapper instead of typing the compose command yourself, use:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-docker-https.ps1
```

That script now starts the same root `docker-compose.yml` flow for `https://localhost`.

For a public deployment, the same script can still use Let's Encrypt when you pass real domains and an email address:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-docker-https.ps1 `
  -HttpsDomains shop.example.com,www.shop.example.com `
  -LetsEncryptEmail ops@example.com
```

That public-domain path adds `docker-compose.https.yml` on top of the main compose so ACME validation can use port `80`.

Renew certificates for the Docker-built frontend path with:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\renew-https-cert.ps1 -UseDockerFrontend
```

## Tech Stack

- Java 17
- Spring Boot 3.3.5
- Spring Cloud 2023.0.3
- Spring Security
- MongoDB
- Eureka Discovery
- Spring Cloud Gateway

## Implemented Backend Features

- `POST /auth/register`, `POST /auth/login`
- `GET /me`, `PUT /me` (JSON and multipart form-data variants)
- `GET /products`, `GET /products/{id}`
- `GET /products/me`, `POST /products`, `PUT /products/{id}`, `DELETE /products/{id}`
- `GET /media/images/{id}`
- `GET /media/images`, `POST /media/images`, `DELETE /media/images/{id}`
- JWT validation in the gateway, product service, and media service
- Seller ownership checks for product and media mutations, including cross-service image ownership validation
- Seller avatar upload through `user-service`, delegated to `media-service`
- Global exception handlers with `400/401/403/404/415`
- `/actuator/health` and `/actuator/info` exposure on all services
- Gateway rate limiting for auth and media write endpoints
- Optional Kafka event publication for product and image lifecycle events
- Optional HTTPS startup path with a generated local development certificate

## Security Notes

- Passwords are hashed with BCrypt in `user-service`
- JWTs include `userId` and `role` claims
- Public routes are limited to auth, product reads, and image reads
- Media uploads accept `image/*` only, enforce `<= 2 MB`, and sniff file signatures before storage
- Files are stored outside MongoDB in an S3-compatible object store (MinIO by default for local development)
- `ADMIN` users can be bootstrapped through environment variables but cannot self-register through the public API

## Local Run

If you do not want Docker for the full stack, the backend can still be started manually as before.

1. Start MongoDB and MinIO:

```bash
cd backend
docker compose up -d
```

Fastest option on Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\start-backend.ps1 -Clean -StopExisting
```

That script:

- starts MongoDB and MinIO
- runs `mvn clean` once when `-Clean` is provided
- can stop existing backend listeners first when `-StopExisting` is provided
- opens separate PowerShell windows for discovery, gateway, user, product, and media services
- supports `-SkipObjectStorage` if you already have MinIO running
- supports `-UseHttps` to launch the full stack over HTTPS with a generated local certificate

If MongoDB and MinIO are already running, use:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\start-backend.ps1 -Clean -StopExisting -SkipMongo -SkipObjectStorage
```

If you only want to stop the backend services that are currently listening on the standard ports:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\stop-backend.ps1
```

2. Start the discovery service:

```bash
cd backend
mvn -pl discovery-service spring-boot:run
```

3. Start the remaining services in separate terminals:

```bash
cd backend
mvn -pl gateway-service spring-boot:run
```

```bash
cd backend
mvn -pl user-service spring-boot:run
```

```bash
cd backend
mvn -pl product-service spring-boot:run
```

```bash
cd backend
mvn -pl media-service spring-boot:run
```

## Environment Variables

Common:

- `EUREKA_DEFAULT_ZONE` default: `http://localhost:8761/eureka`
- `JWT_SECRET` default: `changeit-changeit-changeit-changeit-changeit-1234567890`
  If you want to supply a Base64-encoded secret, prefix it as `base64:<value>`.

Mongo:

- `USER_SERVICE_MONGODB_URI` default: `mongodb://localhost:27017/user_service_db`
- `PRODUCT_SERVICE_MONGODB_URI` default: `mongodb://localhost:27017/product_service_db`
- `MEDIA_SERVICE_MONGODB_URI` default: `mongodb://localhost:27017/media_service_db`

Gateway:

- `ALLOWED_ORIGINS` default: `http://localhost:4200`
- `GATEWAY_RATE_LIMIT_ENABLED` default: `true`
- `GATEWAY_RATE_LIMIT_AUTH_MAX_REQUESTS` default: `30`
- `GATEWAY_RATE_LIMIT_MEDIA_WRITE_MAX_REQUESTS` default: `60`

Internal service URLs:

- `MEDIA_SERVICE_INTERNAL_BASE_URL` default: `http://localhost:8083`
- `PRODUCT_SERVICE_INTERNAL_BASE_URL` default: `http://localhost:8082`

Media:

- `MEDIA_PUBLIC_BASE_URL` default: `http://localhost:8080/media/images`
- `MEDIA_STORAGE_TYPE` default: `s3`
- `MEDIA_STORAGE_ROOT` default: `./storage/media` when `MEDIA_STORAGE_TYPE=filesystem`
- `MEDIA_STORAGE_S3_ENDPOINT` default: `http://localhost:9000`
- `MEDIA_STORAGE_S3_REGION` default: `us-east-1`
- `MEDIA_STORAGE_S3_ACCESS_KEY` default: `minioadmin`
- `MEDIA_STORAGE_S3_SECRET_KEY` default: `minioadmin`
- `MEDIA_STORAGE_S3_BUCKET` default: `buy01-media`

Admin bootstrap:

- `ADMIN_EMAIL`
- `ADMIN_PASSWORD`
- `ADMIN_FULL_NAME` default: `Marketplace Admin`

Kafka:

- `KAFKA_ENABLED` default: `false`
- `KAFKA_BOOTSTRAP_SERVERS` default: `localhost:9092`
- `KAFKA_PRODUCTS_TOPIC` default: `product-events`
- `KAFKA_MEDIA_TOPIC` default: `media-events`

HTTPS:

- `SERVER_SSL_ENABLED` default: `false`
- `SERVER_SSL_KEY_STORE`
- `SERVER_SSL_KEY_STORE_PASSWORD`
- `SERVER_SSL_KEY_STORE_TYPE` default: `PKCS12`
- `SERVER_SSL_KEY_ALIAS` default: `buy01-dev`

Frontend TLS termination:

- `FRONTEND_HTTP_PORT` default: `80` when `docker-compose.https.yml` is used for ACME validation
- `FRONTEND_HTTPS_PORT` default: `443` in the main compose
- `ENABLE_TLS` default: `true` in the main compose
- `ENABLE_LOCAL_TLS` default: `true` in the main compose
- `SERVER_NAME` default: `localhost` for local Docker runs, example public value: `shop.example.com www.shop.example.com`
- `TLS_CERT_PATH` default: `/etc/letsencrypt/live/<first-server-name>/fullchain.pem`
- `TLS_KEY_PATH` default: `/etc/letsencrypt/live/<first-server-name>/privkey.pem`
- `HSTS_MAX_AGE` default: `31536000`

## Quick API Examples

Register a seller:

```bash
curl -X POST http://localhost:8080/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"seller@example.com\",\"password\":\"Password123\",\"fullName\":\"Seller\",\"role\":\"SELLER\"}"
```

Create a product:

```bash
curl -X POST http://localhost:8080/products ^
  -H "Authorization: Bearer <token>" ^
  -H "Content-Type: application/json" ^
  -d "{\"name\":\"Phone\",\"description\":\"Flagship phone\",\"price\":699.99,\"quantity\":5,\"imageUrls\":[]}"
```

Upload an image:

```bash
curl -X POST http://localhost:8080/media/images ^
  -H "Authorization: Bearer <token>" ^
  -F "file=@C:\\path\\image.png" ^
  -F "productId=<product-id>"
```

Update seller profile with avatar:

```bash
curl -X PUT http://localhost:8080/me ^
  -H "Authorization: Bearer <token>" ^
  -F "fullName=Seller Updated" ^
  -F "avatar=@C:\\path\\avatar.png;type=image/png"
```

## Verification

The backend was verified with:

```bash
cd backend
mvn -q clean test
```

The live stack was also verified with:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\test-backend.ps1
```

HTTPS with the local development certificate:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\test-backend.ps1 -UseHttps -SkipCertificateValidation
```

If admin bootstrap is enabled before startup, include the same credentials in the smoke test so the admin-only checks run too:

```powershell
$env:ADMIN_EMAIL = "admin@example.com"
$env:ADMIN_PASSWORD = "Password123"
powershell -ExecutionPolicy Bypass -File .\backend\scripts\start-backend.ps1 -Clean -StopExisting
powershell -ExecutionPolicy Bypass -File .\backend\scripts\test-backend.ps1 -AdminEmail $env:ADMIN_EMAIL -AdminPassword $env:ADMIN_PASSWORD
```

The smoke test now covers:

- health endpoints and gateway routing
- public registration/login flows plus rejection of public `ADMIN` registration
- delegated avatar upload, avatar replacement, and old-avatar cleanup
- product validation, seller ownership checks, and cross-service image ownership validation
- media validation, media filtering by `productId`, public image caching headers, and post-delete download failures
- gateway rate limiting for auth and media write endpoints
- optional admin bootstrap login plus admin access to seller-owned products and media

## Notes

- The three services use separate Mongo databases on the same local Mongo instance by default.
- MinIO is the default local object store so image binaries are not stored in MongoDB.
- Kafka publishing is implemented but disabled by default so local development does not require a broker.
- `backend/scripts/generate-dev-certs.ps1` can generate a shared PKCS12 certificate for local HTTPS runs.
// webhook test
