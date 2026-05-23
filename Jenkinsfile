pipeline {
    agent any

    triggers {
        githubPush()
        pollSCM('H/5 * * * *')
    }

    environment {
        IMAGE_TAG = "${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Verify Tools') {
            steps {
                sh '''
                    java -version
                    mvn -version
                    node -v
                    npm -v
                    docker --version
                    docker compose version
                '''
            }
        }

        stage('Backend Test') {
            steps {
                dir('backend') {
                    sh 'mvn clean test'
                }
            }
            post {
                always {
                    junit 'backend/**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Frontend Test') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm test -- --watch=false --browsers=ChromeHeadless --code-coverage'
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'frontend/coverage/**', allowEmptyArchive: true
                }
            }
        }

        stage('Build Images') {
            steps {
                sh 'IMAGE_TAG=${BUILD_NUMBER} docker compose build'
            }
        }

        stage('Save Rollback Version') {
            steps {
                sh '''
                    echo "${BUILD_NUMBER}" > .last-successful-build
                    mkdir -p rollback
                    cp docker-compose.yml rollback/docker-compose.yml
                '''
                archiveArtifacts artifacts: 'rollback/docker-compose.yml,.last-successful-build', allowEmptyArchive: true
            }
        }

        stage('Deploy') {
            steps {
                sh 'IMAGE_TAG=${BUILD_NUMBER} docker compose up -d'
            }
        }

        stage('Health Check') {
            steps {
                script {
                    echo "Waiting for services to become healthy..."
                    sleep 30

                    sh 'docker compose ps'

                    sh '''
                        curl -f http://localhost:8761/actuator/health
                        curl -f http://localhost:8080/actuator/health
                        curl -f http://localhost:8081/actuator/health
                        curl -f http://localhost:8082/actuator/health
                        curl -f http://localhost:8083/actuator/health
                        curl -k -f https://localhost/healthz || curl -f http://localhost/healthz
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "SUCCESS: Build #${BUILD_NUMBER} passed, deployed, and health check completed."
        }

        failure {
            echo "FAILURE: Build #${BUILD_NUMBER} failed. Starting rollback..."

            script {
                if (env.BUILD_NUMBER.toInteger() > 1) {
                    def previousTag = (env.BUILD_NUMBER.toInteger() - 1).toString()

                    echo "Rolling back to previous image tag: ${previousTag}"

                    sh """
                        IMAGE_TAG=${previousTag} docker compose up -d
                        docker compose ps
                    """

                    echo "Rollback completed."
                } else {
                    echo "No previous build available for rollback."
                }
            }
        }

        always {
            echo "Pipeline finished. Build URL: ${BUILD_URL}"

            cleanWs(
                deleteDirs: true,
                disableDeferredWipeout: true,
                notFailBuild: true
            )
        }
    }
}