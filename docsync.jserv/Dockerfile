# https://stackoverflow.com/a/56295453
# FROM nginx:alpine
# RUN apk add openjdk17

FROM openjdk:17

COPY target/docsync.jserv-0.2.0-SNAPSHOT.jar /synodetier/

RUN apt-get update && apt-get -y install exiftool

ENV VOLUME_HOME="/var/local/volume"

WORKDIR /synodetier

CMD ["java", "-jar", "docsync.jserv-0.2.0-SNAPSHOT.jar", "" "8964"]

