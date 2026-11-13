# Implementation Plan: Lumen AI Integration

## Overview

Implement in Java 21 (Spring Boot, Spring Data JPA, `RestClient`). Build the error types, Lumen error mapping and `LumenChatClient` (instance resolution, session per call, `{"message": ...}` payload) first. Then add the Income record type, the search pieces (`SearchFilter`, `JsonObjectExtractor`, `LumenSearchService`), and the advice pieces (`AdvicePromptBuilder`, `LumenAdviceService`). The Lumen-backed services replace the placeholders. Property tests use jqwik with at least 100 tries each. Client tests use `MockRestServiceServer`; service tests use a mocked `LumenChatClient`.

## Tasks

- [ ] 1. Lumen client foundation
  - [ ] 1.1 Add error types, error handlers and jqwik dependency
    - Create `LumenException` in `com.klaro.service` with `httpStatus`, nullable `lumenStatus`, nullable `lumenCode`, both constructors, getters and static factories (`unreachable()`, `invalidResponse()`, `invalidMessageLength()`, and the config/no-instance failures with 503)
    - Create `BadRequestException` in `com.klaro.service`
    - Add `@ExceptionHandler(LumenException.class)` to `ApiErrorHandler`: respond with the Klaro status and a body with `error`, plus `lumenStatus` and `lumenCode` only when present
    - Add `@ExceptionHandler(BadRequestException.class)` returning 400 with `{"error": message}`
    - Add jqwik as a test-scope dependency with a pinned version in `backend/pom.xml`
    - _Requirements: 4.14, 4.15, 5.10, 5.11_

  - [ ] 1.2 Implement `LumenErrors` status and code mapping
    - Create package-private `LumenErrors.fromResponse(int status, String body)`
    - Read the code from JSON fields `code`, `Code`, `error_code`, `errorCode` and `error.code`; treat it as absent if none parse
    - Map status and code to the messages in the design table; a message names the code when one exists
    - Set `lumenStatus` and `lumenCode` on every exception; Klaro status is the Lumen status for 429, 503 and 504, otherwise 502, even for unknown codes
    - _Requirements: 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 4.11, 4.15_

  - [ ] 1.3 Implement `LumenChatClient` and chat properties
    - Create `LumenChatClient` with `String chat(String prompt)` and `MESSAGE_LIMIT = 4000`, using `RestClient` with a 10s connect timeout and a 30s read timeout
    - Inject `klaro.lumen.chat.base-url` (trim trailing slash), `klaro.lumen.chat.instance-id`, `klaro.lumen.api-key` and `klaro.lumen.api-secret`
    - Set `lumen-api-key` and `lumen-api-secret` on every request through a single `authenticate(HttpHeaders)` method; set no `Authorization` header
    - Before any network call, check credentials (503), chat base URL (503) and prompt not blank and at most 4000 chars (400)
    - `resolveInstanceId`: return the property when not blank without calling `/instances`; otherwise `GET /instances` and use `Instances[0].InstanceID`; an empty list raises the no-instance error (503)
    - `createSession`: `POST .../instances/{id}/sessions` with an empty JSON object (no `title`), read `Session.id`
    - `sendMessage`: `POST .../sessions/{sid}/messages` with `{"message": prompt}`, read `Answer.answer`
    - Keep the session id as a local variable passed down the call chain, never a field
    - Parse with Jackson `JsonNode`; non-JSON or missing `Session.id`, `Answer.answer`, `Instances` or `InstanceID` raises the invalid-response error (502)
    - Convert `RestClientResponseException` using `LumenErrors.fromResponse` and `RestClientException` to the unreachable error (502); rethrow `LumenException` unchanged
    - Log Lumen status, code and exception message at WARN only; never log prompts, answers, keys or secrets
    - Add `klaro.lumen.chat.base-url=${LUMEN_CHAT_BASE_URL:}` and `klaro.lumen.chat.instance-id=${LUMEN_CHAT_INSTANCE_ID:}` to `application.properties` with comments; remove any `klaro.lumen.chat.path` property
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 4.1, 4.2, 4.12, 4.13_

  - [ ]* 1.4 Write property tests for `LumenChatClient` requests
    - Use `MockRestServiceServer` bound to the client's `RestClient.Builder` and record every request
    - **Property 1: Credentials on every request**
    - **Property 2: Configured instance id is used without listing**
    - **Property 3: Blank instance id resolves to the first instance**
    - **Property 4: One new session per call, and the answer is returned**
    - **Validates: Requirements 1.3, 2.1, 2.2, 3.1, 3.3, 3.4, 3.5, 3.6**

  - [ ]* 1.5 Write property tests for client validation and malformed responses
    - **Property 5: Invalid message length is rejected locally**
    - **Property 6: Missing configuration is rejected before any request**
    - **Property 8: Malformed Lumen responses are rejected**
    - **Validates: Requirements 3.7, 4.1, 4.2, 4.13**

  - [ ]* 1.6 Write property test for Lumen error mapping
    - **Property 7: Lumen errors map to status, code and the pass-through rule**
    - Cover documented status and code pairs, unknown codes and absent codes
    - **Validates: Requirements 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 4.11, 4.15**

  - [ ]* 1.7 Write property and unit tests for the error handler and client config
    - **Property 9: Lumen errors become JSON error responses**
    - Unit tests: chat properties are read and `klaro.lumen.chat.path` is gone (1.1, 1.2, 1.4); read timeout is 30s (1.7); empty `Instances` list raises the no-instance error (2.3); session creation omits `title` (3.2); simulated network error and read timeout give "unreachable" (4.12)
    - **Validates: Requirements 4.14**

- [ ] 2. Income records
  - [ ] 2.1 Implement Income entity, repository and controller
    - Create `Income` entity (id, source `@NotBlank`, amount `@NotNull @Positive` precision 12 scale 2, currency length 3 default "PHP", receivedDate `@NotNull`) in the style of `Bill`
    - Create `IncomeRepository extends JpaRepository<Income, Long>`
    - Create `IncomeController` at `/api/income` with GET (list), POST (`@Valid`, returns saved Income) and DELETE (204, or 404 with empty body if missing), following `BillController`
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [ ]* 2.2 Write property tests for Income
    - **Property 16: Income create, list and delete round trip**
    - **Property 17: Invalid Income is rejected and not stored**
    - **Property 18: Deleting an unknown Income returns 404**
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6**

- [ ] 3. Natural-language search
  - [ ] 3.1 Add `SearchFilter`, `JsonObjectExtractor` and specification support
    - Create `SearchFilter` record in `com.klaro.dto` (merchant, startDate, endDate, category as String) with `@JsonIgnoreProperties(ignoreUnknown = true)`
    - Create package-private `JsonObjectExtractor.firstObject(String text, Predicate<JsonNode> accept)`: scan for `{`, find the matching `}` with a depth counter aware of string literals and escapes, parse each balanced candidate, return the first object that parses and is accepted
    - Make `ReceiptRepository` also extend `JpaSpecificationExecutor<Receipt>`
    - _Requirements: 5.2, 5.3, 5.4_

  - [ ]* 3.2 Write property test for filter extraction
    - **Property 12: Filter JSON extraction round trip**
    - Cover empty surrounding text, prose with braces, and code fences with and without a `json` tag; text with no filter object fails with the invalid-response error
    - **Validates: Requirements 5.2, 5.3**

  - [ ] 3.3 Implement `LumenSearchService` and remove `SimpleSearchService`
    - Implement `SearchService` as a `@Service`
    - Null or blank query: throw `BadRequestException("Search query must not be blank.")`; query longer than `MAX_QUERY_LENGTH`: throw `BadRequestException` stating the maximum; neither calls the client
    - Compute `MAX_QUERY_LENGTH = MESSAGE_LIMIT - buildPrompt("").length()` once
    - `buildPrompt(query)`: task instructions, allowed `Category` names, today's ISO date, request for only a JSON object with `merchant`, `startDate`, `endDate`, `category` (`null` when absent), query last, no record data
    - `parseFilter`: use `JsonObjectExtractor` accepting objects with at least one filter key, map to `SearchFilter`; no qualifying object or unmappable value (such as a non-ISO date) throws `LumenException.invalidResponse()`
    - `validate`: non-blank category must match a `Category` (case-insensitive); start date after end date throws invalid response
    - `toSpecification`: merchant contains ignoring case, `receiptDate >= startDate`, `receiptDate <= endDate`, category equals, combined with AND; an empty filter matches all
    - Call `receipts.findAll(spec)`; let `LumenException` propagate with no fallback
    - Delete `SimpleSearchService`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10, 5.11, 5.12, 8.1, 8.3_

  - [ ]* 3.4 Write property tests for search message and rejection
    - **Property 10: Search message is within the limit and carries the query only**
    - **Property 11: Over-long and blank queries are rejected with 400**
    - **Validates: Requirements 5.1, 5.10, 5.11, 5.12**

  - [ ]* 3.5 Write property tests for filtering and validation
    - **Property 13: Search results equal the filter's matching receipts**
    - **Property 14: Category matching against the enum**
    - **Property 15: Inverted date ranges are rejected**
    - Use `@DataJpaTest` or in-memory H2, checking against an in-memory predicate; add unit tests for an unparseable date (5.8) and a no-match search returning an empty list (5.9)
    - **Validates: Requirements 5.4, 5.5, 5.6, 5.7, 5.8, 5.9**

- [ ] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Budgeting advice
  - [ ] 5.1 Implement `AdvicePromptBuilder`
    - Create package-private `AdvicePromptBuilder.build(income, receipts, bills, budgets)`
    - Fixed header with advice instructions (one recommendation per line, plain text, no preamble)
    - Four sections (Income, Receipts, Bills, Budgets), each with a title line, a bounded aggregate line (count and total, or total limit for budgets) and item lines with only the fields advice needs; never include `rawText`; truncate free-text fields to a fixed length in item lines
    - If the full rendering is at most `MESSAGE_LIMIT`, return it; otherwise start from header plus titles and aggregates, sort each collection by date most recent first, add items round-robin across sections one at a time, keeping an item only if the message stays within the limit and stopping a section once an item does not fit
    - Render kept items most recent first within each section
    - _Requirements: 7.2, 7.3, 7.4_

  - [ ]* 5.2 Write property tests for advice message building
    - **Property 19: Advice message never exceeds the limit**
    - **Property 20: Advice message budgeting keeps aggregates and the most recent items**
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.6**

  - [ ] 5.3 Implement `LumenAdviceService` and remove `StubAdviceService`
    - Implement `AdviceService` as a `@Service`; load all Income, Receipts, Bills and Budgets
    - Build one message with `AdvicePromptBuilder` and always send it, including when all collections are empty
    - Parse the answer into `List<String>`: split on newlines, strip bullet and number prefixes (`-`, `*`, `1.`, `1)`), trim, drop empty lines
    - Leave the `AdviceController` path and response type unchanged
    - Delete `StubAdviceService`
    - _Requirements: 7.1, 7.2, 7.5, 7.6, 7.7, 8.2, 8.3_

  - [ ]* 5.4 Write property test for advice parsing
    - **Property 21: Advice text parses to a list of lines**
    - **Validates: Requirements 7.5**

- [ ] 6. Final wiring verification
  - [ ]* 6.1 Write context smoke tests
    - Verify exactly one `SearchService` and one `AdviceService` bean, both Lumen-backed, and that `SimpleSearchService` and `StubAdviceService` no longer exist
    - Verify advice with no data still calls the client and the advice endpoint path and type are unchanged
    - _Requirements: 7.6, 7.7, 8.1, 8.2, 8.3, 4.16_

- [ ] 7. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties (jqwik, at least 100 tries each)
- Unit tests validate specific examples and edge cases

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1", "3.1"] },
    { "id": 2, "tasks": ["1.3", "1.6", "2.2", "3.2", "5.1"] },
    { "id": 3, "tasks": ["1.4", "1.7", "3.3", "5.2", "5.3"] },
    { "id": 4, "tasks": ["1.5", "3.4", "3.5", "5.4"] },
    { "id": 5, "tasks": ["6.1"] }
  ]
}
```
