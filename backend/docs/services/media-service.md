# Media Service Test Guide

## Purpose

`media-service` handles seller image uploads, metadata storage, public image download, and ownership-based deletion.

## Base URLs

- Gateway: `http://localhost:8080`
- Direct: `http://localhost:8083`

## Endpoints

| Method | Path | Auth | Success | Notes |
| --- | --- | --- | --- | --- |
| `GET` | `/media/images` | Seller JWT | `200` | Lists current seller's images; supports `productId` filter |
| `POST` | `/media/images` | Seller JWT | `201` | Multipart image upload |
| `GET` | `/media/images/{id}` | No | `200` | Public download with cache headers |
| `DELETE` | `/media/images/{id}` | Seller JWT | `204` | Deletes only if owned by authenticated seller |
| `GET` | `/actuator/health` | No | `200` | Service health |

## Upload Rules

- Multipart field name: `file`
- Optional form field: `productId`
- Allowed type: `image/*`
- Real file signature must match a supported image type
- Max file size: `2 MB`
- File bytes are stored on disk, not in MongoDB

## Core Test Cases

1. Seller uploads valid PNG/JPEG -> `201`.
2. Seller lists own media -> `200`.
3. Public `GET /media/images/{id}` downloads image -> `200`.
4. Response contains image content type and cache headers.
5. Owner deletes uploaded image -> `204`.
6. Public `GET /media/images/{id}` after deletion -> `404`.

## Validation and Edge Cases

1. Missing token on upload/delete/list -> `401`.
2. `CLIENT` token on upload/delete/list -> `403`.
3. No multipart file sent -> `400`.
4. Empty file sent -> `400`.
5. Non-image MIME type -> `415`.
6. Fake image renamed with `.png` but invalid bytes -> `400`.
7. File over `2 MB` -> `400`.
8. Unknown media id on delete -> `404`.
9. Seller tries to delete another seller's media -> `403`.
10. Download unknown media id -> `404`.

## Supported Detected Types

- `image/jpeg`
- `image/png`
- `image/gif`
- `image/bmp`
- `image/webp`

## What To Verify On Disk

- Files appear under the configured storage root
- File names are generated server-side
- Metadata is stored in MongoDB separately from file bytes

## Common Failure Causes

- Storage root not writable
- `MEDIA_PUBLIC_BASE_URL` wrong, causing bad returned URLs
- Multipart size limit overridden by external config
