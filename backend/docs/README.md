# Backend Test Documentation

Service-specific test guides are in [services/discovery-service.md](c:\Users\7abib\buy-01\backend\docs\services\discovery-service.md), [services/gateway-service.md](c:\Users\7abib\buy-01\backend\docs\services\gateway-service.md), [services/user-service.md](c:\Users\7abib\buy-01\backend\docs\services\user-service.md), [services/product-service.md](c:\Users\7abib\buy-01\backend\docs\services\product-service.md), and [services/media-service.md](c:\Users\7abib\buy-01\backend\docs\services\media-service.md).

The executable end-to-end verifier is [scripts/test-backend.ps1](c:\Users\7abib\buy-01\backend\scripts\test-backend.ps1).

Run it after all backend services are up:

```powershell
powershell -ExecutionPolicy Bypass -File .\backend\scripts\test-backend.ps1
```
