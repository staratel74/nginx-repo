pipeline {
    agent any
    environment {
        DOCKER_CREDENTIALS_ID = 'dockerhub-staratel'
        REPO_URL = 'git@github.com:staratel74/nginx-repo.git'
        DOCKER_IMAGE = 'staratel/nginx-repo:v1.9'
        KUBECONFIG_CREDENTIALS_ID = 'kubeconfig-yandex'
        KUBE_NAMESPACE = 'default'
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
        stage('Deploy to Kubernetes') {
            steps {
                script {
                    withCredentials([file(credentialsId: "${KUBECONFIG_CREDENTIALS_ID}", variable: 'KUBECONFIG')]) {
                        sh '''
                        cat <<EOF | kubectl --kubeconfig=$KUBECONFIG apply -f -
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

                        cat <<EOF | kubectl --kubeconfig=$KUBECONFIG apply -f -
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
