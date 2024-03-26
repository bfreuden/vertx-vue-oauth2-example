
# Start pre-configured Keycloak

```
cd keycloak
docker compose up -d
cd ..
```
Wait something like 10 seconds, or until `docker compose logs -f` is showing something like: 
```
2024-03-26 13:28:05,084 WARN  [org.keycloak.quarkus.runtime.KeycloakMain] (main) Running the server in development mode. DO NOT use this configuration in production.
```

# Start Vert.x backend
Tested with OpenJDK 11 and Maven 3.8.1

```
mvn compile exec:java
```

# Open the single page app

http://localhost:8000/
