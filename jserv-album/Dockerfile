# buld mvn war package or
# mvn clean package && docker build -t jserv-album .
# Be careful clean maven first.

FROM tomcat:9.0.98-jdk17-corretto-al2
RUN sed -i 's/port="8080"/port="80"/' ${CATALINA_HOME}/conf/server.xml
RUN apt-get update && apt-get -y install exiftool

COPY target/jserv-album/index.html $CATALINA_HOME/webapps/ROOT/index.html
COPY target/jserv-album-0.7*.war $CATALINA_HOME/webapps

ARG imgtag=jserv-album
ARG ver=0.7.0
ARG imghub="https://hub.docker.com/u/odysz"
ARG echo_html="echo.html"

# RUN mkdir $CATALINA_HOME/webapps/ROOT; \
#
#
#
# This line requires build with --no-cache
#
RUN echo "<html><body><h1>${imgtag} `echo $ver`</h1><p><a href='${imghub}'>Image Home</a></p><p>Built at `date`</p></body></html>" > $CATALINA_HOME/webapps/ROOT/${echo_html};
# mkdir $CATALINA_HOME/webapps/${imgtag}; \
# echo "<html><body><h1>${imgtag} `echo $ver`</h1><p><a href='${imghub}'>Image Home</a></p><p>Built at `date`</p></body></html>" > $CATALINA_HOME/webapps/${imgtag}/${echo_html}

ENV VOLUME_HOME="/var/local/volume"

EXPOSE 80
