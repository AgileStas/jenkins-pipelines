library changelog: false, identifier: "lib@master", retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
])

def moleculeDir = "pbm-functional/replicaset"

pipeline {
    agent {
    label 'min-centos-7-x64'
    }
    environment {
        PATH = '/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/home/ec2-user/.local/bin'
        ANSIBLE_DISPLAY_SKIPPED_HOSTS = false
        SCENARIO = 'aws'
    }
    parameters {
        string(name: 'BRANCH',description: 'PBM repo branch',defaultValue: 'pbm_2.0')
        choice(name: 'PSMDB',description: 'PSMDB for testing',choices: ['psmdb-44','psmdb-42','psmdb-50'])        
        choice(name: 'STORAGE',description: 'Storage for PBM',choices: ['aws','gcp'])
        string(name: 'TIMEOUT',description: 'Timeout for backup/restore',defaultValue: '60')
        string(name: 'SIZE',description: 'Data size for test collection',defaultValue: '100')
        string(name: 'TESTING_BRANCH',description: 'Branch for testing repository',defaultValue: 'main')
    }
    options {
        withCredentials(moleculePbmJenkinsCreds())
        disableConcurrentBuilds()
    }
    stages {
        stage('Set build name'){ 
            steps {
                script {
                    currentBuild.displayName = "${env.BUILD_NUMBER}-${env.SCENARIO}"
                }
            }
        }
        stage('Checkout') {
            steps {
                deleteDir()
                git poll: false, branch: TESTING_BRANCH, url: 'https://github.com/Percona-QA/psmdb-testing.git'
            }
        }
        stage ('Install molecule') {
            steps {
                script {
                    installMolecule()
                }
            }
        }
        stage ('Install build tools') {
            steps {
                sh """
                    curl "https://github.com/percona/percona-backup-mongodb/blob/main/packaging/scripts/mongodb-backup_builder.sh" -o "mongodb-backup_builder.sh"
                    chmod +x mongodb-backup_builder.sh
                    mkdir /tmp/builddir
                    sudo ./mongodb-backup_builder.sh --builddir=/tmp/builddir --install_deps=1
                """
            }
        }
        stage ('Run playbook full test sequence') {
            steps {
                script{
                    moleculeExecuteActionWithScenario(moleculeDir, "test", env.SCENARIO)
                }
                junit testResults: "**/report.xml", keepLongStdio: true
            }
        }
    }
}
