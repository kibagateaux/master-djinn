# default pedestal template build
# FROM openjdk:8-alpine
# MAINTAINER Your Name <you@example.com>
# ADD target/master-djinn-0.0.1-SNAPSHOT-standalone.jar /master-djinn/app.jar
# EXPOSE 8080
# CMD ["java", "-jar", "/master-djinn/app.jar"]



# FROM openjdk:18-alpine
# lein has CMD ["lein", "repl"], override it
# FROM clojure:openjdk-18-lein-2.9.6
FROM clojure:lein AS builder
MAINTAINER Kiba Gateaux perfectloyalty2+masterdjinn@proton.me

ARG API_HOST
ARG ACTIVITYDB_URI
ARG ACTIVITYDB_USER
ARG ACTIVITYDB_PW
ARG IDENTITYDB_URI
ARG IDENTITYDB_USER
ARG IDENTITYDB_PW

ARG SPOTIFY_CLIENT_ID
ARG SPOTIFY_CLIENT_SECRET
# Note that Environ automatically lowercases keys, and replaces the characters "_" and "." with "-". The environment variable DATABASE_URL and the system property database.url are therefore both converted to the same keyword :database-url.

ENV API_HOST=$API_HOST
ENV ACTIVITYDB_URI=$ACTIVITYDB_URI
ENV ACTIVITYDB_USER=$ACTIVITYDB_USER
ENV ACTIVITYDB_PW=$ACTIVITYDB_PW
ENV IDENTITYDB_URI=$IDENTITYDB_URI
ENV IDENTITYDB_USER=$IDENTITYDB_USER
ENV IDENTITYDB_PW=$IDENTITYDB_PW

ENV SPOTIFY_CLIENT_ID=$SPOTIFY_CLIENT_ID
ENV SPOTIFY_CLIENT_SECRET=$SPOTIFY_CLIENT_SECRET

COPY . /usr/src/app
WORKDIR /usr/src/app
# move files from local machine to image
RUN lein uberjar

FROM clojure:lein
EXPOSE 8000
EXPOSE 8080

# RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app-standalone.jar
ARG JAR_FILE="/usr/src/app/target/master-djinn-0.0.1-SNAPSHOT-standalone.jar"
WORKDIR /usr/src/app
COPY --from=builder $JAR_FILE ./app.jar


# https://stackoverflow.com/questions/57885828/netty-cannot-access-class-jdk-internal-misc-unsafe
# "--add-opens java.base/jdk.internal.misc=ALL-UNNAMED -Dio.netty.tryReflectionSetAccessible=true", 
CMD ["java", "-jar", "./app.jar", "--illegal-access=permit", "-Dio.netty.tryReflectionSetAccessible=true"]
