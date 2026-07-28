# Screenshots

Committed screenshots of this app's views, captured by the
`vaadin-playwright-screenshot` skill during a ticket's visual verification and
scored by `visual-verdict`. They are the reference a later verification compares
against, so a shot of a view is **overwritten** when that view changes rather
than accumulating one file per issue.

Named `<view>-<state>.png`. Current set, all from the owner's report detail view
(`/report`, `/report/{id}`):

| File | State | First captured for |
| --- | --- | --- |
| `report-detail-suppressed-line.png` | A generated line a zero Quantity Override removed — "Removed" badge, €0.00, reason, statutory baseline | #132 |
| `report-detail-suppressed-line-phone.png` | The same at 390×844, actions stacked under the row | #132 |
| `report-detail-override-dialog.png` | The Quantity Override editor at a count of 0 | #132 |
| `report-detail-receipt-destruction-confirm.png` | The confirm shown when removing a line would delete its receipt | #132 |
| `report-detail-overridden-line.png` | An ordinary (non-zero) overridden line — "Overridden" badge, reason, baseline | #131 |
| `report-detail-delete-report-confirm.png` | The draft-only "Delete report?" confirm | pre-existing |
