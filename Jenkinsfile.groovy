// pipeline {
//     agent any
//     environment {
//         DOCKER_CREDENTIALS_ID = 'dockerhub-staratel'
//         KUBECONFIG_CREDENTIALS_ID = 'kubeconfig-yandex'
//         REPO_URL = 'git@github.com:staratel74/nginx-repo.git'
//         DOCKER_IMAGE = 'staratel/nginx-repo:v1.10.0'
//     }
//     stages {
//         stage('Clone Repository') {
//             steps {
//                 git branch: 'main', credentialsId: 'github-staratel74', url: "${REPO_URL}"
//             }
//         }

pipeline {
    agent any
    environment {
        DOCKER_CREDENTIALS_ID = 'dockerhub-staratel'
        REPO_URL = 'git@github.com:staratel74/nginx-repo.git'
        KUBECONFIG_CREDENTIALS_ID = 'kubeconfig-yandex'
        TARGET_TAG = '1.10.3' // Тег, при котором будет выполняться сборка и деплой
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
                    env.GIT_TAG = sh(returnStdout: true, script: "git describe --tags `git rev-list --tags --max-count=1`").trim()
                    echo "Current Git Tag: ${env.GIT_TAG}"
                }
            }
        }
        stage('Check Tag') {
            when {
                expression {
                    // Проверяем, что текущий тег совпадает с целевым тегом
                    return env.GIT_TAG == env.TARGET_TAG
                }
            }
            steps {
                echo "Tag ${env.GIT_TAG} matches target tag ${env.TARGET_TAG}. Proceeding with build and deployment."
            }
        }
        stage('Build Docker Image') {
            when {
                expression {
                    return env.GIT_TAG == env.TARGET_TAG
                }
            }
            steps {
                script {
                    // Устанавливаем имя Docker-образа с тегом
                    env.DOCKER_IMAGE = "staratel/nginx-repo:${env.GIT_TAG}"
                    echo "Docker Image: ${env.DOCKER_IMAGE}"
                    // Строим Docker-образ
                    docker.build("${DOCKER_IMAGE}")
                }
            }
        }
        stage('Push Docker Image') {
            when {
                expression {
                    return env.GIT_TAG == env.TARGET_TAG
                }
            }
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
  replicas: 2
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
    targetPort: 80
    nodePort: 30000
  type: NodePort
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
