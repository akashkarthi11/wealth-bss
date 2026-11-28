# Finvu Account Aggregator (FIU sandbox)

This integration is an opt-in sandbox adapter. It does **not** use the consumer Finvu web account directly and does not enable production AA access.

## Configuration (BSS only)

Set the following server environment variables; never expose them in the UI, browser, repository, or client-side logs:

- `WEALTH_FINVU_FIU_TOKEN`: sandbox FIU bearer token supplied by Finvu.
- `WEALTH_FINVU_FIU_BASE_URL`: optional; defaults to `https://finsense.finvu.in/ConnectHub/FIU/API/V1`.

If the token is absent, the UI reports `NOT_CONFIGURED` and calls that require credentials fail closed.

## User flow

1. Open Wealth > Banks and enter the AA handle (e.g. the sandbox's `customer1@finvu` test handle, or your actual handle if provisioned in the sandbox).
2. Request consent using `ONETIME_DEV_TEMPLATE` or `PERIODIC_DEV_TEMPLATE`.
3. Approve/reject the consent in the Finvu AA app/web.
4. Poll consent status using the returned `consentHandle`. Acceptance returns `consentId`.
5. Request FI data with a date range within the consent.
6. Poll FI status. Only fetch after the status is `READY`.
7. The raw sandbox response is displayed in the current browser session. The current implementation does not persist imported FI data or create Wealth account/transaction records.

## Internal endpoints

All routes require an authenticated Wealth session and pass through BFF → BSS.

- POST `/wealth/api/v1/aa/finvu/config`
- POST `/wealth/api/v1/aa/finvu/consent/request`
- POST `/wealth/api/v1/aa/finvu/consent/status`
- POST `/wealth/api/v1/aa/finvu/fi/request`
- POST `/wealth/api/v1/aa/finvu/fi/status`
- POST `/wealth/api/v1/aa/finvu/fi/fetch`

The corresponding BSS paths are under `/wealth-service/api/v1/aa/finvu`.

## Sandbox API mapping

The adapter calls Finvu's FIU module endpoints documented at the sandbox site:

- `SubmitConsentRequest`
- `ConsentStatus/{consentHandleId}/{custId}`
- `FIRequest`
- `FIStatus/{consentId}/{sessionId}/{consentHandleId}/{custId}`
- `FIFetch/{custId}/{consentId}/{sessionId}`

The sandbox FIU endpoint is AA-agnostic and uses the AA handle to route consent to the selected AA. The sandbox offers one-time and periodic development templates. Consult Finvu's sandbox docs for current access, sample handles and tokens.

## Production readiness gaps

Production integration requires Finvu onboarding, FIU credentials and security configuration. Before production:
- Implement durable consent/session storage and lifecycle state.
- Validate consent artefacts and webhook/notification signatures.
- Add consent revoke/expiry handling and audit events.
- Encrypt fetched FI data at rest and apply retention/deletion controls.
- Normalize account/transaction/deposit/loan data and reconcile idempotently into Wealth.
- Add retry limits, rate limiting, timeout handling and provider-specific monitoring.
- Confirm compliance/FIU onboarding and FIP coverage with Finvu.
