# Security Policy

## Supported versions

This is a single-branch portfolio project (no maintained release lines): security fixes land on
`main` only. There is no LTS/backport policy.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for a suspected vulnerability. Instead, use
[GitHub's private vulnerability reporting](../../security/advisories/new) for this repository
(Security tab, "Report a vulnerability"), or contact the maintainer through the email on the
[GitHub profile](https://github.com/ronybrand).

Include, where applicable: a description and impact, steps to reproduce, and the affected screen
or module.

This project is maintained on a best-effort basis (no SLA), but reports are taken seriously.

## Scope

In scope: the application code in this repository and its CI/CD workflows.

Out of scope: the [`estado`](https://github.com/ronybrand/estado) API it talks to, covered by its
own policy. Dependency vulnerabilities are tracked by Dependabot and Trivy.

## What this app already does

- **The session token is encrypted at rest** with an AES-GCM key held in the Android Keystore
  (`KeystoreKeyValueStore`); only the ciphertext reaches `SharedPreferences`. It is mirrored in
  memory because the HTTP interceptor needs it without waiting. (`androidx.security:security-crypto`
  does the same but is deprecated, so the 40 lines it wrapped live here.) Nothing is backed up:
  `allowBackup` is off and the data extraction rules exclude every domain.
- **The session is read before the first frame** (`Application.onCreate`), so no guard ever acts on an
  "unknown yet" state. A stored token that is expired or malformed is dropped and deleted at launch,
  and a timer ends the session at the JWT's `exp`. The client-side `exp` decode is a UI hint only: the
  signature is never verified here, and the backend authorizes every request.
- **No secrets in the binary.** `API_URL` is compiled in (`-PapiUrl=...`) and is a public url. The
  bearer token is attached by one OkHttp interceptor to requests built from that base url only, and a
  redirect to another host loses the header.
- **Cleartext http is off in release.** Only the `e2e` build type, which is never published, allows
  plain http, and only to `localhost`.
- **A failing keystore degrades to "logged out"** instead of crashing; a failed write keeps the
  session in memory for that run only.
- **Path parameters are validated** before they reach the API (a deep link can put anything in
  `{id}`).
- **Request correlation ids** (`X-Request-Id`, a random v4 uuid) are generated once per action and
  kept across retries; only a uuid echoed by the server is ever displayed.
- **The release is minified** (R8) and the upload key never touches a pull request: CI proves the
  signing wiring with a throwaway key.
- Dependabot for Gradle and GitHub Actions, CodeQL, [Trivy](.github/workflows/ci.yml) on the
  repository, and a CycloneDX SBOM of the shipped classpath scanned on every change and weekly.

## Known limits

- No certificate pinning: the app trusts the system certificate store.
- A rooted device can read the app's memory and, with the right access, use the Keystore key; that
  is outside what an app can defend.
- Android's dependency graph on GitHub does not read this Gradle build, so the dependency review
  does not see the libraries; the SBOM scan and the dependency submission from `main` cover them
  (see `.github/workflows/supply-chain.yml`).
