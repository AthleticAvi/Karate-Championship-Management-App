# Karate Championship Management App

A management system for competitive karate, built around **Kumite**, the sparring discipline, where two athletes face each other on the mat and a panel of referees scores the bout in real time.

## What this is

A Kumite bout is short, fast, and unforgiving to officiate. Two competitors, a countdown clock, and referees awarding points and penalties as techniques land. Scores differ in weight depending on the technique and where it lands. Penalties escalate in stages, and the later stages hand points to the opponent or end the bout outright. A match can finish because someone pulled far enough ahead, because penalties ran out, because the clock did, or because a competitor was disqualified or never appeared.

Most of that is still tracked on paper, on whiteboards, or in tools that were never built for it. Mistakes are easy to make and hard to unwind, and the result of a bout is not something anyone wants to reconstruct from memory.

This project is the system of record for that bout. It holds the competitors, the clock, every point and penalty as it happens, and the result, with the rules of the sport encoded rather than left to whoever is holding the pen.

## What it aims to deliver

**A faithful implementation of the rules.** Scoring, penalty escalation, the conditions that end a bout, and how a winner is decided, including the tiebreaks. The rules of karate are specific, and a system that only approximates them is not useful to the people running a tournament.

**Rules that can change without a rewrite.** Governing bodies revise rules, and individual championships adopt variations. Thresholds and conditions are configuration, and new ways for a bout to end can be added without unpicking the ones already there.

**Room for human judgement.** Referees override systems, clocks get stopped a beat late, and edge cases reach the mat that no rulebook anticipated. The system accommodates correction and records that a correction was made, rather than pretending it never happens.

**A real-time view for the people who need it.** Officials, competitors, and spectators are all watching the same bout. The scoreboard should reflect the mat as it happens.

**A championship above the bout.** Individual matches are the foundation. The goal is the tournament around them: brackets, divisions, schedules, and results carried through to a standing.

## Stack

| | |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1.2 |
| Database | MongoDB 8.0, in Docker |
| Build | Maven |
| API description | springdoc-openapi, generated, `dev` only |
| Testing | JUnit, Mockito, AssertJ, Testcontainers |

A frontend will follow once the backend supports it.

## Running locally

Requires JDK 25, Maven, and Docker.

```bash
# Database: a one-member replica set, published to this machine only. Initiate it once.
docker run -d -p 127.0.0.1:27017:27017 --name kumite-mongo mongo:8.0 --replSet rs0
until docker exec kumite-mongo mongosh --quiet --eval "rs.initiate({_id: 'rs0', members: [{_id: 0, host: 'localhost:27017'}]})"; do sleep 1; done

mvn verify            # build and test
mvn spring-boot:run   # run
```

The defaults reach this database. To use another, set `MONGO_URI` and, if needed, `MONGO_DB`: see
`src/main/java/com/management/repositories/README.md`.

## Profiles

The application refuses to start without a profile. A deployment must choose one, so it can never
fall back to local settings.

- **dev**: local development. `mvn spring-boot:run` sets it through `spring-boot.run.profiles` in `pom.xml`, so a clean clone needs no extra steps. Holds the default `MONGO_URI`, which reaches the database above. Allows calls from a local development server, and serves the API description at `/v3/api-docs`.
- **prod**: a deployment. Set `SPRING_PROFILES_ACTIVE=prod` and `MONGO_URI`, which has no default here: without it, startup stops and names it. No browser origin may call it until `CORS_ALLOWED_ORIGINS` names one.
- **test**: activated for the whole test suite via `@ActiveProfiles("test")` on the shared test base classes. It has no file, because nothing differs. `test` is not a profile the application should ever be started under.

A profile with no file of its own, such as a mistyped one, is refused too.

**Running from an IDE:** the main class fails with no profile. Set the active profile to `dev` in its
run configuration, or set the environment variable `SPRING_PROFILES_ACTIVE=dev`.

To run the packaged application:

```bash
SPRING_PROFILES_ACTIVE=prod MONGO_URI=<connection string> java -jar target/karate-championship-0.0.1-SNAPSHOT.jar
```

To run Maven under another profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

`spring-boot:run` always forks a separate JVM, so a plain `-Dspring.profiles.active=dev` is set on
Maven and never reaches the application. Use the plugin's own `-Dspring-boot.run.profiles`. It also
wins over `SPRING_PROFILES_ACTIVE`.

## Documentation

- **[`CONTRIBUTING.md`](CONTRIBUTING.md)**: how to contribute. Read this before opening a pull request.
- **[`context/workflow/`](context/workflow/)**: the engineering knowledge this codebase is held to, organised by concern and technology. Deliberately project-agnostic, so it transfers to other work. Start with its README.
- **Domain READMEs**: project facts, one per package under `src/main/java/com/management/`. The quality gates are in [`config/README.md`](config/README.md).
- **[`CLAUDE.md`](CLAUDE.md)**: the router for AI coding assistants. Project summary, hard rules, and where to find everything else.

Planned work is tracked in [GitHub issues](../../issues), grouped under epics.

## Contributing

Contributions are welcome, and there are rules. Read **[`CONTRIBUTING.md`](CONTRIBUTING.md)** first.

The short version: start from an open issue, comment and wait to be assigned, one issue at a time, include tests, and make sure your pull request description matches what you actually changed. Cosmetic-only changes are not accepted.

Issues labelled `good first issue` are genuinely self-contained and a reasonable place to start.

## Licence

No licence has been applied yet, so default copyright applies and no usage or distribution rights are granted. A licence is being decided. Until then, please open an issue if you want to use this for anything beyond contributing to the project itself.
