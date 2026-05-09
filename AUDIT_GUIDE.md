# Jenkins CI/CD Audit Guide

This guide provides step-by-step instructions on how to demonstrate the CI/CD pipeline capabilities for your audit.

---

## 🛠 Initial Setup (Do this before the audit)

To ensure the auditor sees a working system, follow these steps:

1. **Start Jenkins**:
   - Navigate to the `jenkins/` directory.
   - Run: `docker compose up -d`.
   - Access Jenkins at `http://localhost:8080`.

2. **Configure the Pipeline**:
   - Create a **New Item** -> **Pipeline**.
   - Name it `Ecommerce-Pipeline`.
   - Under **Pipeline**, select **Pipeline script from SCM**.
   - SCM: **Git**.
   - Repository URL: `https://github.com/Mohamed-Alasfoor/mr-jenk.git`.
   - Branch: `*/main`. (Or the branch you are using).

3. **Install Plugins (Required from your side)**:
   - Ensure the following plugins are installed in Jenkins (Manage Jenkins -> Plugins):
     - **Docker Pipeline**
     - **JUnit Plugin**
     - **Slack Notification** (Optional, for notifications)

---

## 📋 Audit Questions - Step-by-Step Testing

### 1. Functional: Trigger a Jenkins build
- **Question**: Does the pipeline initiate and run successfully from start to finish?
- **How to Test**:
  1. Go to the `Ecommerce-Pipeline` job.
  2. Click **Build Now**.
  3. Observe the "Stage View". It should progress through: *Checkout -> Backend Test -> Frontend Test -> Build Images -> Deploy -> Health Check*.
  4. Once finished, the build dot should turn **Blue** (Success).

### 2. Functional: Intentional build errors
- **Question**: Does Jenkins respond appropriately to build errors?
- **How to Test**:
  1. Break a test: Go to `backend/user-service/src/test/java/com/buy01/userservice/service/UserServiceTest.java` and change an assertion to fail.
  2. Commit and trigger a build.
  3. **Result**: The pipeline should stop at the **Backend Test** stage and turn **Red** (Failure).

### 3. Functional: Automated testing integration
- **Question**: Are tests run automatically? Does it halt on failure?
- **How to Test**:
  1. Point the auditor to the **Console Output** of a build.
  2. Show where `mvn test` and `npm test` are executed.
  3. Show that if these commands fail, the subsequent stages (Build/Deploy) are **skipped**.

### 4. Functional: Automatic trigger on commit
- **Question**: Does a new push automatically trigger the pipeline?
- **How to Test**:
  1. Make a small change (e.g., in `README.md`).
  2. Commit and push: `git add .`, `git commit -m "audit test"`, `git push`.
  3. Wait a few minutes (configured for 5-min polling) or show the `pollSCM` configuration in the `Jenkinsfile`.
  4. **Result**: A new build should start automatically.

### 5. Functional: Deployment & Rollback
- **Question**: Is the application deployed automatically? Is there a rollback strategy?
- **How to Test**:
  1. **Deployment**: After a successful build, run `docker compose ps` in your terminal to show the services are running with the new build number.
  2. **Rollback**: Trigger a build that fails *after* a previous successful build. Show the Jenkins logs in the `post { failure }` section where it runs `IMAGE_TAG=<previous_build_number> docker compose up -d`.

### 6. Security: Permissions & Secrets
- **Question**: Are permissions set? Is sensitive data secured?
- **How to Test**:
  1. **Permissions**: Go to **Manage Jenkins -> Configure Global Security**. Show that "Logged-in users can do anything" or "Role-Based Strategy" is enabled.
  2. **Secrets**: Go to **Manage Jenkins -> Credentials**. Show where you would store Docker Hub passwords or SSH keys. Point to the `Jenkinsfile` where `credentials()` would be used instead of hardcoding.

### 7. Code Quality: Jenkinsfile Best Practices
- **Question**: Is the script well-organized?
- **How to Test**:
  1. Open the `Jenkinsfile` in the root.
  2. Point out:
     - **Declarative syntax** (easy to read).
     - **Stages** for clear separation of concerns.
     - **Post-build actions** for cleanup and notifications.
     - **Environment variables** for versioning.

### 8. Code Quality: Test Reports
- **Question**: Are test reports clear and stored?
- **How to Test**:
  1. In the Jenkins job page, click on a completed build.
  2. Click on **Test Result**.
  3. Show the graph and the list of passed/failed tests (archived via the `junit` step).

### 9. Notifications
- **Question**: Are notifications informative?
- **How to Test**:
  1. Show the `post { success }` and `post { failure }` blocks in the `Jenkinsfile`.
  2. Explain that these are configured to send emails/Slack messages (skeletons provided in the code).

---

## ✅ Summary: What I did vs. What YOU need to do

### What I have done:
- Created the **Jenkins infrastructure** (`jenkins/Dockerfile`, `jenkins/docker-compose.yml`).
- Wrote the **entire Pipeline logic** (`Jenkinsfile`).
- Prepared the **microservices** for tagging and rollbacks (`docker-compose.yml`).
- Integrated **Backend (JUnit)** and **Frontend (Karma)** tests.
- Set up **Automated Rollback** and **Health Checks**.
- Cleaned the repo and added `.gitignore`.

### What YOU need to do:
1. **Run the Jenkins container** on your machine or server.
2. **Install the required Plugins** in the Jenkins UI (JUnit, Docker Pipeline).
3. **Add your Credentials** (like GitHub SSH keys or Docker Hub logins) to the Jenkins Credentials store.
4. **Create the Pipeline Job** in the Jenkins UI as described in the "Initial Setup" section.
