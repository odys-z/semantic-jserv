# buld mvn war package or
# mvn clean package && docker build -t jserv-sample .
# Tests on 8/9/10 show only work for 9.0.
# Be careful clean maven first.
FROM tomcat:9.0
COPY target/jserv-sample.war $CATALINA_HOME/webapps
EXPOSE 8080
