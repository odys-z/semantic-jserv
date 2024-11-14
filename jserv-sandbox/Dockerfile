# buld mvn war package or
# mvn clean package && docker build -t jserv-sandbox .
# Be careful clean maven first.
FROM tomcat:9.0
COPY target/jserv-sandbox.war $CATALINA_HOME/webapps

ARG imgtag=jserv-album
ARG ver=0.6.50.1
ARG imghub="https://hub.docker.com/u/odysz"
ARG echo_html="echo.html"

# #################################
#
#
# This line requires build with --no-cache
#
RUN echo "<html><body><h1>${imgtag} `echo $ver`</h1><p><a href='${imghub}'>Image Home</a></p><p>Built at `date`</p></body></html>" > $CATALINA_HOME/webapps/ROOT/${echo_html};

ENV VOLUME_HOME="/var/local/volume"

EXPOSE 8080
