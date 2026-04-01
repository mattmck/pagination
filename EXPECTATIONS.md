# Expectations & Clarifications

This document clarifies what we expect from your submission. It is referenced from the main [README](README.md).

---

## API type

The deliverable is a **remote API** that clients call over the network, not an in-process Java API. Concretely:

- Build a **single HTTP endpoint** (e.g. REST). Clients should be able to call it with `curl`, Postman, or any HTTP client.
- We are **not** asking for only a Java interface or a class that another service calls in-process. The endpoint must be runnable (e.g. start a server) and callable over HTTP.

---

## Tests: unit vs integration

Your tests should run via your build tool (e.g. Maven `mvn test`, Gradle `test`) with no manual steps. We do not prescribe unit-only or integration-only:

- **Unit tests** — Testing your pagination/cursor logic (e.g. a service or helper) in isolation, with the data layer stubbed or in-memory, is fine and often quick to run.
- **Integration tests** — Testing the full stack by issuing HTTP requests to your endpoint and asserting on the response are also fine and demonstrate end-to-end correctness.

Use whichever mix best proves that pagination is correct (totals match, no duplicates, no missing IDs). Both are valued.

---

## Javadoc

Include **proper Javadoc for all public classes and all public methods**. We use this to assess how you document APIs and code design. Describe purpose, parameters, return values, and any notable behavior or constraints.

---

## Libraries and frameworks

- **Spring** — Allowed. Use it if you prefer.
- **Guava** — **Not allowed.** Do not add Guava as a dependency or use it in your solution. Use the JDK and other libraries instead.

All other technical choices (build tool, test framework, in-memory vs DB, etc.) are up to you.
