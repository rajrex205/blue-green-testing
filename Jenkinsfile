def region= 'eu-central-1'


pipeline {
  agent any
  parameters {
    choice (name: 'chooseNode', choices: ['Green', 'Blue'], description: 'Choose which Environment to Deploy: ')
     choice (name: 'weight', choices: ['50Blue-50Green', '80Blue-20Green', '100Blue-0Green','50Green-50Blue', '80Green-20Blue', '100Geen-0Blue'], description: 'Choose weight to Deploy each Environment: ')
  }
  environment {
    listenerARN = 'arn:aws:elasticloadbalancing:eu-central-1:823351923123:listener/app/Bluegreen-deployment/9f9121e8db33df00/133d84a04628a583'
    blueARN = 'arn:aws:elasticloadbalancing:eu-central-1:823351923123:targetgroup/BlueTG/2a7ed533e4ba29ff'
    greenARN = 'arn:aws:elasticloadbalancing:eu-central-1:823351923123:targetgroup/GreenTG/7cd0a89181b32ce3'
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
		      script {
                    if (params.weight == '50Blue-50Green') 
                        {
                         echo Blue= '1'
		         echo Green= '1'
                        }
                    if (params.weight == '80Blue-20Green') 
                        {
                        echo Blue= '4'
		         echo Green= '1'
                        }
		    if (params.weight == '100Blue-0Green') 
                        {
                        echo Blue= '1'
		         echo Green= '0'
                        }
                    else {
                        sh "echo 'selected null'"
                        }
                    }
                sh """aws elbv2 modify-listener --listener-arn ${listenerARN} --default-actions '[{"Type": "forward","Order": 1,"ForwardConfig": {"TargetGroups": [{"TargetGroupArn": "${greenARN}", "Weight": '${Green}' },{"TargetGroupArn": "${blueARN}", "Weight": '${Blue}' }],"TargetGroupStickinessConfig": {"Enabled": true,"DurationSeconds": 1}}}]'"""
              }
            }
            stage('Deploying to Green') {
              steps {
                sh '''sudo cp index.html /var/www/html/
                sudo systemctl restart httpd
                '''
              }
            }
            stage('Validate and Add Green for testing') {
              steps {
                sh """
                if [ "\$(curl -o /dev/null --silent --head --write-out '%{http_code}' http://3.75.196.205/)" -eq 200 ]
                then
                    echo "** BUILD IS SUCCESSFUL **"
                    curl -I http://3.75.196.205/
                    aws elbv2 modify-listener --listener-arn ${listenerARN} --default-actions '[{"Type": "forward","Order": 1,"ForwardConfig": {"TargetGroups": [{"TargetGroupArn": "${greenARN}", "Weight": '${Green}' },{"TargetGroupArn": "${blueARN}", "Weight": '${Blue}' }],"TargetGroupStickinessConfig": {"Enabled": true,"DurationSeconds": 1}}}]'
                else
	                echo "** BUILD IS FAILED ** Health check returned non 200 status code"
                    curl -I http://3.75.196.205/
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
                sh '''sudo scp index.html root@172.10.4.234:/var/www/html/
                sudo systemctl restart httpd
                '''
              }
            }
            stage('Validate Blue and added to TG') {
              steps {
                sh """
                if [ "\$(curl -o /dev/null --silent --head --write-out '%{http_code}' http://3.67.13.243/)" -eq 200 ]
                then
                    echo "** BUILD IS SUCCESSFUL **"
                    curl -I http://3.67.13.243/
                    aws elbv2 modify-listener --listener-arn ${listenerARN} --default-actions '[{"Type": "forward","Order": 1,"ForwardConfig": {"TargetGroups": [{"TargetGroupArn": "${greenARN}", "Weight": 0 },{"TargetGroupArn": "${blueARN}", "Weight": 1 }],"TargetGroupStickinessConfig": {"Enabled": true,"DurationSeconds": 1}}}]'
                else
	                echo "** BUILD IS FAILED ** Health check returned non 200 status code"
                    curl -I http://3.67.13.243/
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
