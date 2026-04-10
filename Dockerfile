# ==========================================
# STAGE 1: Build the Application
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy only the pom.xml first to cache the dependency downloads
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the actual code and compile it
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# STAGE 2: Create the Production Image
# ==========================================
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the compiled .jar file from the 'build' stage above
COPY --from=build /app/target/*.jar app.jar

# Expose the standard Spring Boot port
EXPOSE 8080

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]