package com.scaloz.superadmin.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tenant_users")
public class TenantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;

    @Column(nullable = false)
    private String role; // Sub Admin, Manager, Employee, User

    @Column(name = "assigned_products", columnDefinition = "TEXT")
    private String assignedProducts; // Comma separated product IDs or codes

    @Column(name = "assigned_modules", columnDefinition = "TEXT")
    private String assignedModules; // Comma separated module IDs or names

    @Column(name = "is_sub_admin")
    private Boolean isSubAdmin = false;

    @Column(nullable = false)
    private String status = "Active";

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "work_location")
    private String workLocation;

    @Column(name = "personal_email")
    private String personalEmail;

    private String gender;

    @Column(name = "date_of_birth")
    private String dateOfBirth;

    @Column(name = "aadhar_no")
    private String aadharNo;

    @Column(name = "pan_no")
    private String panNo;

    @Column(name = "present_address", length = 1000)
    private String presentAddress;

    @Column(name = "permanent_address", length = 1000)
    private String permanentAddress;

    @Column(name = "contact_no")
    private String contactNo;

    @Column(name = "emergency_contact_no")
    private String emergencyContactNo;

    @Column(name = "blood_group")
    private String bloodGroup;

    @Column(name = "joining_date")
    private String joiningDate;

    @ManyToOne
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    /**
     * ITSM customer code this user belongs to (e.g. "NMB").
     * Nullable — MSP staff users have no customer association.
     * Stored as metadata; ownership validation happens on the ITSM side.
     */
    @Column(name = "customer_code")
    private String customerCode;

    public TenantUser() {
        // Empty constructor for JPA purposes
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAssignedProducts() {
        return assignedProducts;
    }

    public void setAssignedProducts(String assignedProducts) {
        this.assignedProducts = assignedProducts;
    }

    public String getAssignedModules() {
        return assignedModules;
    }

    public void setAssignedModules(String assignedModules) {
        this.assignedModules = assignedModules;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getWorkLocation() {
        return workLocation;
    }

    public void setWorkLocation(String workLocation) {
        this.workLocation = workLocation;
    }

    public String getPersonalEmail() {
        return personalEmail;
    }

    public void setPersonalEmail(String personalEmail) {
        this.personalEmail = personalEmail;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getAadharNo() {
        return aadharNo;
    }

    public void setAadharNo(String aadharNo) {
        this.aadharNo = aadharNo;
    }

    public String getPanNo() {
        return panNo;
    }

    public void setPanNo(String panNo) {
        this.panNo = panNo;
    }

    public String getPresentAddress() {
        return presentAddress;
    }

    public void setPresentAddress(String presentAddress) {
        this.presentAddress = presentAddress;
    }

    public String getPermanentAddress() {
        return permanentAddress;
    }

    public void setPermanentAddress(String permanentAddress) {
        this.permanentAddress = permanentAddress;
    }

    public String getContactNo() {
        return contactNo;
    }

    public void setContactNo(String contactNo) {
        this.contactNo = contactNo;
    }

    public String getEmergencyContactNo() {
        return emergencyContactNo;
    }

    public void setEmergencyContactNo(String emergencyContactNo) {
        this.emergencyContactNo = emergencyContactNo;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public String getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(String joiningDate) {
        this.joiningDate = joiningDate;
    }

    @Column(name = "must_change_password")
    private Boolean mustChangePassword = true;

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private java.time.LocalDateTime resetTokenExpiry;

    @Column(name = "failed_attempt_count", nullable = false)
    private Integer failedAttemptCount = 0;

    @Column(name = "account_locked", nullable = false)
    private Boolean accountLocked = false;

    @Column(name = "last_failed_login")
    private java.time.LocalDateTime lastFailedLogin;

    public Integer getFailedAttemptCount() {
        return failedAttemptCount != null ? failedAttemptCount : 0;
    }

    public void setFailedAttemptCount(Integer failedAttemptCount) {
        this.failedAttemptCount = failedAttemptCount;
    }

    public Boolean getAccountLocked() {
        return accountLocked != null && accountLocked;
    }

    public void setAccountLocked(Boolean accountLocked) {
        this.accountLocked = accountLocked;
    }

    public java.time.LocalDateTime getLastFailedLogin() {
        return lastFailedLogin;
    }

    public void setLastFailedLogin(java.time.LocalDateTime lastFailedLogin) {
        this.lastFailedLogin = lastFailedLogin;
    }

    public Boolean getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(Boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public java.time.LocalDateTime getResetTokenExpiry() {
        return resetTokenExpiry;
    }

    public void setResetTokenExpiry(java.time.LocalDateTime resetTokenExpiry) {
        this.resetTokenExpiry = resetTokenExpiry;
    }

    @Column(name = "password_changed_at")
    private java.time.LocalDateTime passwordChangedAt;

    @Column(name = "password_history", columnDefinition = "TEXT")
    private String passwordHistory;

    @Column(name = "known_logins", columnDefinition = "TEXT")
    private String knownLogins;

    public java.time.LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(java.time.LocalDateTime passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public String getPasswordHistory() {
        return passwordHistory;
    }

    public void setPasswordHistory(String passwordHistory) {
        this.passwordHistory = passwordHistory;
    }

    public String getKnownLogins() {
        return knownLogins;
    }

    public void setKnownLogins(String knownLogins) {
        this.knownLogins = knownLogins;
    }

    public Boolean getIsSubAdmin() {
        return isSubAdmin != null && isSubAdmin;
    }

    public void setIsSubAdmin(Boolean isSubAdmin) {
        this.isSubAdmin = isSubAdmin;
    }
}
