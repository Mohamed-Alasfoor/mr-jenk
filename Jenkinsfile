pipeline {
    agent any

    triggers {
        pollSCM('H/5 * * * *')
    }

    environment {
        IMAGE_TAG = "${BUILD_NUMBER}"
        STATE_DIR = "/var/jenkins_home/deployment-state/mr-jenk"
        PREVIOUS_IMAGE_TAG = ""
        DEPLOYMENT_STARTED = "false"
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
                    junit allowEmptyResults: true,
                          testResults: 'backend/**/target/surefire-reports/*.xml'
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
                    archiveArtifacts(
                        artifacts: 'frontend/coverage/**',
                        allowEmptyArchive: true
                    )
                }
            }
        }

        stage('Read Previous Deployment') {
            steps {
                script {
                    sh '''
                        mkdir -p "${STATE_DIR}"
                    '''

                    env.PREVIOUS_IMAGE_TAG = sh(
                        script: '''
                            if [ -f "${STATE_DIR}/last-successful-build" ]; then
                                cat "${STATE_DIR}/last-successful-build"
                            fi
                        ''',
                        returnStdout: true
                    ).trim()

                    if (env.PREVIOUS_IMAGE_TAG) {
                        echo "Previous successful image tag: ${env.PREVIOUS_IMAGE_TAG}"
                    } else {
                        echo "No previous successful deployment was found."
                    }
                }
            }
        }

        stage('Build Images') {
            steps {
                sh '''
                    IMAGE_TAG=${IMAGE_TAG} docker compose build \
                        discovery-service \
                        media-service \
                        product-service \
                        user-service \
                        gateway-service \
                        frontend
                '''
            }
        }

        stage('Deploy') {
            steps {
                script {
                    env.DEPLOYMENT_STARTED = 'true'
                }

                sh '''
                    IMAGE_TAG=${IMAGE_TAG} docker compose up -d \
                        mongo \
                        minio \
                        discovery-service \
                        media-service \
                        product-service \
                        user-service \
                        gateway-service \
                        frontend
                '''
            }
        }

        stage('Health Check') {
            steps {
                sh '''
                    MAX_ATTEMPTS=18
                    ATTEMPT=1

                    SERVICES="
                        mongo
                        minio
                        discovery-service
                        media-service
                        product-service
                        user-service
                        gateway-service
                        frontend
                    "

                    while [ "$ATTEMPT" -le "$MAX_ATTEMPTS" ]; do
                        echo "Health-check attempt $ATTEMPT/$MAX_ATTEMPTS"

                        ALL_READY=true

                        for SERVICE in $SERVICES; do
                            CONTAINER_ID=$(docker compose ps -q "$SERVICE")

                            if [ -z "$CONTAINER_ID" ]; then
                                echo "$SERVICE: container was not found"
                                ALL_READY=false
                                continue
                            fi

                            STATUS=$(docker inspect \
                                --format='{{.State.Status}}' \
                                "$CONTAINER_ID")

                            HEALTH=$(docker inspect \
                                --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
                                "$CONTAINER_ID")

                            echo "$SERVICE: status=$STATUS, health=$HEALTH"

                            if [ "$STATUS" != "running" ]; then
                                ALL_READY=false
                            fi

                            if [ "$HEALTH" = "starting" ] || [ "$HEALTH" = "unhealthy" ]; then
                                ALL_READY=false
                            fi
                        done

                        docker compose ps

                        if [ "$ALL_READY" = "true" ]; then
                            echo "All application services are running and healthy."
                            exit 0
                        fi

                        ATTEMPT=$((ATTEMPT + 1))
                        sleep 10
                    done

                    echo "Health check failed after the timeout."
                    docker compose ps
                    exit 1
                '''
            }
        }

        stage('Record Successful Deployment') {
            steps {
                sh '''
                    mkdir -p "${STATE_DIR}"

                    echo "${IMAGE_TAG}" \
                        > "${STATE_DIR}/last-successful-build"

                    mkdir -p rollback

                    cp docker-compose.yml \
                        rollback/docker-compose.yml

                    echo "${IMAGE_TAG}" \
                        > rollback/last-successful-build
                '''

                archiveArtifacts(
                    artifacts: 'rollback/**',
                    allowEmptyArchive: true
                )
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
                        <tr><td><b>Image Tag</b></td><td>${env.IMAGE_TAG}</td></tr>
                        <tr>
                            <td><b>Build URL</b></td>
                            <td>
                                <a href="${env.BUILD_URL}">
                                    ${env.BUILD_URL}
                                </a>
                            </td>
                        </tr>
                    </table>
                """
            )

            echo "SUCCESS: Build #${BUILD_NUMBER} passed, deployed, and passed the health check."
        }

        failure {
            script {
                if (
                    env.DEPLOYMENT_STARTED == 'true' &&
                    env.PREVIOUS_IMAGE_TAG?.trim()
                ) {
                    echo "Deployment failed. Rolling back to image tag ${env.PREVIOUS_IMAGE_TAG}."

                    sh """
                        IMAGE_TAG=${env.PREVIOUS_IMAGE_TAG} docker compose up -d \
                            mongo \
                            minio \
                            discovery-service \
                            media-service \
                            product-service \
                            user-service \
                            gateway-service \
                            frontend
                    """

                    echo "Rollback command completed."
                } else if (env.DEPLOYMENT_STARTED == 'true') {
                    echo "Deployment failed, but no previous successful image tag is available."
                } else {
                    echo "The pipeline failed before deployment. Rollback is not required."
                }
            }

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
                        <tr><td><b>Attempted Image</b></td><td>${env.IMAGE_TAG}</td></tr>
                        <tr><td><b>Rollback Image</b></td><td>${env.PREVIOUS_IMAGE_TAG ?: 'Not available'}</td></tr>
                        <tr>
                            <td><b>Build URL</b></td>
                            <td>
                                <a href="${env.BUILD_URL}">
                                    ${env.BUILD_URL}
                                </a>
                            </td>
                        </tr>
                    </table>
                """
            )

            echo "Build failed."
        }

        always {
            echo "Pipeline finished with status: ${currentBuild.currentResult}"
        }

        cleanup {
            cleanWs(
                deleteDirs: true,
                disableDeferredWipeout: true,
                notFailBuild: true
            )
        }
    }
}