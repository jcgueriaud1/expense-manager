# ADR-0007 — Google OAuth2 + domain-gated auto-provisioning

**Status:** Accepted

## Context
Auth is Google OAuth2 with local user records so roles/access are managed
independently of the Google account. The provisioning and gating policy drives
the user table, the login flow, and the admin "revoke access" feature.

## Decision
- **Identity link:** local user ↔ Google's stable `sub` (subject) claim; also
  store email + display name. Do **not** match on email alone (reassignable).
- **Gating:** **auto-provision on first successful login, restricted to the
  `vaadin.com` Google Workspace domain** (`hd` claim). Default role `USER`.
  Logins outside the domain are rejected.
- **Revoke:** a local `enabled` flag. A disabled user is refused at login even
  with a valid Google account — this is what makes access "managed independently
  of Google."
- **Bootstrap:** seed `jean-christophe@vaadin.com` as the initial `ADMIN` via
  migration, so admin screens aren't locked out on a fresh DB.
- **Roles:** `USER`, `ADMIN` (admin ⊇ user capabilities).

## Consequences
- No manual onboarding, but not open to the public — a credible internal tool.
- Dev/test replace real Google with a form-stub of the same authorities and
  user records (ADR-0012); real Google OAuth only in staging/prod (ADR-0013).
