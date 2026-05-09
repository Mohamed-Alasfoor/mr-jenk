# Gateway Service Test Guide

## Purpose

`gateway-service` is the public entry point for the marketplace backend. It applies JWT validation on protected routes, forwards the original bearer token downstream, and enforces CORS.

## Base URL

- Direct/Public: `http://localhost:8080`

## Routed Paths

| External Path | Target Service | Auth |
| --- | --- | --- |
| `/auth/**` | `user-service` | Public for register/login |
| `/me` and `/me/**` | `user-service` | JWT required |
| `/products/**` | `product-service` | Public for `GET /products` and `GET /products/{id}`; JWT required otherwise |
| `/media/**` | `media-service` | Public for `GET /media/images/{id}`; JWT required otherwise |

## Gateway-Level Behaviors

| Behavior | Expected |
| --- | --- |
| Missing token on protected endpoint | `401` |
| Invalid or expired token | `401` |
| Valid token on protected endpoint | Request forwarded |
| Public endpoint without token | Allowed |
| Unknown route | `404` |
| Valid route with unsupported method | `404` or `405` from routing/downstream, not `401` |
| CORS from allowed origin | Allowed |
| CORS from disallowed origin | Blocked by browser/client |

## Core Test Cases

1. `GET /actuator/health` returns `200`.
2. `POST /auth/register` works without JWT.
3. `POST /auth/login` works without JWT.
4. `GET /products` works without JWT.
5. `GET /products/{id}` works without JWT when the product exists.
6. `GET /media/images/{id}` works without JWT when the image exists.
7. `GET /me` without JWT returns `401`.
8. `POST /products` without JWT returns `401`.
9. `POST /media/images` without JWT returns `401`.
10. `GET /me` with a valid JWT succeeds.
11. `GET /does-not-exist` returns `404`, not `401`.
12. `GET /auth/register` returns `405`, not `401`.

## Edge Cases

1. Bearer header exists but token is malformed:
   - Expected `401`.
2. Token is valid but user role is not authorized:
   - Gateway forwards request, downstream returns `403` or `404` depending on ownership rule.
3. Downstream service unavailable:
   - Gateway returns `5xx`.
4. Wrong `JWT_SECRET` between gateway and other services:
   - Gateway rejects otherwise valid tokens with `401`.
5. Wrong CORS origin:
   - Browser request blocked even if server route exists.
6. Unsupported method on a protected path without a token:
   - Should fall through and return framework `404` or `405`.
   - Must not be converted into `401` by the gateway filter.

## Auth Propagation

- The gateway validates JWTs on protected routes.
- The original `Authorization: Bearer <token>` header is forwarded downstream unchanged.
- Downstream services perform their own JWT parsing and role enforcement.

## Common Failure Causes

- Gateway started before discovery or downstream services
- Different JWT secret between gateway and backend services
- Invalid `ALLOWED_ORIGINS`
