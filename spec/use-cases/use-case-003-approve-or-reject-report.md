# UC-003: Approve or Reject Expense Report

> A manager reviews and approves or rejects an expense report submitted by one of their team members.

---

**Goal:** As a manager, I want to review expense reports submitted by my team and approve or reject them so that I can control spending and ensure compliance.

**Status:** Pending  
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** Authenticated user with MANAGER role
- **Secondary actors:** Database; ExpenseApproval audit system

---

## Preconditions

- User is authenticated and has the MANAGER role
- At least one subordinate has submitted an expense report (status = SUBMITTED)
- User has navigated to the "Pending Approvals" or "Reports to Approve" page

---

## Trigger

Manager clicks "Pending Approvals" menu item or navigates to the approval queue.

---

## Main Flow

1. System displays a list of SUBMITTED expense reports from the manager's direct reports.

2. Each report row shows:
   - Report ID (link to detail view)
   - Submitter name
   - Total amount
   - Number of expenses
   - Submission date
   - Status (SUBMITTED)

3. System sorts by submission date (oldest first) to surface older pending reviews.

4. Manager clicks on a report row to view the detailed report.

5. System displays the full report detail with:
   - Report description and metadata (submitter, submission date)
   - Table of all expenses (category, description, amount, date, receipt link)
   - Total amount summary
   - An approval action panel with two buttons: "Approve" and "Reject"
   - A comment/note field (optional)

6. Manager reviews the report, including all line items and receipts.

7. Manager clicks "Approve".

8. System optionally prompts for a comment (or allows one if manager has typed in the comment field).

9. System updates the report status to APPROVED, sets `approvedAt` to now, and sets `approvedBy` to the manager's user ID.

10. System creates an ExpenseApproval record with action = APPROVED and any comment.

11. System displays success message: "Report approved."

12. System navigates back to the approval queue.

---

## Alternative Flows

### AF-1: Reject Report

**Branches from:** Main Flow step 7  
**Condition:** Manager clicks "Reject" instead of "Approve"

1. System displays a rejection dialog with a required text field for rejection reason.
2. Manager enters the reason (e.g., "Missing receipts for meals; please resubmit with documentation").
3. Manager clicks "Confirm Rejection".
4. System updates the report status to REJECTED, sets `approvedAt`, sets `approvedBy`, and sets `rejectionReason` to the entered text.
5. System creates an ExpenseApproval record with action = REJECTED and the reason as the comment.
6. System displays success message: "Report rejected. Employee will be notified."
7. System navigates back to the approval queue.
8. Use case ends.

### AF-2: Add Comment Without Action

**Branches from:** Main Flow step 6  
**Condition:** Manager types a comment but does not yet approve or reject

1. Manager enters a comment (optional).
2. Manager can save the comment as a note without taking action (e.g., "Need clarification" button).
3. System creates an ExpenseApproval record with action = COMMENTED.
4. System displays success message: "Comment added; report remains pending."
5. Use case continues (manager can later approve or reject).

### AF-3: Filter Approval Queue

**Branches from:** Main Flow step 1  
**Condition:** Manager uses filter options on the approval queue

1. Manager can filter by:
   - Amount range (e.g., > $500)
   - Submitter (if managing multiple teams)
   - Days pending (e.g., > 7 days overdue)
2. System updates the list according to the filter.
3. Use case continues.

### AF-4: Invalid or Missing Receipts

**Branches from:** Main Flow step 6  
**Condition:** A receipt link is broken or missing for critical items

1. Manager can click "View Receipt" but get a 404 or empty result.
2. Manager notes this in the rejection reason (AF-1).
3. Use case continues.

---

## Postconditions

- **On approval:** Report status = APPROVED, approvedAt is set, approvedBy is set, ExpenseApproval record created, manager returned to queue.
- **On rejection:** Report status = REJECTED, approvedAt is set, approvedBy is set, rejectionReason is set, ExpenseApproval record created, manager returned to queue.
- **On comment-only:** ExpenseApproval record created with action = COMMENTED; report status remains SUBMITTED; manager can continue in queue.
- **On failure:** No status change; error message displayed.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-013 | A manager can only approve reports from their direct reports. |
| BR-014 | A report can only be approved/rejected once (status transitions from SUBMITTED to APPROVED or REJECTED are permanent). |
| BR-015 | Rejection requires a reason; approval and comments are optional. |
| BR-016 | Multiple managers (e.g., Finance) may also approve reports (see UC-004 for Finance approval flow). |
| BR-017 | Once approved or rejected, the report is read-only for the employee and cannot be edited or resubmitted from the approval view. |

---

## Tests

- [ ] Main Flow (approve) covered (steps 1–12)
- [ ] AF-1 (reject with reason) covered
- [ ] AF-2 (comment without action) covered
- [ ] AF-3 (filter queue) covered
- [ ] AF-4 (missing receipt) covered
- [ ] BR-013 through BR-017 covered

---

## UI Surface

| Page/Component | Access | Details |
|---|---|---|
| Approval Queue / Pending Approvals | Authenticated MANAGER | Table showing SUBMITTED reports from subordinates. Columns: ID, Submitter, Total, Count, Submitted Date, Status. Sortable by submission date. |
| Filter Options | Authenticated MANAGER | Amount range slider, submitter dropdown (if multiple teams), days pending filter. |
| Report Detail (Approval View) | Authenticated MANAGER | Shows full report, all expenses, total, and Approve/Reject/Comment buttons with optional comment field. |
| Rejection Reason Dialog | Authenticated MANAGER | Required text field for reason. |
| Success Message | Authenticated MANAGER | Toast/banner confirming action and returning to queue. |
