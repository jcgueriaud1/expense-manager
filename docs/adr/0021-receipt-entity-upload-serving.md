# ADR-0021 — Receipt: separate entity, buffered upload, summary-only DTO

**Status:** Accepted

## Context
ADR-0009 decided receipts are stored as Postgres `bytea` on the expense line,
served with the current Vaadin streaming API, with a size cap and content-type
validation. Phase 3 (plan 3.1–3.3) had to turn that into a concrete design, and
three questions surfaced that ADR-0009 left open — and one that collided with
another decision:

1. **Where does the blob physically live?** A `bytea` column on `expense_line`
   sits in a hot row: every report-detail render and My-Reports total recompute
   loads lines, and lazy `byte[]` loading in Hibernate is the fragile case
   (silently eager without bytecode enhancement).
2. **When is a receipt uploaded, given ADR-0019?** ADR-0019 keeps the report and
   its lines *in memory until first save*, and explicitly punted the receipt
   tension: "uploading a receipt requires saving the report first… revisit in the
   Phase 3 spec if the UX bites." A "save before you can attach" step — and, worse,
   a disabled upload control — is exactly the kind of unexplained gate the app's
   forms are meant to avoid (never disable the primary action; explain instead).
3. **What crosses the DTO boundary?** ADR-0003 keeps entities off the UI. Naively
   mapping the receipt would drag megabytes through every aggregate load.
4. **How much do we trust the uploaded content?** A browser's `Content-Type` is
   just a label from the file extension; a mislabeled file served back inline is a
   stored-XSS footgun.

## Decision

**Separate `receipt` entity/table.** A `receipt` row (bigint PK, `AuditedEntity`
timestamps) holds `content_type` (not null), `filename`, `size_bytes`, and the
`bytea`, in a `LAZY` one-to-one owned by the receipt (FK to `expense_line`).
Cardinality is **0..1 per line, always optional**. Physical separation makes
"blobs never ride a hot query" structural, not dependent on a fetch annotation.

**Buffered upload, persisted on aggregate save — overrides ADR-0019's "save
first."** The upload control works at any time, including on a brand-new unsaved
report; it is never gated or disabled. Received bytes are held in the in-memory
`ReportDetailDto` working copy and INSERTed/updated as part of `create`/`update`.
An *unsaved* receipt previews from the buffered bytes; a *saved* one streams from
the DB. This retires ADR-0019's "save before attaching a receipt" consequence.

**Summary-only DTO; bytes only via a dedicated streaming query.** The aggregate
and all DTOs carry a receipt *summary* — `receiptId`, `filename`, `content_type`,
`size_bytes`, `hasReceipt` — and **never** the `byte[]`. The `DownloadHandler`
service method issues a dedicated projection selecting only one receipt's `bytea`
by id, guarded by the owning-report authorization check (ADR-0008), and streams
it without materializing it on the aggregate.

**Validation.** Allow-list **JPEG / PNG / PDF**. Reject by **server-side
magic-byte verification** (`FF D8 FF` / `89 50 4E 47` / `25 50 44 46`), and derive
the stored `content_type` from the sniffed signature rather than the browser's
claim. **10 MB** cap, aligned across the Vaadin `UploadHandler`, Spring multipart
(`max-request-size` ~12 MB for overhead), and any proxy body limit; rejected at
upload time. Serve with `Content-Disposition` and `X-Content-Type-Options:
nosniff`.

**View & mutation.** Images preview inline (thumbnail → enlarge in a dialog); PDFs
open/download. Replace = overwrite (no receipt history); explicit remove clears it.
All mutations are gated to `DRAFT`/`REJECTED`, mirroring line editability
(ADR-0019); `SUBMITTED`/`APPROVED` are view-only.

## Consequences
- Blob storage stays off list and edit load paths by construction; one extra
  table and a lazy one-to-one is the cost.
- Receipt bytes now live on the Vaadin session heap between attach and save
  (bounded by the 10 MB cap × lines with a pending upload). If that pressure
  bites under real use, revisit with a staging store — log a finding.
- Magic-byte verification means "we accept images," not "we accept things labeled
  as images," making inline preview safe.
- HEIC (iPhone default) is excluded; iPhone users will hit it. Logged as F-019.
- Multi-image per line and "receipt required over a threshold" are out of scope
  for V1; both are plausible later work.
