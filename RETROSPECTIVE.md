# Retrospective

This document is an honest post-mortem on the submission — what I prioritized, why, and where I'd invest differently with another pass.

## What I concentrated on

**Core correctness.** The assignment's stated problem was a real pagination bug: dedup happening after the page fetch, which caused the cursor to encode a position in the de-duplicated result rather than the raw data, producing overlaps and inflated totals. I fixed this by deduplicating once at startup and paginating over the clean set — the cursor then always encodes an unambiguous position. The three correctness assertions in the test suite (`paginatedTotalMatchesNonPaginatedTotal`, `noDuplicateIdsAcrossPages`, `noMissingIdsAcrossPages`) were the primary acceptance criteria I designed toward.

**Architecture and extensibility.** The `EventStore` interface, cursor format, and binary search approach were deliberate design choices I can defend: the interface exists so a `JdbcEventStore` would require zero changes to the controller or tests; the `(startTime, id)` cursor tuple is unique in the deduplicated view, which is the property that makes pagination unambiguous; the binary search mirrors a B-tree index on `(start_time, id)`. These aren't incidental — they reflect how I'd design a system meant to grow.

**Process discipline.** I structured the work as I would on a real team: GitHub issues for each milestone, feature branches, PRs reviewed by CodeRabbit before merging, CI on every push. The branching history is visible in the repo. This was intentional — I wanted to show not just what was built but how.

**AI-assisted development, directed and reviewed.** I used Claude Code throughout. My role was architecture, design decisions, code review, and process enforcement. Claude handled implementation under direction. Every PR went through CodeRabbit review; real findings (an NPE on null direction, a reference equality bug, an O(n) scan documented as O(log n)) were caught and fixed — not dismissed. I disclosed this in the README because I think transparency about how the work was done is more useful signal than pretending it wasn't.

## What I'd do differently

**The validation layer was thin.** I relied on Spring's built-in type enforcement — it gives you a 400 for the wrong type on a required param, and that's about it. I should have built out the full layer:

- `start_time > end_time` currently returns HTTP 200 with an empty event array. That's wrong — a client can't distinguish "no events in this range" from "your range is inverted." It should be a 400.
- A `@ControllerAdvice` + `@ExceptionHandler` would normalize error response shapes across type mismatches, missing params, and application-level errors. Without it, different failure modes return different bodies.
- `@Validated` + Bean Validation annotations (`@Min`, `@Max`, `@Size`) on request params is the idiomatic Spring approach. Manual clamping on `limit` silently accepts negative values and returns 1 with no feedback to the caller.
- The cursor has no integrity check. The docs warn that a cursor is only valid with the same query parameters that produced it, but there's no enforcement. Embedding the sort direction in the cursor and validating on decode would catch misuse; signing with HMAC would catch tampering.

In practice I de-prioritized this layer to invest time in the process demonstration and extras. In retrospect, for a staff-level assessment, validation hardening is core correctness — not a nice-to-have — and the extras signaled breadth over depth.
