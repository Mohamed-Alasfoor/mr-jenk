# Jenkins Setup for Audit

This directory contains the configuration for the Jenkins CI/CD environment.

## Audit Compliance Details

### 1. Security & Permissions
- The Jenkins instance is configured in `docker-compose.yml` to run.
- **Passwords & Secrets**: All sensitive data (Docker Hub credentials, Git SSH keys, API keys) must be managed via **Jenkins Credentials** (Manage Jenkins -> Credentials). Do NOT hardcode secrets in the Jenkinsfile.
- **Access Control**: Jenkins should be configured with "Role-Based Strategy" or "Matrix Authorization Strategy" to restrict access to the dashboard.

### 2. Automated Triggering
- The pipeline is configured to poll SCM every 5 minutes (`pollSCM`). In a production environment, it is recommended to use GitHub Webhooks for instant triggering.

### 3. Automated Testing & Reports
- **Backend**: Maven runs JUnit tests. Reports are automatically archived using the `junit` plugin in the `Backend Test` stage.
- **Frontend**: Angular tests run in headless Chromium. Coverage reports are archived as build artifacts.

### 4. Deployment & Rollback
- The deployment uses `docker compose up -d` with versioned `IMAGE_TAG`.
- **Rollback Strategy**: If a build fails after the deployment stage (or if health checks fail), the `post { failure }` block automatically re-runs the deployment using the `IMAGE_TAG` from the previous successful build number.

### 5. Notifications
- The `Jenkinsfile` includes `post` blocks for `success` and `failure` with echo statements. These are ready to be integrated with the `mail` or `slackSend` steps once the corresponding plugins and credentials are set up.

## How to Run
1. Navigate to the `jenkins` directory.
2. Run `docker compose up -d`.
3. Access Jenkins at `http://localhost:8080`.
4. Create a new "Pipeline" job and point it to the Git repository.
