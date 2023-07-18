# buld mvn war package or
# mvn clean package && docker build -t jserv-sandbox .
# Be careful clean maven first.
FROM tomcat:9.0
COPY target/jserv-sandbox.war $CATALINA_HOME/webapps

ENV VOLUME_HOME="/var/local/volume"

EXPOSE 8080
