# UC-001: Submit Expense Report

> An employee creates a new expense report with one or more line items and submits it for approval.

---

**Goal:** As an employee, I want to submit an expense report containing multiple expenses so that I can request reimbursement for business costs.

**Status:** Pending  
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** Authenticated employee
- **Secondary actors:** Expense category system; database

---

## Preconditions

- User is authenticated and has the EMPLOYEE role
- At least one expense category is active in the system
- User has access to the "New Report" or "Submit Expense" feature

---

## Trigger

User clicks "New Expense Report" or "Submit Expenses" button.

---

## Main Flow

1. System displays a form with:
   - Report description field (text)
   - Expense line-item list (initially empty or with one blank row)
   - Fields for each line item: category dropdown, description, amount, currency, expense date, receipt (optional file/URL)
   - "Add Line Item" and "Delete Line Item" buttons
   - "Save as Draft" and "Submit" buttons

2. Actor selects an expense category from the dropdown for the first line item.

3. Actor enters a description (e.g., "Flight to Helsinki conference").

4. Actor enters an amount and selects a currency.

5. Actor selects an expense date (date picker).

6. Actor optionally attaches or links a receipt.

7. Actor clicks "Add Line Item" to add more expenses (if needed).

8. Repeat steps 2–6 for each additional line item.

9. Actor enters a business purpose/summary in the report description field (e.g., "Q1 travel and meals for client meetings").

10. System calculates and displays the total amount for all line items.

11. Actor reviews the report and clicks "Submit".

12. System validates the report:
    - All required fields (category, description, amount, date per line item; report description) are filled
    - All amounts are positive and valid
    - Report contains at least one expense
    - No currency is invalid

13. System creates the ExpenseReport with status SUBMITTED and all Expenses.

14. System records submission timestamp and sets `submittedAt`.

15. System displays a success message: "Report submitted. It will be reviewed by your manager."

16. System navigates to the report detail view, showing the submitted report as read-only.

---

## Alternative Flows

### AF-1: Save as Draft

**Branches from:** Main Flow step 11  
**Condition:** Actor clicks "Save as Draft" instead of "Submit"

1. System validates only that at least one expense line item exists with category and amount.
2. System creates the ExpenseReport with status DRAFT and all Expenses.
3. System displays success message: "Report saved as draft."
4. System navigates to the report detail view, showing the draft report in edit mode.
5. Use case ends.

### AF-2: Missing Required Fields

**Branches from:** Main Flow step 12  
**Condition:** One or more required fields are empty or invalid

1. System highlights the invalid fields with error messages (e.g., "Amount is required" or "Invalid date").
2. System prevents submission and remains on the form.
3. Actor corrects the errors and re-attempts submission.
4. Returns to Main Flow step 12.

### AF-3: No Expenses in Report

**Branches from:** Main Flow step 12  
**Condition:** Report has no line items or all line items are empty

1. System displays error: "Report must contain at least one expense."
2. System remains on the form.
3. Actor adds an expense and retries.
4. Returns to Main Flow step 12.

### AF-4: Invalid Currency

**Branches from:** Main Flow step 12  
**Condition:** User enters or selects an unsupported currency code

1. System displays error: "Currency [CODE] is not supported."
2. System remains on the form.
3. Actor selects a supported currency.
4. Returns to Main Flow step 12.

### AF-5: Delete Line Item

**Branches from:** Main Flow (any step before submission)  
**Condition:** Actor clicks "Delete" on a line item

1. System removes the line item from the form.
2. System recalculates total amount.
3. If report now has zero expenses and user clicks "Submit", branch to AF-3.
4. Use case continues.

---

## Postconditions

- **On success (submitted):** ExpenseReport is created with status SUBMITTED, all Expenses are persisted, submittedAt is set, and system navigates to detail view.
- **On success (draft):** ExpenseReport is created with status DRAFT, all Expenses are persisted, submittedAt is null, and system navigates to edit view.
- **On failure:** No ExpenseReport or Expenses are created; form remains open with error messages.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-001 | A report must contain at least one expense line item with category, description, and amount. |
| BR-002 | All amounts must be positive (> 0) and have a valid currency. |
| BR-003 | The report description is required and must not be empty. |
| BR-004 | Each expense date must be a valid date; future dates are allowed (for pre-approved travel). |
| BR-005 | Receipt attachment is optional but recommended. |
| BR-006 | Report status is DRAFT until explicitly submitted; only submitted reports enter the approval workflow. |
| BR-007 | An employee can have multiple reports in DRAFT or SUBMITTED status concurrently. |

---

## Tests

- [ ] Main Flow covered (steps 1–16)
- [ ] AF-1 (Save as Draft) covered
- [ ] AF-2 (Missing Required Fields) covered
- [ ] AF-3 (No Expenses) covered
- [ ] AF-4 (Invalid Currency) covered
- [ ] AF-5 (Delete Line Item) covered
- [ ] BR-001 through BR-007 covered

---

## UI Surface

| Page/Component | Access | Details |
|---|---|---|
| New Expense Report Form | Authenticated EMPLOYEE | Multi-row form with category dropdown, description, amount, currency, date picker per line item; report-level description field; Add/Delete line item buttons; Save Draft and Submit buttons |
| Total Amount Display | Authenticated EMPLOYEE | Read-only, updates as user adds/edits amounts |
| Success Message | Authenticated EMPLOYEE | Toast or banner; "Report submitted" or "Report saved as draft" |
| Report Detail (Read-only post-submit) | Authenticated EMPLOYEE | Shows submitted report with all expenses, status, and submission timestamp |
| Report Detail (Editable Draft) | Authenticated EMPLOYEE | Shows draft report with all expenses; allows editing and resubmission |
