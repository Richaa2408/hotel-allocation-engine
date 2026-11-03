# Hotel Allocation Engine

## Requirements

- Java 21 or newer (JDK 21 LTS recommended)
- No Maven install needed: the included Maven Wrapper downloads it on first run

Confirm the active runtime with `java -version`. If several JDKs are installed, set
`JAVA_HOME` to a Java 21+ JDK before building or running the application.

## Build and test

macOS / Linux:

```shell
./mvnw clean test
```

Windows (PowerShell):

```powershell
.\mvnw.cmd clean test
```

## Run

```powershell
.\mvnw.cmd spring-boot:run
```

The server starts on http://localhost:8080. Stop it with Ctrl + C.
