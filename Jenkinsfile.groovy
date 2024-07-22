pipeline {
    agent any
    environment {
        DOCKER_CREDENTIALS_ID = 'dockerhub-staratel'
        REPO_URL = 'git@github.com:staratel74/nginx-repo.git'
        KUBECONFIG_CREDENTIALS_ID = 'kubeconfig-yandex'
    }
    stages {
        stage('Clone Repository') {
            steps {
                // Клонируем репозиторий из GitHub
                git branch: 'main', credentialsId: 'github-staratel74', url: "${REPO_URL}"
            }
        }
        stage('Get Git Tag') {
            steps {
                script {
                    // Получаем текущий тег из Git
                    env.GIT_TAG = sh(returnStdout: true, script: "git describe --tags --abbrev=0").trim()
                    // Устанавливаем имя Docker-образа с тегом
                    env.DOCKER_IMAGE = "staratel/nginx-repo:${env.GIT_TAG}"
                    echo "Docker Image: ${env.DOCKER_IMAGE}"
                }
            }
        }

        stage('Check Tag') {
            steps {
                script {
                    if (env.GIT_TAG != 'v1.10.9') {
                        echo "Tag is not v1.10.9. Exiting pipeline."
                        currentBuild.result = 'SUCCESS'
                        return
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    // Строим Docker-образ
                    docker.build("${DOCKER_IMAGE}")
                }
            }
        }
        stage('Push Docker Image') {
            steps {
                script {
                    // Пушим Docker-образ в Docker Hub
                    docker.withRegistry('https://index.docker.io/v1/', "${DOCKER_CREDENTIALS_ID}") {
                        docker.image("${DOCKER_IMAGE}").push()
                    }
                }
            }
        }
        stage('Deploy to Kubernetes') {
            steps {
                script {
                    withCredentials([file(credentialsId: "${KUBECONFIG_CREDENTIALS_ID}", variable: 'KUBECONFIG')]) {
                        // Step to deploy the application
                        sh '''
                        kubectl --kubeconfig=$KUBECONFIG apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
  labels:
    app: nginx
spec:
  replicas: 1
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: ${DOCKER_IMAGE}
        ports:
        - containerPort: 80
EOF
                        '''

                        // Step to create the service
                        sh '''
                        kubectl --kubeconfig=$KUBECONFIG apply -f - <<EOF
apiVersion: v1
kind: Service
metadata:
  name: nginx-service
spec:
  selector:
    app: nginx
  ports:
  - protocol: TCP
    port: 80
  type: LoadBalancer
EOF
                        '''
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
