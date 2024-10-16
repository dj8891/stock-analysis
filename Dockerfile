# Use a base image that includes JDK 17
FROM openjdk:23-jdk-slim

# Create a directory for the application
WORKDIR /app

# Copy the built Spring Boot JAR file into the container
COPY /stock_analysis-0.0.1-SNAPSHOT.jar /app/stock_analysis.jar

# Set the entry point to run the JAR file
ENTRYPOINT ["java", "-jar", "/app/stock_analysis.jar"]