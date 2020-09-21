FROM openjdk:8-alpine

COPY target/uberjar/kybernetik.jar /kybernetik/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/kybernetik/app.jar"]
