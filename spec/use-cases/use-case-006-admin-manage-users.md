# UC-006: Admin Manage Users

> System administrators create, update, and deactivate user accounts and assign roles.

---

**Goal:** As an administrator, I want to manage user accounts and roles so that I can maintain proper access control and team structures.

**Status:** Pending  
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** Authenticated user with ADMIN role
- **Secondary actors:** Database; user authentication system

---

## Preconditions

- User is authenticated and has the ADMIN role
- User has navigated to the "User Management" page

---

## Trigger

Admin clicks "User Management" menu item or admin dashboard link.

---

## Main Flow

1. System displays the user list page with a table of all users:
   - Username
   - Email
   - Full name (first + last)
   - Role (EMPLOYEE, MANAGER, FINANCE, ADMIN)
   - Manager (if applicable)
   - Active status (Active / Inactive)
   - Created date
   - Last login date

2. System sorts by creation date (most recent first) or username (configurable).

3. Admin can click on a user row to view/edit user details, or click "Add User" to create a new user.

4. System displays a user detail form with fields:
   - Username (required, unique, read-only after creation)
   - Email (required, unique)
   - First name (required)
   - Last name (required)
   - Role dropdown (EMPLOYEE, MANAGER, FINANCE, ADMIN)
   - Manager dropdown (only visible if role = EMPLOYEE; select which manager supervises this user)
   - Active checkbox (default: checked)

5. For new users:
   - Admin enters all fields
   - System generates a temporary password or sends password reset link
   - Admin clicks "Create User"
   - System creates the User record with status ACTIVE
   - System displays success message and returns to list

6. For existing users:
   - Admin can modify Email, First Name, Last Name, Role, Manager, and Active status
   - Admin clicks "Save Changes"
   - System updates the User record
   - System displays success message and returns to list

---

## Alternative Flows

### AF-1: Username Already Exists

**Branches from:** Main Flow step 5  
**Condition:** Admin enters a username that already exists

1. System displays error: "Username already taken. Please choose another."
2. Form remains open; admin corrects the username.
3. Returns to Main Flow step 5.

### AF-2: Email Already Exists

**Branches from:** Main Flow step 5 or 6  
**Condition:** Admin enters an email that already exists

1. System displays error: "Email already in use. Please enter a different email."
2. Form remains open; admin corrects the email.
3. Returns to Main Flow step 5 or 6.

### AF-3: Deactivate User

**Branches from:** Main Flow step 6  
**Condition:** Admin unchecks "Active" for an existing user

1. System displays warning: "This user will not be able to log in. Continue?"
2. Admin confirms deactivation.
3. System sets `active = false` for the User record.
4. System displays success message: "User deactivated."
5. Use case ends.

### AF-4: Reactivate User

**Branches from:** Main Flow step 6  
**Condition:** Admin checks "Active" for a deactivated user

1. System sets `active = true` for the User record.
2. System displays success message: "User reactivated."
3. Use case ends.

### AF-5: Filter Users

**Branches from:** Main Flow step 1  
**Condition:** Admin uses filter options

1. Admin can filter by:
   - Role (EMPLOYEE, MANAGER, FINANCE, ADMIN)
   - Active status (Active, Inactive, All)
   - Team (if manager is assigned)

2. System re-displays the list with matching users.
3. Use case continues.

### AF-6: Search Users

**Branches from:** Main Flow step 1  
**Condition:** Admin types in a search box

1. Admin searches by username, email, or full name.
2. System filters the list in real-time.
3. Use case continues.

### AF-7: Change Manager Assignment

**Branches from:** Main Flow step 6  
**Condition:** Admin changes the Manager dropdown for an EMPLOYEE

1. Admin selects a different manager from the dropdown.
2. System updates the User record's manager FK.
3. System displays success message: "Manager assignment updated."
4. Use case continues or ends.

### AF-8: Prevent Self-Deactivation

**Branches from:** Main Flow step 6  
**Condition:** Admin attempts to deactivate their own account

1. System displays error: "You cannot deactivate your own account. Ask another admin."
2. Deactivation is blocked.
3. Use case continues.

---

## Postconditions

- **On user creation:** User record is created with all fields populated, active = true, and admin is returned to list.
- **On user update:** User record is updated with new values, and admin is returned to list.
- **On user deactivation:** User's active status is false; user cannot log in until reactivated.
- **On failure:** Error message displayed; form remains open for correction.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-031 | Only ADMIN users can manage users. |
| BR-032 | Username and email must be unique across all users. |
| BR-033 | All required fields (username, email, first name, last name, role) must be non-empty. |
| BR-034 | EMPLOYEE users must have a Manager assigned; MANAGER, FINANCE, and ADMIN users do not require a manager. |
| BR-035 | Deactivated users cannot log in but their records remain in the system for audit purposes. |
| BR-036 | An admin cannot deactivate their own account. |
| BR-037 | User creation can optionally trigger an email invitation with a password reset link. |

---

## Tests

- [ ] Main Flow (create and update users) covered (steps 1–6)
- [ ] AF-1 (duplicate username) covered
- [ ] AF-2 (duplicate email) covered
- [ ] AF-3 (deactivate user) covered
- [ ] AF-4 (reactivate user) covered
- [ ] AF-5 (filter users) covered
- [ ] AF-6 (search users) covered
- [ ] AF-7 (change manager assignment) covered
- [ ] AF-8 (prevent self-deactivation) covered
- [ ] BR-031 through BR-037 covered

---

## UI Surface

| Page/Component | Access | Details |
|---|---|---|
| User Management List | Authenticated ADMIN | Table with columns: Username, Email, Full Name, Role, Manager, Active, Created, Last Login. Sortable. |
| Filter Panel | Authenticated ADMIN | Role dropdown, Active status filter (Active/Inactive/All), Team filter. |
| Search Box | Authenticated ADMIN | Text input to search by username, email, or full name. |
| Add User Button | Authenticated ADMIN | Navigates to new user form. |
| User Detail Form | Authenticated ADMIN | Form with fields: Username, Email, First Name, Last Name, Role, Manager (conditional), Active checkbox. |
| Success Message | Authenticated ADMIN | Toast/banner confirming create or update. |
| Warning Dialog | Authenticated ADMIN | Confirmation before deactivating a user. |
| Error Messages | Authenticated ADMIN | Inline form errors for duplicate username/email and required fields. |
