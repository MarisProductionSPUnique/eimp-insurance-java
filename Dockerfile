FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN printf '%s\n' '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"><mirrors><mirror><id>google-central</id><mirrorOf>central</mirrorOf><url>https://maven-central.storage-download.googleapis.com/maven2/</url></mirror></mirrors></settings>' > /tmp/maven-settings.xml \
 && mvn -B -s /tmp/maven-settings.xml clean package
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/eimp-insurance-0.0.1-SNAPSHOT.jar app.jar
RUN groupadd -r eimp && useradd -r -g eimp eimp
USER eimp
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
