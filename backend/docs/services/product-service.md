# Product Service Test Guide

## Purpose

`product-service` exposes public product reads and seller-only product management with ownership enforcement.

## Base URLs

- Gateway: `http://localhost:8080`
- Direct: `http://localhost:8082`

## Endpoints

| Method | Path | Auth | Success | Notes |
| --- | --- | --- | --- | --- |
| `GET` | `/products` | No | `200` | Public list |
| `GET` | `/products/{id}` | No | `200` | Public product details |
| `GET` | `/products/me` | Seller JWT | `200` | Current seller's products |
| `POST` | `/products` | Seller JWT | `201` | Creates product owned by authenticated seller |
| `PUT` | `/products/{id}` | Seller JWT | `200` | Updates only if owned by authenticated seller |
| `DELETE` | `/products/{id}` | Seller JWT | `204` | Deletes only if owned by authenticated seller |
| `GET` | `/actuator/health` | No | `200` | Service health |

## Request Model

```json
{
  "name": "Phone",
  "description": "Flagship phone",
  "price": 699.99,
  "quantity": 5,
  "imageUrls": [
    "http://localhost:8080/media/images/<media-id>"
  ]
}
```

## Core Test Cases

1. `GET /products` when empty -> `200` and empty array.
2. Seller creates valid product -> `201`.
3. Public `GET /products` shows created product -> `200`.
4. Public `GET /products/{id}` returns created product -> `200`.
5. Owner `GET /products/me` returns created product -> `200`.
6. Owner updates product name/price/quantity/imageUrls -> `200`.
7. Owner deletes product -> `204`.
8. Public `GET /products/{id}` after deletion -> `404`.

## Validation and Edge Cases

1. Missing token on `POST/PUT/DELETE` -> `401`.
2. `CLIENT` token on `POST/PUT/DELETE` -> `403`.
3. Empty name -> `400`.
4. Empty description -> `400`.
5. `price <= 0` -> `400`.
6. Negative quantity -> `400`.
7. Blank image URL entry -> `400`.
8. Unknown product id -> `404`.
9. Seller tries to update another seller's product -> `403`.
10. Seller tries to delete another seller's product -> `403`.

## Ownership Rules

- `sellerId` is always derived from the authenticated JWT
- Client input cannot override product ownership
- Non-owner access to mutate a product is treated as `403`

## Common Failure Causes

- JWT missing `userId` or `role`
- Different JWT secret from gateway/user-service
- MongoDB not available
