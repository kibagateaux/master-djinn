FROM clojure:lein AS builder
MAINTAINER Kiba Gateaux the_anonymous_hash+masterdjinn@proton.me

COPY . /usr/src/app
WORKDIR /usr/src/app
RUN lein uberjar

FROM clojure:lein
EXPOSE 8000
EXPOSE 8888

# RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app.jar
ARG JAR_FILE="/usr/src/app/target/master-djinn-0.0.1-SNAPSHOT-standalone.jar"
WORKDIR /usr/src/app
COPY --from=builder $JAR_FILE ./app.jar

# https://stackoverflow.com/questions/57885828/netty-cannot-access-class-jdk-internal-misc-unsafe
# "", 
CMD ["java", "--add-opens", "java.base/jdk.internal.misc=ALL-UNNAMED", "--illegal-access=permit", "-Dio.netty.tryReflectionSetAccessible=true", "-jar", "./app.jar"]
