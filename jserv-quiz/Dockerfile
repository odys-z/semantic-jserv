# buld mvn war package or
# mvn clean package && docker build -t jserv-quiz .
# See ../jserv-sample/Dockerfile for why not latest tomcat.
# Be careful clean maven first.
FROM tomcat:9.0
COPY target/jserv-sample.war $CATALINA_HOME/webapps
EXPOSE 8080
