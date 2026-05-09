# User Service Test Guide

## Purpose

`user-service` handles registration, login, JWT issuance, and profile management for `CLIENT` and `SELLER`.

## Base URLs

- Gateway: `http://localhost:8080`
- Direct: `http://localhost:8081`

## Endpoints

| Method | Path | Auth | Success | Notes |
| --- | --- | --- | --- | --- |
| `POST` | `/auth/register` | No | `201` | Registers `CLIENT` or `SELLER` and returns JWT |
| `POST` | `/auth/login` | No | `200` | Returns JWT and current profile |
| `GET` | `/me` | JWT | `200` | Returns current user profile |
| `PUT` | `/me` | JWT | `200` | Updates `fullName` and `avatarUrl` |
| `GET` | `/actuator/health` | No | `200` | Service health |

## Request Models

### Register

```json
{
  "email": "seller@example.com",
  "password": "Password123",
  "fullName": "Seller Example",
  "role": "SELLER"
}
```

### Login

```json
{
  "email": "seller@example.com",
  "password": "Password123"
}
```

### Update Profile

```json
{
  "fullName": "Seller Example Updated",
  "avatarUrl": "http://localhost:8080/media/images/abc123"
}
```

## Core Test Cases

1. Register seller with valid payload -> `201`.
2. Register client with valid payload -> `201`.
3. Login with valid credentials -> `200`.
4. `GET /me` with valid JWT -> `200`.
5. `PUT /me` with valid JWT -> `200`.
6. `PUT /me` actually persists the updated values.

## Validation and Edge Cases

1. Duplicate email registration -> `400`.
2. Invalid email format -> `400`.
3. Password shorter than 8 chars -> `400`.
4. Missing `fullName` -> `400`.
5. Invalid role value -> `400`.
6. Login with wrong password -> `401`.
7. Login with unknown email -> `401`.
8. `GET /me` without token -> `401`.
9. `PUT /me` without token -> `401`.
10. `avatarUrl` longer than 500 chars -> `400`.

## Security Expectations

- Passwords are BCrypt-hashed in storage
- JWT contains:
  - `sub` = email
  - `userId`
  - `role`
- No password field is ever returned in responses

## Common Failure Causes

- `JWT_SECRET` mismatch with gateway
- MongoDB not available
- Duplicate user index not created because database was manually modified
