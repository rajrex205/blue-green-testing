pipeline {
  agent any
  parameters {
    choice (name: 'chooseNode', choices: ['Green', 'Blue'], description: 'Choose which Environment to Deploy: ')
  }
  environment {
    listenerARN = 'arn:aws:elasticloadbalancing:ap-south-1:745825563476:listener/app/blue-green/a201cfaa8896efbc/42dd93cfc6ceb733'
    blueARN = 'arn:aws:elasticloadbalancing:ap-south-1:745825563476:targetgroup/blue/2e98c824a11a99c8'
    greenARN = 'arn:aws:elasticloadbalancing:ap-south-1:745825563476:targetgroup/Green/1aa96e9ddb6bd5c5'
  }
  stages {
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
                sh """aws elbv2 modify-listener --listener-arn ${listenerARN} --default-actions '[{"Type": "forward","Order": 1,"ForwardConfig": {"TargetGroups": [{"TargetGroupArn": "${greenARN}", "Weight": 0 },{"TargetGroupArn": "${blueARN}", "Weight": 1 }],"TargetGroupStickinessConfig": {"Enabled": true,"DurationSeconds": 1}}}]'"""
              }
            }
            stage('Deploying to Green') {
              steps {
                sh '''scp -r index.html ec2-user@3.6.126.50:/usr/share/nginx/html/
                ssh -t ec2-user@3.6.126.50 -p 22 << EOF 
                sudo service nginx restart
                '''
              }
            }
            stage('Validate and Add Green for testing') {
              steps {
                sh """
                if [ "\$(curl -o /dev/null --silent --head --write-out '%{http_code}' http://3.6.126.50/)" -eq 200 ]
                then
                    echo "** BUILD IS SUCCESSFUL **"
                    curl -I http://3.6.126.50/
                    aws elbv2 modify-listener --listener-arn ${listenerARN} --default-actions '[{"Type": "forward","Order": 1,"ForwardConfig": {"TargetGroups": [{"TargetGroupArn": "${greenARN}", "Weight": 0 },{"TargetGroupArn": "${blueARN}", "Weight": 1 }],"TargetGroupStickinessConfig": {"Enabled": true,"DurationSeconds": 1}}}]'
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
            stage('Offloading Blue') {
              steps {
                sh """aws elbv2 modify-listener --listener-arn ${listenerARN} --default-actions '[{"Type": "forward","Order": 1,"ForwardConfig": {"TargetGroups": [{"TargetGroupArn": "${greenARN}", "Weight": 1 },{"TargetGroupArn": "${blueARN}", "Weight": 0 }],"TargetGroupStickinessConfig": {"Enabled": true,"DurationSeconds": 1}}}]'"""
              }
            }
            stage('Deploying to Blue') {
              steps {
                sh '''scp -r index.html ec2-user@3.6.126.50:/usr/share/nginx/html/
                ssh -t ec2-user@3.6.126.50 -p 22 << EOF 
                sudo service nginx restart
                '''
              }
            }
            stage('Validate Blue and added to TG') {
              steps {
                sh """
                if [ "\$(curl -o /dev/null --silent --head --write-out '%{http_code}' http://3.6.126.50/)" -eq 200 ]
                then
                    echo "** BUILD IS SUCCESSFUL **"
                    curl -I http://3.6.126.50/
                    aws elbv2 modify-listener --listener-arn ${listenerARN} --default-actions '[{"Type": "forward","Order": 1,"ForwardConfig": {"TargetGroups": [{"TargetGroupArn": "${greenARN}", "Weight": 1 },{"TargetGroupArn": "${blueARN}", "Weight": 1 }],"TargetGroupStickinessConfig": {"Enabled": true,"DurationSeconds": 1}}}]'
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
      }
    }
  }
}