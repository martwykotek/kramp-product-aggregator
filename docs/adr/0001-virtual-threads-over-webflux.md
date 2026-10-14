# ADR-0001: Virtual threads and a blocking HTTP client instead of WebFlux

Status: accepted, 2026-10-14 · Decided by: Dawid (decision log D-05, D-17)

## Context
One request fans out to four upstreams that answer in 50–100 ms typically and sometimes hang for
seconds. The fan-out must run in parallel, every call must be bounded in time, and hung calls must be
cancelled. The code has to stay simple enough to explain line by line in an interview.

## Decision
Run each upstream call on its own Java 21 virtual thread (`SimpleAsyncTaskExecutor` with virtual threads)
and use Spring `RestClient` on the JDK `HttpClient` with a read timeout per source. The orchestrator waits
on futures with explicit deadlines and cancels with `Future.cancel(true)`, which interrupts the call.
The correlation id is copied into each thread's MDC by a `TaskDecorator`.

## Consequences
- Imperative code: the orchestration reads top to bottom and stack traces are ordinary.
- A blocked virtual thread costs no platform thread, so the same scalability as reactive code for this
  I/O-bound fan-out.
- On Java 21 a virtual thread blocked inside `synchronized` pins its carrier thread, so the code base has
  no `synchronized` at all (`ReentrantLock` where a lock is needed). Java 25 removes that limitation
  (JEP 491); moving to it is listed as future work.

## Alternatives
- **WebFlux / Reactor**: non-blocking end to end, but every line becomes an operator chain, and
  timeouts, cancellation and MDC propagation need reactive-specific tooling.
- **Platform thread pool**: works, but needs pool sizing and wastes threads on hung upstream calls.
- **`StructuredTaskScope`**: the natural fit, but a preview API in Java 21.
