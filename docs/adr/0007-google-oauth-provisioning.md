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
  `vaadin.com` Google Workspace domain** — accept only if `hd == "vaadin.com"`
  **and** `email_verified == true`. Default role `USER`. Logins failing the gate
  are rejected.
- **Provisioning hook:** a custom `OidcUserService` runs the gate + claim/create
  in a transaction and returns a principal whose authorities are the *local*
  user's roles (not Google's).
- **Immutability / source of truth:** `name` and `email` are **set once** at
  provision/claim and **not** re-synced on later logins (vaadin.com identities
  are stable). `roles` and `enabled` are always local source of truth and are
  never overwritten by Google claims.
- **Rejection UX:** throw `OAuth2AuthenticationException` → redirect to the login
  view, distinguishing *"limited to vaadin.com accounts"* from *"access disabled
  — contact an administrator"*.
- **Revoke:** a local `enabled` flag. A disabled user is refused at login even
  with a valid Google account — this is what makes access "managed independently
  of Google."
- **Bootstrap:** seed `jean-christophe@vaadin.com` as the initial `ADMIN` via
  migration, so admin screens aren't locked out on a fresh DB. The seed row has
  a **`null` `sub`** (Google assigns `sub` only at first login).
- **Claim-by-email (resolves the seed-vs-`sub` tension):** provisioning looks up
  by `sub` first; on miss, it falls back to an email lookup **restricted to rows
  with `sub IS NULL`** and, if found, populates that row's `sub`/name —
  "claiming" the pre-seeded row and preserving its `ADMIN` role. Only if both
  lookups miss is a new `USER` row created. Claiming only `sub IS NULL` rows
  prevents hijacking an already-linked account. `sub` is **nullable-until-claimed**;
  `email` carries a **unique** constraint.
- **Roles:** `USER`, `ADMIN` (admin ⊇ user capabilities). Only these two for
  now — no separate `FINANCE` role; the admin performs finance/export. Stored as
  an `@ElementCollection` `Set<Role>` (a `user_roles` table); subsumption is not
  stored (see ADR-0008 `RoleHierarchy`), so an admin row holds `{ADMIN}`.

## Consequences
- No manual onboarding, but not open to the public — a credible internal tool.
- Dev/test replace real Google with a form-stub of the same authorities and
  user records (ADR-0012); real Google OAuth only in staging/prod (ADR-0013).
