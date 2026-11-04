# enums

## Index

- [purpose] Purpose
- [owns] Owns
- [transitions] Valid state transitions
- [deviations] Known vocabulary deviations

## [purpose] Purpose

The domain vocabulary. WKF Kumite terms are authoritative.

## [owns] Owns

| Concept | Values |
|---|---|
| Player colours | AKA = RED, AO = BLUE. Exactly one of each per game |
| Scoring | IPPON = 3, WAZA-ARI = 2, YUKO = 1 |
| Game states | QUEUED, RUNNING, PAUSED, FINISHED |
| Foul stages | CHUI1, CHUI2, CHUI3, HANSOKU_CHUI, HANSOKU, SHIKKAKU, derived from the count |
| Match end reasons | POINTS_THRESHOLD, FOUL_LIMIT, TIME_EXPIRED, DISQUALIFICATION, KIKEN, REFEREE_OVERRIDE |

`PointsType` carries its `PointStrategy` instance.

## [transitions] Valid state transitions

Anything not in this table is illegal.

| From | To |
|---|---|
| QUEUED | RUNNING |
| RUNNING | PAUSED, FINISHED |
| PAUSED | RUNNING, FINISHED |

The table lives as data on `GameState` (`canTransitionTo`). Each lifecycle method in
`KumiteGameService` names its allowed starting states (start: QUEUED; pause: RUNNING; resume:
PAUSED; end: RUNNING or PAUSED). The verbs are narrower than the state machine, since only a paused
match can be *resumed*. An illegal call surfaces as HTTP 409 via `IllegalStateTransitionException`.

## [deviations] Known vocabulary deviations

Each has an issue.

- `PointsType.WAZARI` is missing the hyphen of the WKF's **WAZA-ARI**. It is public API surface:
  clients send `pointType=WAZARI`. It is the last spelling out of step with the rulebook.
- `PlayerColor` has only `RED` and `BLUE`. The AKA and AO names appear nowhere in code.
- `FoulTypes` is derived from the count (`FoulTypes.forCount`). With the default limit of 4 the
  match ends at `HANSOKU_CHUI`, so `HANSOKU`'s point award and `SHIKKAKU`'s disqualification are
  only reached if the limit is raised. "CHUI up to 3, then HANSOKU CHUI" and "4 fouls ends the
  match" do not both fit one progression. Both are configuration rather than picked between in
  code. **Worth an explicit decision.**
