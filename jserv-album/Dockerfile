# buld mvn war package or
# mvn clean package && docker build -t jserv-sandbox .
# Be careful clean maven first.
FROM tomcat:9.0
RUN sed -i 's/port="8080"/port="80"/' ${CATALINA_HOME}/conf/server.xml
COPY target/jserv-album.war $CATALINA_HOME/webapps
EXPOSE 80
