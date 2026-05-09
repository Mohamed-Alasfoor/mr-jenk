# Discovery Service Test Guide

## Purpose

`discovery-service` is the Eureka registry used by the gateway and all backend services for service discovery.

## Base URL

- Direct: `http://localhost:8761`

## Endpoints

| Method | Path | Auth | Expected | Notes |
| --- | --- | --- | --- | --- |
| `GET` | `/actuator/health` | No | `200` | Health check for service availability |
| `GET` | `/actuator/info` | No | `200` | Optional metadata |
| `GET` | `/` | No | `200` | Eureka dashboard UI |

## Core Test Cases

1. `GET /actuator/health` returns `200` and body contains `UP`.
2. `GET /` loads the Eureka dashboard.
3. After starting all services, the dashboard shows:
   - `GATEWAY-SERVICE`
   - `USER-SERVICE`
   - `PRODUCT-SERVICE`
   - `MEDIA-SERVICE`

## Edge Cases

1. Discovery service down:
   - Gateway and backend services may still start, but registration/discovery will not work.
2. Registry empty:
   - Usually means downstream services are not running or `EUREKA_DEFAULT_ZONE` is wrong.
3. Actuator health fails:
   - Check the port binding and startup logs first.

## What To Verify In Logs

- Eureka server starts on port `8761`
- No port conflict
- Services register successfully after startup

## Common Failure Causes

- Discovery service not started before other services
- Wrong `EUREKA_DEFAULT_ZONE`
- Firewall or port conflict on `8761`
