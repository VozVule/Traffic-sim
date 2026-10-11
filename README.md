# Traffic simulator

Kotlin/JVM project using Maven. The project currently contains a minimal console entry point; simulation behavior will be added in later tasks.

## Requirements

Use JDK 21. The included Maven wrapper downloads Maven 3.9.15 on first use; a separate Maven installation is unnecessary. Dependency downloads require internet access.

On macOS, select your installed JDK 21 if needed:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## Compile

```sh
./mvnw clean compile
```

## Run

```sh
./mvnw compile exec:java
```

The entry point is `src/main/kotlin/dev/trafficsimulator/Main.kt`. Open `pom.xml` to import the project into an IDE. On Windows, use `mvnw.cmd` in place of `./mvnw`.
