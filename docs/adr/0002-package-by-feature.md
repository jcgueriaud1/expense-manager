# ADR-0002 — Package-by-feature code organization

**Status:** Accepted

## Context
Vaadin 25's own guidance recommends feature-based packaging over layer-based.
The brief's meta-goal also benefits: a finding or an AI-implemented use-case
should map to one cohesive folder rather than being spread across four layer
packages.

## Decision
Organize top-level packages **by feature**, each with `domain` / `service` / `ui`
sub-packages, plus shared and security packages:

```
com.example.expenses
├── base/        shared domain + ui (MainLayout, common types, error handling)
├── security/    Spring Security + Google OAuth config, CurrentUser
├── user/        user records, roles, admin user management
├── report/      expense reports + lines (aggregate), My Reports, report detail
├── approval/    admin review queue, approve/reject, status history
└── allowance/   per-diem/km rate config + calculation domain service
```

Package boundaries are provisional and confirmed as the domain model settles.

## Consequences
- Cross-feature reuse goes through `base`; features do not depend on each other's
  internals.
- The base Maven `groupId` is `com.example`; final root package to be confirmed
  at implementation (kept `com.example.expenses` here for illustration).
