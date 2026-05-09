pipeline {
    agent any

    environment {
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        // These would be configured in Jenkins credentials
        // SLACK_WEBHOOK_URL = credentials('slack-webhook-url')
    }

    stages {
        stage('Checkout') {
            steps {
                // Jenkins usually handles checkout if the Jenkinsfile is in the repo
                checkout scm
            }
        }

        stage('Backend Test') {
            steps {
                dir('backend') {
                    // Running mvn clean test on the parent POM will test all modules
                    sh 'mvn clean test'
                }
            }
        }

        stage('Frontend Test') {
            steps {
                dir('frontend') {
                    sh 'npm install'
                    // Ensure the test run in headless mode and finishes
                    sh 'npm test -- --watch=false --browsers=ChromeHeadless'
                }
            }
        }

        stage('Build Images') {
            steps {
                // Using the docker-compose-plugin (docker compose instead of docker-compose)
                sh 'docker compose build'
            }
        }

        stage('Deploy') {
            steps {
                // Deploy with the new build number as tag
                sh 'docker compose up -d'
            }
        }

        stage('Health Check') {
            steps {
                script {
                    echo "Waiting for services to be healthy..."
                    // Wait for services to start and pass health checks defined in docker-compose.yml
                    sleep 60
                    sh 'docker compose ps'

                    // Verify if services are actually running
                    def status = sh(script: 'docker compose ps --format json', returnStdout: true).trim()
                    echo "Current status: ${status}"
                }
            }
        }
    }

    post {
        always {
            echo "Pipeline finished for build ${env.BUILD_NUMBER}"
        }
        success {
            echo "Build and Deployment successful!"
            // Example notification (requires Slack Notification plugin)
            // slackSend channel: '#deployments', color: 'good', message: "SUCCESS: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
        }
        failure {
            echo "Build or Deployment failed. Initiating rollback..."
            script {
                // Rollback strategy: redeploy with previous build tag if available
                if (env.BUILD_NUMBER.toInteger() > 1) {
                    def previousTag = (env.BUILD_NUMBER.toInteger() - 1).toString()
                    echo "Rolling back to IMAGE_TAG=${previousTag}"
                    sh "IMAGE_TAG=${previousTag} docker compose up -d"
                } else {
                    echo "No previous version to rollback to."
                }
            }
            // Example notification
            // slackSend channel: '#deployments', color: 'danger', message: "FAILURE: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
        }
    }
}
