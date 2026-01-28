# 1. Base Image (Java 21)
FROM openjdk:21-jdk-slim

# 2. Argument for JAR file path
ARG JAR_FILE=build/libs/*.jar

# 3. Copy the JAR file to the container
COPY ${JAR_FILE} app.jar

# 4. Set Environment Variables (Optional defaults)
ENV SPRING_PROFILES_ACTIVE=prod

# 5. Run the application
ENTRYPOINT ["java", "-jar", "/app.jar"]
