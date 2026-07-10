# ADR-0009 — Receipts stored as Postgres bytea

**Status:** Accepted — refined by [ADR-0021](0021-receipt-entity-upload-serving.md)
(separate `receipt` table, buffered upload, summary-only DTO, magic-byte validation).

## Context
Receipts must survive the deployment model. The brief wants rolling/blue-green
updates, meaning multiple ephemeral container instances can be live at once.
Local filesystem storage silently breaks under that model (files vanish on
redeploy, not shared across instances).

## Decision
Store receipt images as **Postgres `bytea`**, associated with the expense line,
with a **size cap and allowed-content-type validation** on upload. Bytes are
served through a **streaming service method using the current Vaadin streaming
API (`DownloadHandler`/`UploadHandler`), not the deprecated `StreamResource`**,
and never expose the JPA entity to the UI (ADR-0003).

Object storage (MinIO local / S3-compatible prod) is **deferred**.

## Consequences
- One transaction, one backup, survives redeploys, works across instances. No
  extra infra in V1.
- DB grows with receipts; large-file streaming goes through the app.
- **Logged finding:** at what scale does DB-blob storage stop being appropriate,
  and how painful is the later migration to object storage? The deferral itself
  is learning.
