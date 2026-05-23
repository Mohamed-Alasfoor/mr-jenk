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
                    sh '''
                        export CHROME_BIN=/usr/bin/chromium

                        ./node_modules/.bin/ng test \
                          --watch=false \
                          --browsers=ChromeHeadlessNoSandbox \
                          --code-coverage
                    '''
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
                        docker compose ps | grep healthy
                    '''

                    echo "All services are healthy."
                }
            }
        }
    }

    post {
        success {
            echo "SUCCESS: Build #${BUILD_NUMBER} passed, deployed, and health check completed."
        }

        failure {
            echo "Build failed."
        }

        always {
            echo "Pipeline finished."

            cleanWs(
                deleteDirs: true,
                disableDeferredWipeout: true,
                notFailBuild: true
            )
        }
    }
}