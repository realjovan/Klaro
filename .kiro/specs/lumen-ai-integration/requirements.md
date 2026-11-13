# Requirements Document

## Introduction

This feature integrates the Lumen AI Chat API into the Klaro backend. It provides two capabilities: natural-language record search, where Lumen converts a query into structured filters (merchant, date range, category) that the Klaro backend runs against its own database, and budgeting advice, where Lumen produces advice from the user's income, receipts, bills and budgets. A new Income record type supplies the income data.

The Lumen AI Chat API is available on test and staging gateways only (not on live). Its base URL is the Lumen gateway origin followed by `/ai_chat`, for example `https://api.staging.nonlive.lumen.bayanichain.tech/ai_chat`. Authentication uses the `lumen-api-key` and `lumen-api-secret` headers; the key must hold the AI Chat grant. The API flow is: list instances (`GET /instances`), create a session (`POST /instances/{id}/sessions`), then send a message (`POST /instances/{id}/sessions/{sessionID}/messages`). The message route accepts a single `message` string and has no system prompt or role field, so instructions and data are embedded in that one message, which is limited to 4000 characters. The model reply is free text, so structured output must be extracted from it.

All Lumen communication is isolated in a single client class. Each search or advice call opens its own new session, so the Klaro_Backend keeps no session state and never reuses a stale or foreign session (a session is owned by the creating key and bound to one instance). When Lumen is unavailable, unconfigured or returns an error, the backend returns a clear error that includes the Lumen error code and does not fall back to the placeholder services.

## Glossary

- **Klaro_Backend**: The Spring Boot application in the `backend` directory.
- **Lumen_Chat_Client**: The single class that builds requests to, sends requests to, and parses responses from the Lumen_Chat_API.
- **Lumen_Chat_API**: The external Lumen AI Chat HTTP service located at the configured chat base URL.
- **Chat_Base_URL**: The value of `klaro.lumen.chat.base-url`, being the Lumen gateway origin followed by `/ai_chat`.
- **Instance**: A Lumen AI Chat instance, returned by `GET {Chat_Base_URL}/instances` as an object with `InstanceID`, `Name`, `Description` and `Status`.
- **Instance_ID**: The identifier of the Instance used for chat, configured by `klaro.lumen.chat.instance-id`.
- **Session**: A Lumen AI Chat conversation created by `POST {Chat_Base_URL}/instances/{Instance_ID}/sessions`, owned by the creating API key and bound to one Instance.
- **Answer_Text**: The `Answer.answer` string in the response of the Lumen_Chat_API message route.
- **Lumen_Error_Code**: The error code string returned by the Lumen_Chat_API in an error response, for example `ACCESS_DENIED` or `AI_CHAT_REPLY_TIMEOUT`.
- **Search_Service**: The Klaro_Backend component that handles natural-language record queries.
- **Advice_Service**: The Klaro_Backend component that produces budgeting advice.
- **Search_Filter**: A structured JSON object with optional merchant, start date, end date and category fields.
- **Income**: A record of money received, with a source, an amount and a date.
- **Income_Endpoint**: The REST controller endpoint that manages Income records.
- **Record**: A Receipt stored in the Klaro_Backend database.
- **Lumen_Error**: An HTTP error response from the Klaro_Backend with a status code, a message describing why Lumen could not be used, the Lumen HTTP status where one was received, and the Lumen_Error_Code where one was received.
- **Message_Limit**: The maximum message length of 4000 characters accepted by the Lumen_Chat_API message route.
- **Placeholder_Services**: SimpleSearchService and StubAdviceService.

## Requirements

### Requirement 1: Lumen Chat Client Configuration and Authentication

**User Story:** As a developer, I want Lumen chat access isolated in one client class with configurable settings, so that API differences can be fixed in one place.

#### Acceptance Criteria

1. THE Lumen_Chat_Client SHALL read the Chat_Base_URL from the property `klaro.lumen.chat.base-url`, separate from `klaro.lumen.base-url`.
2. THE Lumen_Chat_Client SHALL read credentials from the existing properties `klaro.lumen.api-key` and `klaro.lumen.api-secret`.
3. THE Lumen_Chat_Client SHALL send the credentials on every request in the HTTP headers `lumen-api-key` and `lumen-api-secret` and SHALL use no Bearer or OAuth authentication.
4. THE Lumen_Chat_Client SHALL read the Instance_ID from the property `klaro.lumen.chat.instance-id`.
5. THE Lumen_Chat_Client SHALL build all request payloads and parse all response payloads within the Lumen_Chat_Client class only.
6. THE Lumen_Chat_Client SHALL obtain credentials only from environment-backed properties and not from committed source or properties values.
7. THE Lumen_Chat_Client SHALL use a read timeout of 30 seconds for requests to the Lumen_Chat_API.

### Requirement 2: Instance Resolution

**User Story:** As a developer, I want the chat instance chosen predictably, so that requests go to the intended Lumen AI Chat instance.

#### Acceptance Criteria

1. WHERE `klaro.lumen.chat.instance-id` is not blank, THE Lumen_Chat_Client SHALL use that value as the Instance_ID without calling `GET {Chat_Base_URL}/instances`.
2. WHILE `klaro.lumen.chat.instance-id` is blank, THE Lumen_Chat_Client SHALL call `GET {Chat_Base_URL}/instances` and use the `InstanceID` of the first Instance in the returned `Instances` list as the Instance_ID.
3. IF `klaro.lumen.chat.instance-id` is blank and the `Instances` list is empty, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error indicating that no Lumen chat instance is available.

### Requirement 3: Sessions and Messages

**User Story:** As a developer, I want each AI call to use its own session, so that the backend stays stateless and never reuses a stale or foreign session.

#### Acceptance Criteria

1. WHEN a prompt is submitted, THE Lumen_Chat_Client SHALL create a new Session by sending `POST {Chat_Base_URL}/instances/{Instance_ID}/sessions` and read the session identifier from `Session.id` in the 201 response.
2. WHEN the Lumen_Chat_Client creates a Session, THE Lumen_Chat_Client SHALL omit the optional `title` field or send a `title` of 1 to 200 characters.
3. WHEN a Session has been created, THE Lumen_Chat_Client SHALL send the prompt by `POST {Chat_Base_URL}/instances/{Instance_ID}/sessions/{sessionID}/messages` with a JSON body containing the field `message`.
4. WHEN the message route returns HTTP 200, THE Lumen_Chat_Client SHALL return the Answer_Text.
5. THE Lumen_Chat_Client SHALL create exactly one Session for each search call and exactly one Session for each advice call.
6. THE Lumen_Chat_Client SHALL discard the Session identifier after the Answer_Text has been returned or an error has been raised.
7. IF a prompt is blank or longer than the Message_Limit, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error indicating an invalid message length without calling the Lumen_Chat_API.

### Requirement 4: Lumen Error Handling

**User Story:** As a user, I want clear errors when Lumen cannot be used, so that I know why the AI feature did not work.

#### Acceptance Criteria

1. IF `klaro.lumen.api-key` or `klaro.lumen.api-secret` is empty, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error indicating that Lumen credentials are not configured.
2. IF `klaro.lumen.chat.base-url` is blank, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error indicating that the Lumen chat base URL is not configured.
3. IF the Lumen_Chat_API returns HTTP 403 with Lumen_Error_Code `ACCESS_DENIED` or `VIEW_ONLY_ACCESS`, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error indicating that the API key lacks the AI Chat grant and including the Lumen_Error_Code.
4. IF the Lumen_Chat_API returns HTTP 403 with Lumen_Error_Code `API_CREDITS_INSUFFICIENT`, `LC_INSUFFICIENT` or `SESSION_NOT_OWNED`, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error describing that condition and including the Lumen_Error_Code.
5. IF the Lumen_Chat_API returns HTTP 404, including Lumen_Error_Code `SESSION_NOT_FOR_INSTANCE`, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error indicating an unknown instance or session and including the Lumen_Error_Code where one was returned.
6. IF the Lumen_Chat_API returns HTTP 409 with Lumen_Error_Code `AI_CHAT_CONFLICT`, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error indicating a chat conflict and including the Lumen_Error_Code.
7. IF the Lumen_Chat_API returns HTTP 429 with Lumen_Error_Code `AI_CHAT_MODEL_RATE_LIMITED`, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error indicating that the model is rate limited and including the Lumen_Error_Code.
8. IF the Lumen_Chat_API returns HTTP 502 with Lumen_Error_Code `AI_CHAT_MODEL_UPSTREAM_ERROR`, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error indicating a model upstream error and including the Lumen_Error_Code.
9. IF the Lumen_Chat_API returns HTTP 503 with Lumen_Error_Code `AI_CHAT_MODEL_UNAVAILABLE`, `AI_CHAT_NOT_CONFIGURED`, `FEATURE_DISABLED` or `PERMISSIONS_UPDATING`, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error indicating that Lumen AI Chat is unavailable and including the Lumen_Error_Code.
10. IF the Lumen_Chat_API returns HTTP 504 with Lumen_Error_Code `AI_CHAT_REPLY_TIMEOUT`, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error indicating a reply timeout and including the Lumen_Error_Code.
11. IF the Lumen_Chat_API returns any other non-success HTTP status, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error that includes the HTTP status and the Lumen_Error_Code where one was returned.
12. IF a request to the Lumen_Chat_API fails with a network error or exceeds the read timeout, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error indicating that Lumen is unreachable.
13. IF a response from the Lumen_Chat_API cannot be parsed or lacks the expected `Session.id` or `Answer.answer` field, THEN THE Lumen_Chat_Client SHALL raise a Lumen_Error indicating an invalid Lumen response.
14. WHEN a Lumen_Error is raised, THE Klaro_Backend SHALL return an HTTP error response with a JSON body containing an error message, the Lumen HTTP status where one was received, and the Lumen_Error_Code where one was received.
15. WHEN a Lumen_Error is raised for a Lumen HTTP status of 429, 503 or 504, THE Klaro_Backend SHALL return the same HTTP status to the caller; WHEN a Lumen_Error is raised for any other Lumen failure, THE Klaro_Backend SHALL return HTTP 502, except where another requirement specifies an HTTP status.
16. WHEN a Lumen_Error is raised, THE Klaro_Backend SHALL return the error without invoking the Placeholder_Services.

### Requirement 5: Natural-Language Search

**User Story:** As a user, I want to search my records in plain language, so that I can find receipts without choosing filters manually.

#### Acceptance Criteria

1. WHEN a natural-language query is received, THE Search_Service SHALL send one message to the Lumen_Chat_Client containing instructions to return a Search_Filter as JSON with merchant, date range and category fields, followed by the query, within the Message_Limit.
2. WHEN the Lumen_Chat_Client returns the Answer_Text, THE Search_Service SHALL extract the Search_Filter JSON object from the Answer_Text, tolerating surrounding text and Markdown code fences.
3. IF the Answer_Text contains no JSON object that parses as a Search_Filter, THEN THE Search_Service SHALL return a Lumen_Error indicating an invalid Lumen response.
4. WHEN the Search_Service has a Search_Filter, THE Search_Service SHALL query the Klaro_Backend database using the merchant, date range and category fields that are present.
5. WHEN a Search_Filter contains a category, THE Search_Service SHALL match the category against the defined Category values.
6. IF the Search_Filter contains a category that is not a defined Category value, THEN THE Search_Service SHALL return a Lumen_Error indicating an invalid Lumen response.
7. IF the Search_Filter contains a start date later than the end date, THEN THE Search_Service SHALL return a Lumen_Error indicating an invalid Lumen response.
8. IF a date field in the Search_Filter is not a valid ISO-8601 date, THEN THE Search_Service SHALL return a Lumen_Error indicating an invalid Lumen response.
9. WHEN the query matches no Records, THE Search_Service SHALL return an empty list.
10. IF the query is blank, THEN THE Search_Service SHALL return an HTTP 400 response with an error message.
11. IF the query is too long for the instructions and the query to fit within the Message_Limit, THEN THE Search_Service SHALL return an HTTP 400 response with an error message stating the maximum query length.
12. THE Search_Service SHALL execute the database query within the Klaro_Backend and send no Record data to the Lumen_Chat_API during search.

### Requirement 6: Income Records

**User Story:** As a user, I want to record my income, so that budgeting advice can account for it.

#### Acceptance Criteria

1. THE Klaro_Backend SHALL store each Income with an identifier, a source, an amount and a received date.
2. WHEN a valid Income is submitted to the Income_Endpoint, THE Klaro_Backend SHALL persist the Income and return it with its identifier.
3. WHEN a list request is sent to the Income_Endpoint, THE Klaro_Backend SHALL return all stored Income records.
4. WHEN a delete request for an existing Income identifier is sent to the Income_Endpoint, THE Klaro_Backend SHALL remove that Income.
5. IF a submitted Income has a missing source, a missing date or an amount that is not greater than zero, THEN THE Klaro_Backend SHALL return an HTTP 400 response with an error message.
6. IF a delete request references an Income identifier that does not exist, THEN THE Klaro_Backend SHALL return an HTTP 404 response.

### Requirement 7: Budgeting Advice

**User Story:** As a user, I want budgeting advice based on my finances, so that I can adjust my spending.

#### Acceptance Criteria

1. WHEN an advice request is received, THE Advice_Service SHALL collect all Income records, Receipts, Bills and Budgets from the Klaro_Backend database.
2. WHEN the Advice_Service has collected the data, THE Advice_Service SHALL build a single message containing the advice instructions and a summary of that data, and send the message to the Lumen_Chat_Client.
3. THE Advice_Service SHALL build the advice message with a length of at most the Message_Limit.
4. WHEN the full data does not fit within the Message_Limit, THE Advice_Service SHALL include aggregated totals for Income, Receipts, Bills and Budgets and only the most recent individual items that fit, instead of failing.
5. WHEN the Lumen_Chat_Client returns the Answer_Text, THE Advice_Service SHALL return the advice as a list of strings derived from the Answer_Text.
6. WHEN no Income, Receipts, Bills or Budgets exist, THE Advice_Service SHALL still send the message and return the resulting advice.
7. THE Advice_Service SHALL keep the existing advice endpoint path and response type unchanged.

### Requirement 8: Replace Placeholders

**User Story:** As a developer, I want the Lumen-backed services to replace the placeholders, so that the application uses real AI behavior.

#### Acceptance Criteria

1. THE Klaro_Backend SHALL use the Lumen-backed Search_Service as the active SearchService implementation.
2. THE Klaro_Backend SHALL use the Lumen-backed Advice_Service as the active AdviceService implementation.
3. THE Klaro_Backend SHALL contain no code path that calls the Placeholder_Services when a Lumen_Error occurs.
