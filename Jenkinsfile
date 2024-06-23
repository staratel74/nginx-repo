pipeline {
    agent any
    environment {
        DOCKER_CREDENTIALS_ID = 'dockerhub-staratel'
        REPO_URL = 'git@github.com:staratel74/nginx-repo.git'
        DOCKER_IMAGE = 'nginx:v1.7'
    }
    stages {
        stage('Clone Repository') {
            steps {
                git branch: 'main', credentialsId: 'github-staratel74', url: "${REPO_URL}"
            }
        }
        stage('Build Docker Image') {
            steps {
                script {
                    docker.build("${DOCKER_IMAGE}")
                }
            }
        }
        stage('Push Docker Image') {
            steps {
                script {
                    docker.withRegistry('https://index.docker.io/v1/', "${DOCKER_CREDENTIALS_ID}") {
                        docker.image("${DOCKER_IMAGE}").push()
                    }
                }
            }
        }
    }
    post {
        always {
            cleanWs()
        }
    }
}
