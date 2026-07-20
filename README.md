# CampKin Backend

Spring Boot API for CampKin room and discussion-group assignments.

## Requirements

- Java 21
- Maven 3.9+
- PostgreSQL

## Configure

Set these environment variables before starting:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/campkin
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_password
APP_CORS_ORIGINS=http://localhost:5173
```

Create an empty PostgreSQL database named `campkin`. Flyway creates and updates the tables automatically.

## Run

```powershell
mvn spring-boot:run
```

The API runs at `http://localhost:8080/api`.

## Test

```powershell
mvn test
```

## Main capabilities

- Camp, room, camper, preference, and discussion-group persistence
- CSV, XLSX, and XLS camper imports
- Airtable Team view CSV support
- Roommate-name matching and review
- Room and discussion-group generation
- Manual assignments and gender review
- PDF exports
