pipeline {
  agent any
  parameters {
    gitParameter defaultValue: 'master', name: 'BRANCH_TAG', type: 'PT_BRANCH_TAG'
    choice (name: 'chooseNode', choices: ['Green', 'Blue'], description: 'Choose which Environment to Deploy: ')
  }
  environment {
    listenerARN = 'arn:aws:elasticloadbalancing:eu-central-1:823351923123:loadbalancer/app/Bluegreen-deployment/9f9121e8db33df00'
    blueARN = 'arn:aws:elasticloadbalancing:eu-central-1:823351923123:targetgroup/BlueTG/2a7ed533e4ba29ff'
    greenARN = 'arn:aws:elasticloadbalancing:eu-central-1:823351923123:targetgroup/GreenTG/7cd0a89181b32ce3'
  }
  stages {
    stage('Cloning Git') {
      steps {
        checkout([$class: 'GitSCM', branches: [[name: "${params.BRANCH_TAG}"]], extensions: [], userRemoteConfigs: [[credentialsId: '7456cebd-f850-4183-b76d-5fcee987797a', url: 'git@github.com:signeasy/webapp-v2.git']]])
      }
    }
    stage('Deployment Started') {
      parallel {
        stage('Green') {
          when {
            expression {
              params.chooseNode == 'Green'
            }
          }
          stages {
            stage('Offloading Green') {
              steps {
                sh """aws elbv2 modify-listener --listener-arn ${getSigneasyListenerARN} --default-actions '[{"Type": "forward","Order": 1,"ForwardConfig": {"TargetGroups": [{"TargetGroupArn": "${getSigneasyNode1ARN}", "Weight": 0 },{"TargetGroupArn": "${getSigneasyNode2_3ARN}", "Weight": 1 }],"TargetGroupStickinessConfig": {"Enabled": true,"DurationSeconds": 1}}}]'"""
              }
            }
            stage('Deploying to getsigneasy 1') {
              steps {
                sh '''scp -r index.html ec2-user@3.6.126.50:/usr/share/nginx/html/
                ssh -t ec2-user@3.6.126.50 -p 22 << EOF 
                sudo service nginx restart
                '''
              }
            }
            stage('Validate and Add GSBlue for testing') {
              steps {
                sh """
                if [ "\$(curl -o /dev/null --silent --head --write-out '%{http_code}' http://3.6.126.50/)" -eq 200 ]
                then
                    echo "** BUILD IS SUCCESSFUL **"
                    curl -I http://3.6.126.50/
                    aws elbv2 modify-listener --listener-arn ${getSigneasyListenerARN} --default-actions '[{"Type": "forward","Order": 1,"ForwardConfig": {"TargetGroups": [{"TargetGroupArn": "${getSigneasyNode1ARN}", "Weight": 1 },{"TargetGroupArn": "${getSigneasyNode2_3ARN}", "Weight": 0 }],"TargetGroupStickinessConfig": {"Enabled": true,"DurationSeconds": 1}}}]'
                else
	                echo "** BUILD IS FAILED ** Health check returned non 200 status code"
                    curl -I http://3.6.126.50/
                exit 2
                fi
                """
              }
            }
          }
        }
        stage('Blue') {
          when {
            expression {
              params.chooseNode == 'Blue'
            }
          }
          stages {
            stage('Offloading Blue TG') {
              steps {
                sh """aws elbv2 modify-listener --listener-arn ${signeasyListenerARN} --default-actions '[{"Type": "forward","Order": 1,"ForwardConfig": {"TargetGroups": [{"TargetGroupArn": "${signeasyBlueARN}", "Weight": 0 },{"TargetGroupArn": "${signeasyGreenARN}", "Weight": 1 }],"TargetGroupStickinessConfig": {"Enabled": true,"DurationSeconds": 604800}}}]'"""
              }
            }
            stage('Deploying to Blue 0') {
              steps {
                sh '''rsync -e "ssh -p 22" --recursive --times --compress --delete --progress . ubuntu@172.31.1.53:/home/ubuntu/deployment_files
                ssh -t ubuntu@172.31.1.53 -p 22 << EOF 
                sudo rm -rf /var/www/html/webapp.getsigneasy.com/
                sudo cp -R /home/ubuntu/deployment_files/. /var/www/html/webapp.getsigneasy.com
                sudo find /var/www/html/webapp.getsigneasy.com -type d -exec chmod 775 {} + 
                sudo find /var/www/html/webapp.getsigneasy.com -type f -exec chmod 644 {} +
                sudo chown -R www-data:www-data /var/www/html/webapp.getsigneasy.com
                sudo bash -x /home/ubuntu/deployment_files/deployment/scripts/prod/post.sh
                sudo service nginx start
                '''
              }
            }
            stage('Validate Blue 0 health') {
              steps {
                sh """
                if [ "\$(curl -o /dev/null --silent --head --write-out '%{http_code}' http://172.31.1.53/login)" -eq 200 ]
                then
                    echo "** BUILD IS SUCCESSFUL **"
                    curl -I http://172.31.1.53/login
                else
	                echo "** BUILD IS FAILED ** Health check returned non 200 status code"
                    curl -I http://172.31.1.53/login
                exit 2
                fi
                """
              }
            }
            stage('Deploying to Blue 1') {
              steps {
                sh '''ssh -t ubuntu@172.31.1.53 -p 22 << EOF
                rsync -e "ssh -p 22" --recursive --times --compress --delete --progress /var/www/html/. ubuntu@172.31.1.204:/var/www/html/
                rsync -e "ssh -p 22" --recursive --times --compress --delete --progress /etc/nginx/sites-available/. ubuntu@172.31.1.204:/etc/nginx/sites-available/
                ssh -t ubuntu@172.31.1.204 -p 22 '
                uname -a
                sudo service nginx restart '
                '''
              }
            }
            stage('Validate Blue 1 health') {
              steps {
                sh """
                if [ "\$(curl -o /dev/null --silent --head --write-out '%{http_code}' http://172.31.1.204/login)" -eq 200 ]
                then
                  echo "** BUILD IS SUCCESSFUL **"
                  curl -I http://172.31.1.204/login
                else
	                echo "** BUILD IS FAILED ** Health check returned non 200 status code"
                  curl -I http://172.31.1.204/login
                exit 2
                fi
                """
              }
            }
            stage('Deploying to Blue 2') {
              steps {
                sh '''ssh -t ubuntu@172.31.1.53 -p 22 << EOF
                rsync -e "ssh -p 22" --recursive --times --compress --delete --progress /var/www/html/. ubuntu@172.31.1.86:/var/www/html/
                rsync -e "ssh -p 22" --recursive --times --compress --delete --progress /etc/nginx/sites-available/. ubuntu@172.31.1.86:/etc/nginx/sites-available/
                ssh -t ubuntu@172.31.1.86 -p 22 '
                uname -a
                sudo service nginx restart '
                '''
              }
            }
            stage('Validate Blue 2 health & Add TG') {
              steps {
                sh """
                if [ "\$(curl -o /dev/null --silent --head --write-out '%{http_code}' http://172.31.1.86/login)" -eq 200 ]
                then
                  echo "** BUILD IS SUCCESSFUL **"
                  curl -I http://172.31.1.86/login
                  aws elbv2 modify-listener --listener-arn ${signeasyListenerARN} --default-actions '[{"Type": "forward","Order": 1,"ForwardConfig": {"TargetGroups": [{"TargetGroupArn": "${signeasyBlueARN}", "Weight": 1 },{"TargetGroupArn": "${signeasyGreenARN}", "Weight": 0 }],"TargetGroupStickinessConfig": {"Enabled": true,"DurationSeconds": 604800}}}]'
                else
	                echo "** BUILD IS FAILED ** Health check returned non 200 status code"
                  curl -I http://172.31.1.86/login
                exit 2
                fi
                """
              }
            }
          }
        }
      }
    }
  }
}
