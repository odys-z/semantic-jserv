# buld mvn war package or
# mvn clean package && docker build -t jserv-album .
# Be careful clean maven first.

#####################################################################################
# Errors like:
# docker build -t jserv-album .
# error during connect: This error may indicate that the docker daemon is not running.:
# Post "http://%2F%2F.%2Fpipe%2Fdocker_engine/v1.24/build?buildargs=%7B%7D&cachefrom=%5B%5D&cgroupparent=&cpuperiod=0&cpuquota=0&cpusetcpus=&cpusetmems=&cpushares=0&dockerfile=Dockerfile&labels=%7B%7D&memory=0&memswap=0&networkmode=default&rm=1&shmsize=0&t=jserv-album&target=&ulimits=null&version=1":
# open //./pipe/docker_engine: The system cannot find the file specified.
#
# or 
# failed to solve with frontend dockerfile.v0: failed to create LLB definition:
# failed to do request: Head "https://registry-1.docker.io/v2/library/tomcat/manifests/9.0.98-jdk17-corretto-al2":
# unable to connect to 31.13.96.194:443. Do you need an HTTP proxy?
#
# Similar Reports:
# https://github.com/docker/hub-feedback/issues/2388
# https://www.reddit.com/r/docker/comments/1dtp0do/is_docker_usable_in_china/
#
# For docker proxy, see: https://stackoverflow.com/a/78709471/7362888
# For verifying docker configuration (~/.docker/config.json) is correct,
# docker --version
# WARNING: Error loading config file: ~\.docker\config.json: json:
# cannot unmarshal string into Go struct field ConfigFile.proxies of type configfile.ProxyConfig
# Docker version 20.10.16, build aa7e414
#
# FROM tomcat:7.0
#
FROM tomcat:9.0.98-jdk17-corretto-al2
#
#####################################################################################


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
