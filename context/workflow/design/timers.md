# Timers

Applies when: countdown, timer, clock, time remaining, elapsed time, pause and resume, time up, live display of time, clock skew
Status: enforced

## Index

- [no-tick] The server does not tick
- [client-time] The time the person saw is the fact
- [display] Every display counts locally
- [units] Units
- [verify] Verify
- [sources] Sources

## [no-tick] The server does not tick

- **Never store a running counter.** A timer's state is "running since instant X with Y left" or
  "paused with Y left". Remaining time is derived: Y minus (now minus X) while running.
- **Store one of two things, never both:** that state alone, or the actions (start, pause, add,
  set) with the state derived by folding them. Keep the actions when the history matters. A
  stored state beside the action history is a second copy: see `[one-fact-one-owner]` in
  `design/concurrency-consistency.md`.
- **Derived state survives a restart.** Nothing is lost when the server or a client restarts,
  because nothing was counting in memory.
- **Do not rely on a scheduled job firing at zero** for correctness. A restart loses it. Learn of
  zero from a client report, or derive it on the next read or write.
- **A final state stops its timers in the fold.** When a state refuses every later timer action,
  a timer running as it is entered can never be paused, and the derived time counts down for
  ever. Stop each timer at the instant of the move into it.

## [client-time] The time the person saw is the fact

- **Record the value the client showed when the person acted,** not the server's receive time.
  Network delay shifts the receive time by an unknown amount; the person reacted to the display.
- **Check it against the derived value** and warn when the two differ by more than a configured
  tolerance. Do not silently replace either.

## [display] Every display counts locally

- **Clients count down on their own** from the last state the server returned.
- **Send the server's current time in every response.** The client computes its clock offset from
  it and re-syncs on each answer, so its display does not drift from the server's.
- **A polled timer with a conditional read re-syncs on a change, not on every answer.** A 304 has
  no body, and a browser that revalidates from its own cache hands code a 200 with the body it
  kept: the old remaining time and the old server time. A client that restarts from each answer
  jumps back on every poll. Count down by the time elapsed since the answer whose tag it holds,
  on a monotonic clock, never by counting timer ticks, which a browser slows in a hidden tab.
  Start again only when the tag changes.
- **A body kept by a cache is older than its arrival.** After a reload or in a new tab, the first
  answer can be a body the browser kept, minutes old. Anchor it at its arrival less its age, the
  response's date header minus the server time in the body, never below zero. Counting from the
  arrival alone shows that much too much time left.
- **A 304 carries the server's time only in `Date`,** to the second, and a proxy may replace it.
  State that in the client contract when a client needs the server's time between changes.
- **Return the recorded state after every action,** and have the client show that, not its own
  guess.

## [units] Units

- **Whole milliseconds and UTC instants.** Finer values are lost on a store that keeps
  milliseconds, and a reload then differs from memory with no error. Look for the datastore's
  rule on dates.
- **Name the unit in every field** that holds a duration.

## [verify] Verify

- A test restarts the timer's owner mid-countdown and derives the same remaining time.
- Where actions are stored, no state field is stored beside them.
- No scheduled job decides when a timer ends.

## [sources] Sources

- Synthesis: no standard covers this. The pattern of a server-authoritative end time, a client
  clock offset from server time and periodic re-sync appears across timer libraries and
  clock-skew write-ups; the HTTP `Date` header gives server time but may come from a cache.
