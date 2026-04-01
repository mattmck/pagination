# Take-Home: Events API with Cursor-Based Pagination

**Role:** Backend Java Developer
**Focus:** Pagination correctness, API design  
**Tech:** Your choice of framework and persistence (in-memory from CSV is fine). **Spring is fine.** **Do not use Guava.** You make all other technical decisions.

For more detail on what we expect (API type, tests, documentation), see [Expectations](EXPECTATIONS.md).

---

## The Setup

You are taking over an **Events API**. Your task is to build a **single remote endpoint** (e.g. REST over HTTP) that returns events for a **date range** using **cursor-based pagination**. The deliverable is a **callable HTTP API**, not an in-process Java interface—clients should be able to hit the endpoint with a tool like `curl` or a REST client.

- **Input:** Start date, end date (for the range), optional `limit`, optional `cursor`.
- **Output:** A page of events plus a `next_cursor` when more results exist. Results are ordered by `start_time` (ascending) with a stable tie-breaker (e.g. `id` or `row_index`).
- You may use any framework (Spring, etc.) or database. Loading the provided CSV into memory is acceptable.

The repo includes `sample_data.csv` — treat it as the raw “database” rows. Ingest it however you like (e.g., at startup or on first request).

---

## The Scenario (The Bug)

The previous engineer implemented pagination with the following logic:

1. Fetch `limit + 1` rows from the data store (ordered by `start_time`, then by a tie-breaker such as `id` or `row_index`).
2. **De-duplicate by `id` in memory** (e.g., keep first occurrence per `id`).
3. Take the **first `limit` items** from the de-duplicated list.
4. Set **`next_cursor`** from the **last item** in this de-duplicated return array (e.g., encode `start_time` and `id` of that last item).

The intent was to avoid duplicates in the API response while still using a “fetch one extra row” pattern to detect if there is a next page.

---

## The Symptoms

Clients are reporting:

- **Overlapping events** across pages: the same logical event (same `id`) sometimes appears on more than one page.
- **Higher total event counts** when paginating through all pages than when fetching all records in a single request (e.g., with a very large limit or no pagination).

---

## The Assignment

### 1. Implementation

Build the API endpoint from scratch with **correct cursor derivation** so that:

- Each logical event (by `id`) appears **at most once** across all pages.
- The **total number of distinct events** returned when paginating through all pages equals the total number of distinct events in the date range (no inflation, no missing IDs).
- Cursor semantics are stable and unambiguous (e.g., “start after this position” or “start at this position” clearly defined).

You own the choice of cursor format (opaque string, base64-encoded tuple, etc.) and the exact request/response shape.

---

### 2. Testing

Write **unit and/or integration tests** that prove pagination is correct. Unit tests (e.g. testing your pagination/cursor logic in isolation) and integration tests (e.g. calling the HTTP endpoint) are both acceptable—use whatever mix best demonstrates correctness. The test suite must be runnable without manual steps (e.g. via your build tool). At minimum, your tests must verify:

- **Totals match:** The sum of distinct event IDs over all paginated pages equals the total distinct event count for the same date range when fetched without pagination (or with a single “get all” call).
- **No duplicates:** No event `id` appears more than once across all pages for a given date range and limit.
- **No missing IDs:** Every distinct event in the date range appears in exactly one page (no events skipped).

Feel free to add more tests (e.g., empty range, single page, cursor stability, boundary times).

---

### 3. API Contract

Document how clients should consume this API:

- Request parameters (e.g., `start_time`, `end_time`, `limit`, `cursor`).
- Response shape (e.g., `events`, `next_cursor`, and when `next_cursor` is absent or null).
- How to use `next_cursor` to request the next page and how to know when pagination is complete.

Put this in your README or a dedicated API doc so that an integration engineer could implement a client without reading your source code.

---

## Extras (optional)

If you have time and want to go further:

- **Sorting** — Support a configurable sort order (e.g. `order_by=start_time` with `asc` / `desc`). The cursor must be derived from the same ordering used for the query; document how sort parameters affect the cursor.
- **Filters** — Support at least one simple filter (e.g. by `id` prefix, or `payload` contains a substring). Ensure paginated totals and “get all” totals stay consistent when the filter is applied.

These are not required. The core assignment is date range + cursor-based pagination with correct cursor logic.

---

## Deliverables Checklist

- [ ] **Working API** — One **HTTP** endpoint: events for date range with cursor-based pagination and correct cursor logic (callable via REST/client).
- [ ] **Tests** — Unit and/or integration tests, runnable via your build tool, that prove paginated totals match non-paginated totals with no missing or duplicate IDs.
- [ ] **API contract** — Clear docs for request/response and cursor usage.
- [ ] **Javadoc** — Proper Javadoc for all public classes and public methods.
- [ ] **Setup/run instructions** — How to install dependencies, load data, run the server, and run tests (so we can run your solution locally).

---

## Data: `sample_data.csv`

- **Columns:** `row_index`, `start_time` (Unix timestamp), `id`, `payload`.
- **Note:** The dataset intentionally contains **duplicate rows** (same `start_time` and `id`, different `row_index`) to exercise de-duplication and cursor logic. Your implementation must handle these correctly so that each logical event appears once and cursor boundaries are unambiguous.

Good luck. We’re interested in your reasoning, code structure, and tests as much as in a working endpoint.
