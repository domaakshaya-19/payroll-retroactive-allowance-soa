# Local demo security

All examples use the HTTPS Gateway at `https://localhost:8080`. There is no external identity provider. Use the root README to build the apps, start Kafka/Eureka and generate the local TLS/JWT keys.

| Username | Demo password | Role | Allowed employee IDs |
|---|---|---|---|
| clerk | Clerk@123 | payrollClerk | EMP001 |
| approver | Approver@123 | payrollApprover | EMP001 |

These public passwords are demo credentials, not JWT signing keys. `Initialize-Local.ps1` sets them when their environment variables are absent. To ensure these exact credentials even if your shell contains older values, set them before initialization:

```powershell
$env:CLERK_PASSWORD = 'Clerk@123'
$env:APPROVER_PASSWORD = 'Approver@123'
. .\scripts\Initialize-Local.ps1
```

Initialization explicitly enables the local issuer with `AUTH_DEMO_ENABLED=true`. Without that flag the Auth login controller is absent. The local endpoint is `POST /api/auth/token`; there is no registration or role-selection endpoint. Usernames map to fixed roles on the server; clients cannot choose roles or employee IDs in a login request. `CLERK_EMPLOYEES` / `APPROVER_EMPLOYEES` are explicit comma-separated entitlement lists, both defaulting to `EMP001`. They contain no wildcard. A well-authenticated request for `EMP999` returns **403**. Restart Auth after changing credentials or assignments; already-issued tokens retain their claims until expiry.

The signing key is a generated RSA private key stored in ignored `.local/jwt-private.pem`, referenced by `JWT_PRIVATE_KEY`. It is passed only to Auth. Gateway and domain services load `JWT_PUBLIC_KEY`, validate RS256 signature, issuer, audience and required expiry, and reject invalid/expired tokens with 401. Access tokens last 900 seconds and are returned with `Cache-Control: no-store`. Infrastructure credentials remain random environment values. The separate employee-scoped `retro-service` principal has only read access to PayPeriod snapshots/open periods and is used directly between services.

Gateway checks roles and employee entitlements before forwarding. Domain services repeat the checks so calling a domain port cannot bypass authorization. Clerks create/backdate rules; approvers approve adjustments; both read entitled records. Anonymous protected requests return **401**. A clerk attempting approval returns **403**, even if the adjustment ID does not exist. Approvers cannot create/backdate rules.

Issued statements have no editing API. PUT, PATCH and DELETE under PayPeriod return **403** for authenticated clients at Gateway and PayPeriod. Trying to issue an already closed period returns **409**. Original issued snapshots also have immutable persistence mappings and remain unchanged when rules are backdated.

## curl from Windows PowerShell

Run from the project root after all services have registered in Eureka. `curl.exe` uses the generated public certificate explicitly; no TLS validation bypass is required. Login payload files contain the public demo credentials only.

```powershell
$base = 'https://localhost:8080'
$ca = '.local/localhost.crt'

# 200: obtain each locally signed access token, without printing it.
$clerk = (curl.exe --silent --show-error --fail --cacert $ca -X POST "$base/api/auth/token" -H 'Content-Type: application/json' --data-binary '@examples/clerk-login.json' | ConvertFrom-Json).accessToken
$approver = (curl.exe --silent --show-error --fail --cacert $ca -X POST "$base/api/auth/token" -H 'Content-Type: application/json' --data-binary '@examples/approver-login.json' | ConvertFrom-Json).accessToken

# 401: protected request with no bearer token.
curl.exe --cacert $ca -i "$base/api/allowances/employees/EMP001"

# 200: both roles may read EMP001.
curl.exe --cacert $ca -i "$base/api/allowances/employees/EMP001" -H "Authorization: Bearer $clerk"
curl.exe --cacert $ca -i "$base/api/pay-periods/employees/EMP001/snapshots" -H "Authorization: Bearer $approver"

# 403: employee outside the signed entitlement list.
curl.exe --cacert $ca -i "$base/api/allowances/employees/EMP999" -H "Authorization: Bearer $clerk"

# 403: clerk cannot approve (no adjustment is changed).
curl.exe --cacert $ca -i -X POST "$base/api/retro-adjustments/employees/EMP001/1/approve" -H "Authorization: Bearer $clerk"

# 403: approver cannot backdate rules (no rule is changed).
curl.exe --cacert $ca -i -X POST "$base/api/allowances/employees/EMP001/HOUSING/backdate" -H "Authorization: Bearer $approver" -H 'Content-Type: application/json' --data-binary '@examples/backdate-rule.json'

# 403: issued snapshots cannot be edited.
curl.exe --cacert $ca -i -X PATCH "$base/api/pay-periods/employees/EMP001/1/snapshots" -H "Authorization: Bearer $clerk" -H 'Content-Type: application/json' --data-binary '@examples/forbidden-snapshot-edit.json'
```

These optional examples **change demo data**. Create TRAVEL once; a repeated create returns 409. Backdate the seeded HOUSING version 1 once; repeating with stale `expectedVersion` returns 409. The backdate asynchronously creates the two historical differences described in the root README.

```powershell
# 201: clerk creates a rule.
curl.exe --cacert $ca -i -X POST "$base/api/allowances/employees/EMP001" -H "Authorization: Bearer $clerk" -H 'Content-Type: application/json' --data-binary '@examples/create-rule.json'

# 201: clerk backdates HOUSING.
curl.exe --cacert $ca -i -X POST "$base/api/allowances/employees/EMP001/HOUSING/backdate" -H "Authorization: Bearer $clerk" -H 'Content-Type: application/json' --data-binary '@examples/backdate-rule.json'

# Read generated IDs after the workflow reaches COMPLETED.
curl.exe --cacert $ca "$base/api/retro-adjustments/employees/EMP001" -H "Authorization: Bearer $approver"
$adjustmentId = 1 # Replace with an actual DRAFT adjustment ID from that response.
# 200: approver approves; repeat approval returns 409.
curl.exe --cacert $ca -i -X POST "$base/api/retro-adjustments/employees/EMP001/$adjustmentId/approve" -H "Authorization: Bearer $approver"
```

## Postman

Import `examples/payroll-security.postman_collection.json`. Keep `baseUrl` at the Gateway URL. Add `.local/localhost.crt` as a trusted CA certificate in Postman's certificate settings and keep SSL certificate verification enabled.

Run **Security checks** in order: the first two requests log in and keep tokens in collection variables, then response scripts assert the expected 200/401/403 statuses. No actual approval or rule write occurs in that folder. The separate **Optional workflow writes** folder contains the create/backdate/approve examples; run those deliberately after setting `adjustmentId` to a real draft ID. Clear token collection variables before sharing an exported collection. Expired tokens require logging in again.

## Automated verification

```powershell
mvn -B -ntp verify
```

HTTP security tests generate an RSA test key pair below ignored `target/`, sign real tokens and submit them through MockMvc or WebTestClient. They check actual signature and expiry validation, 401 for anonymous requests, 403 for clerk approval and unauthorized employee IDs, successful role-authorized calls, and blocked snapshot edits. Login tests use the exact demo credentials, verify token roles/employee claims/expiry and verify the endpoint is absent when demo mode is disabled. Existing H2 tests verify that reissuing closed periods fails and persisted issued snapshots cannot be changed. Gateway tests use a stub backend; they do not start live Eureka or Kafka.
