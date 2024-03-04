FROM maven:3.8.3-openjdk-17 as build

# create app folder for sources
RUN mkdir -p /build
WORKDIR /build

#Copy source code
COPY . /build/

# Build application
RUN mvn clean install -DskipTests=true

# Run the jar file
ENTRYPOINT ["mvn", "clean", "verify", "-pl", "server"]