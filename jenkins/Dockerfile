FROM jenkins/jenkins:lts

USER root

# Install Docker CLI and Docker Compose Plugin
RUN apt-get update && \
    apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release && \
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null && \
    apt-get update && \
    apt-get install -y docker-ce-cli docker-compose-plugin

# Install Maven (for backend tests)
RUN apt-get install -y maven

# Install Node.js and Chrome (for frontend tests)
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs

# Install Chromium for headless testing
RUN apt-get install -y chromium

# Set environment variable for Chrome
ENV CHROME_BIN=/usr/bin/chromium

# Add jenkins user to docker group (group ID 999 is common for docker, but it might vary)
# A more robust way is to do it at runtime, but for now we try to set permissions in docker-compose
RUN groupadd docker && usermod -aG docker jenkins

USER jenkins
