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
            emailext(
                mimeType: 'text/html',
                to: 'mohammedalasfoor06@gmail.com',
                subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """
                    <h2 style="color:green;">Build Successful</h2>
                    <table>
                        <tr><td><b>Job</b></td><td>${env.JOB_NAME}</td></tr>
                        <tr><td><b>Build #</b></td><td>${env.BUILD_NUMBER}</td></tr>
                        <tr><td><b>Branch</b></td><td>main</td></tr>
                        <tr><td><b>Commit</b></td><td>${env.GIT_COMMIT}</td></tr>
                        <tr><td><b>Status</b></td><td>SUCCESS</td></tr>
                        <tr><td><b>Deployment</b></td><td>Completed</td></tr>
                        <tr><td><b>Health Check</b></td><td>Passed</td></tr>
                        <tr><td><b>Build URL</b></td><td><a href="${env.BUILD_URL}">${env.BUILD_URL}</a></td></tr>
                    </table>
                """
            )

            echo "SUCCESS: Build #${BUILD_NUMBER} passed, deployed, and health check completed."
        }

        failure {
            emailext(
                mimeType: 'text/html',
                to: 'mohammedalasfoor06@gmail.com',
                subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """
                    <h2 style="color:red;">Build Failed</h2>
                    <table>
                        <tr><td><b>Job</b></td><td>${env.JOB_NAME}</td></tr>
                        <tr><td><b>Build #</b></td><td>${env.BUILD_NUMBER}</td></tr>
                        <tr><td><b>Branch</b></td><td>main</td></tr>
                        <tr><td><b>Commit</b></td><td>${env.GIT_COMMIT}</td></tr>
                        <tr><td><b>Status</b></td><td>FAILED</td></tr>
                        <tr><td><b>Build URL</b></td><td><a href="${env.BUILD_URL}">${env.BUILD_URL}</a></td></tr>
                    </table>
                """
            )

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