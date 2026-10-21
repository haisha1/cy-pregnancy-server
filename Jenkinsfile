pipeline {
    agent any
    tools {
        jdk 'jdk8'
        maven 'maven3'
    }
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    stages {
        stage('构建与部署') {
            steps {
                withCredentials([file(credentialsId: 'cy-user-prod-env', variable: 'DEPLOY_ENV_FILE')]) {
                    sh 'sh deploy/jenkins-build.sh'
                }
            }
        }
    }
}
