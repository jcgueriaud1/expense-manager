# UC-007: Login and View Role-Based Dashboard

> A user logs into the application and is presented with a role-specific dashboard.

---

**Goal:** As a user, I want to log in and be routed to a dashboard appropriate for my role so that I can begin my work efficiently.

**Status:** Pending  
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** Any user (not yet authenticated)
- **Secondary actors:** Authentication system; database

---

## Preconditions

- User has a valid account in the system (active = true)
- User is not currently logged in
- User is on the login page at `/login`

---

## Trigger

User opens the application and is redirected to the login page, or user clicks "Log Out" and is prompted to log in again.

---

## Main Flow

1. System displays the login form with:
   - Username field
   - Password field
   - "Log In" button
   - Optional "Forgot Password" link (not in scope for v1)

2. User enters their username.

3. User enters their password.

4. User clicks "Log In".

5. System validates credentials against the User table:
   - Username exists
   - Password is correct (hashed and verified)
   - User is active (active = true)

6. System establishes an authenticated session and sets a secure session cookie.

7. System determines user's role from the User record.

8. System redirects to the role-specific dashboard:
   - EMPLOYEE → My Reports page (UC-002)
   - MANAGER → Pending Approvals page (UC-003)
   - FINANCE → Finance Dashboard (UC-004)
   - ADMIN → User Management page (UC-006)

9. Dashboard displays a welcome message: "Welcome, [First Name]."

10. User can see a navigation menu with links to available features based on their role.

---

## Alternative Flows

### AF-1: Invalid Username or Password

**Branches from:** Main Flow step 5  
**Condition:** Username does not exist or password is incorrect

1. System displays error message: "Invalid username or password."
2. System does NOT reveal whether username or password was wrong (security best practice).
3. Form is cleared (password field only).
4. User can retry.
5. Returns to Main Flow step 2.

### AF-2: User Account Deactivated

**Branches from:** Main Flow step 5  
**Condition:** Username exists but active = false

1. System displays error message: "This account is inactive. Contact your administrator."
2. Form remains on page.
3. Use case ends.

### AF-3: Session Timeout

**Branches from:** Main Flow (during dashboard use)  
**Condition:** User's session expires due to inactivity

1. System detects expired session on the next action (e.g., clicking a button).
2. System displays warning: "Your session has expired. Please log in again."
3. System redirects to login page.
4. Use case ends (user must restart from step 1).

### AF-4: Access Denied on Protected Page

**Branches from:** Main Flow (if user tries to access a page outside their role)  
**Condition:** User navigates directly to a URL not available for their role (e.g., employee tries to access `/admin/users`)

1. System checks role authorization on the backend.
2. System redirects to the user's default dashboard for their role.
3. System displays message: "You do not have access to that page."
4. Use case ends.

### AF-5: Log Out

**Branches from:** Main Flow (after login)  
**Condition:** User clicks "Log Out" button in navigation

1. System invalidates the session and clears the session cookie.
2. System redirects to login page with message: "You have been logged out."
3. Use case ends.

---

## Postconditions

- **On successful login:** User is authenticated, session is established, user is redirected to role-appropriate dashboard.
- **On failed login:** No session is established; user remains on login page with error message.
- **On log out:** Session is terminated; user is redirected to login page.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-038 | Only active users (active = true) can log in. |
| BR-039 | Login credentials are case-sensitive (username and password). |
| BR-040 | Failed login attempts do not reveal whether username or password was wrong. |
| BR-041 | Sessions expire after a configurable period of inactivity (e.g., 30 minutes). |
| BR-042 | Each user's role determines their default dashboard and available menu items. |
| BR-043 | A user cannot access features or pages outside their role's authorization. |
| BR-044 | Session management is handled by Spring Security (JSESSIONID cookie, server-side session store). |

---

## Tests

- [ ] Main Flow (successful login and dashboard) covered (steps 1–10)
- [ ] AF-1 (invalid credentials) covered
- [ ] AF-2 (inactive account) covered
- [ ] AF-3 (session timeout) covered
- [ ] AF-4 (access denied) covered
- [ ] AF-5 (log out) covered
- [ ] BR-038 through BR-044 covered

---

## UI Surface

| Page/Component | Access | Details |
|---|---|---|
| Login Form | Unauthenticated | Page with username field, password field, Log In button. Optional "Forgot Password" link. |
| Login Error Message | Unauthenticated | Generic error for invalid credentials or inactive account. |
| Dashboard (Role-based) | Authenticated | Different layout/content per role; welcome message, role-specific menu. |
| Navigation Menu | Authenticated | Links to available features per role (My Reports, Approvals, Admin, etc.). |
| Log Out Button | Authenticated | Located in navigation menu or header. |
| Session Timeout Warning | Authenticated | Modal or banner warning of expiring session before timeout. |
| Access Denied Message | Authenticated | Message if user tries to access unauthorized page. |
