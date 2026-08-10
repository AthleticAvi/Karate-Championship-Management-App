# Karate Championship Management App

A management system for competitive karate, built around **Kumite** — the sparring discipline, where two athletes face each other on the mat and a panel of referees scores the bout in real time.

## What this is

A Kumite bout is short, fast, and unforgiving to officiate. Two competitors, a countdown clock, and referees awarding points and penalties as techniques land. Scores differ in weight depending on the technique and where it lands. Penalties escalate in stages, and the later stages hand points to the opponent or end the bout outright. A match can finish because someone pulled far enough ahead, because penalties ran out, because the clock did, or because a competitor was disqualified or never appeared.

Most of that is still tracked on paper, on whiteboards, or in tools that were never built for it. Mistakes are easy to make and hard to unwind, and the result of a bout is not something anyone wants to reconstruct from memory.

This project is the system of record for that bout. It holds the competitors, the clock, every point and penalty as it happens, and the result — with the rules of the sport encoded rather than left to whoever is holding the pen.

## What it aims to deliver

**A faithful implementation of the rules.** Scoring, penalty escalation, the conditions that end a bout, and how a winner is decided — including the tiebreaks. The rules of karate are specific, and a system that only approximates them is not useful to the people running a tournament.

**Rules that can change without a rewrite.** Governing bodies revise rules, and individual championships adopt variations. Thresholds and conditions are configuration, and new ways for a bout to end can be added without unpicking the ones already there.

**Room for human judgement.** Referees override systems, clocks get stopped a beat late, and edge cases reach the mat that no rulebook anticipated. The system accommodates correction and records that a correction was made, rather than pretending it never happens.

**A real-time view for the people who need it.** Officials, competitors, and spectators are all watching the same bout. The scoreboard should reflect the mat as it happens.

**A championship above the bout.** Individual matches are the foundation. The goal is the tournament around them — brackets, divisions, schedules, and results carried through to a standing.

## Stack

| | |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.3 |
| Database | MongoDB, in Docker |
| Build | Maven |
| Testing | JUnit 5, Mockito, AssertJ |

A frontend will follow once the backend supports it.

## Running locally

Requires a JDK, Maven, and Docker. A step-by-step Windows setup guide is included as `karate-app-dev-setup-windows.pdf`.

```bash
docker run -d -p 27017:27017 --name kumite-mongo mongo:7   # database
mvn verify                                                  # build and test
mvn spring-boot:run                                         # run
```

## Documentation

- **[`CONTRIBUTING.md`](CONTRIBUTING.md)** — how to contribute. Read this before opening a pull request.
- **[`workflow/`](workflow/)** — the engineering standards and patterns this codebase is held to. Deliberately project-agnostic, so it transfers to other Spring Boot work.
- **[`CLAUDE.md`](CLAUDE.md)** — project summary, hard rules, and settled decisions. Written as context for AI coding assistants, and the shortest accurate description of the project's constraints.

Planned work is tracked in [GitHub issues](../../issues), grouped under epics.

## Contributing

Contributions are welcome, and there are rules — read **[`CONTRIBUTING.md`](CONTRIBUTING.md)** first.

The short version: start from an open issue, comment and wait to be assigned, one issue at a time, include tests, and make sure your pull request description matches what you actually changed. Cosmetic-only changes are not accepted.

Issues labelled `good first issue` are genuinely self-contained and a reasonable place to start.

## Licence

No licence has been applied yet, so default copyright applies and no usage or distribution rights are granted. A licence is being decided — until then, please open an issue if you want to use this for anything beyond contributing to the project itself.
