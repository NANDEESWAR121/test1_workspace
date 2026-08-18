/* global globalThis */
import React, { useState, useEffect, useMemo } from 'react';
import { Search, UserPlus, Info, CheckCircle, XCircle, Edit2, Upload, Download, FileText, AlertCircle } from 'lucide-react';
import * as XLSX from 'xlsx';
import api from '../api';
import PhoneInput from 'react-phone-input-2';
import 'react-phone-input-2/lib/style.css';
import PropTypes from 'prop-types';

const cleanEmpId = (empId) => {
    if (!empId) return "";
    if (typeof empId === "string" && empId.includes("_")) {
        return empId.substring(empId.indexOf("_") + 1);
    }
    return empId;
};

/* ─────────────────────────────────────────────────────────────
   Pure helpers for bulk-upload parsing (extracted to keep
   each function's cognitive complexity below the allowed 15)
───────────────────────────────────────────────────────────── */

/** Resolve a cell value from a spreadsheet row, handling star-suffixed and
 *  date-formatted column names. Returns a trimmed string or "". */
const parseRawValue = (row, fieldName) => {
    if (fieldName === 'dateOfBirth' || fieldName === 'joiningDate') {
        const candidates = [
            row[`${fieldName}(DD/MM/YYYY)*`],
            row[`${fieldName}(DD/MM/YYYY)`],
            row[`${fieldName}*`],
            row[fieldName]
        ];
        const found = candidates.find(v => v !== undefined);
        return found === undefined ? "" : String(found).trim();
    }
    const withStar = row[`${fieldName}*`];
    const plain = row[fieldName];
    const found = withStar === undefined ? plain : withStar;
    return found === undefined ? "" : String(found).trim();
};

/** Convert an Excel serial date number to YYYY-MM-DD string if needed. */
const convertExcelDateIfNeeded = (rawVal, fieldName) => {
    if ((fieldName === 'dateOfBirth' || fieldName === 'joiningDate') &&
        rawVal && !Number.isNaN(Number(rawVal)) && Number(rawVal) > 10000) {
        const d = new Date(Math.round((Number(rawVal) - 25569) * 86400 * 1000));
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    }
    return rawVal;
};

/** Parse a date-of-birth string into a Date object (null if unparseable). */
const parseDobDate = (dobStr) => {
    if (/^\d{4}-\d{2}-\d{2}$/.test(dobStr)) return new Date(dobStr);
    if (/^\d{2}\/\d{2}\/\d{4}$/.test(dobStr)) {
        const [d, m, y] = dobStr.split('/');
        return new Date(y, m - 1, d);
    }
    return new Date(dobStr);
};

/** Helper to validate regex patterns for a bulk upload row. */
const validateRowPatterns = (userObj, rowNum, errors) => {
    const emailRegex = /^[^\s@]+@[^\s@.]+(?:\.[^\s@.]+)+$/;
    if (userObj.email && !emailRegex.test(userObj.email))
        errors.push(`Row ${rowNum}: Work Email format is invalid (${userObj.email}).`);
    if (userObj.personalEmail && !emailRegex.test(userObj.personalEmail))
        errors.push(`Row ${rowNum}: Personal Email format is invalid (${userObj.personalEmail}).`);
    if (userObj.aadharNo && !/^\d{12}$/.test(userObj.aadharNo))
        errors.push(`Row ${rowNum}: Aadhaar Number must be exactly 12 digits and contain only numbers.`);
    if (userObj.panNo && !/^[A-Z]{5}\d{4}[A-Z]$/i.test(userObj.panNo))
        errors.push(`Row ${rowNum}: PAN Number must be of format: 5 letters, 4 numbers, and 1 letter (e.g., ABCDE1234F).`);
    if (userObj.contactNo && !/^\d{7,15}$/.test(userObj.contactNo))
        errors.push(`Row ${rowNum}: Contact Number must be between 7 and 15 digits.`);
    if (userObj.emergencyContactNo && !/^\d{7,15}$/.test(userObj.emergencyContactNo))
        errors.push(`Row ${rowNum}: Emergency Contact Number must be between 7 and 15 digits.`);
};

/** Validate one parsed user object and push error strings into `errors`. */
const validateBulkRow = (userObj, rowNum, mandatoryFields, errors) => {
    mandatoryFields.forEach(f => {
        if (!userObj[f.key]) errors.push(`Row ${rowNum}: ${f.label} is missing.`);
    });

    validateRowPatterns(userObj, rowNum, errors);

    if (userObj.dateOfBirth) {
        const dobDate = parseDobDate(userObj.dateOfBirth.trim());
        if (Number.isNaN(dobDate.getTime())) {
            errors.push(`Row ${rowNum}: Date of Birth format is invalid. Please use YYYY-MM-DD or DD/MM/YYYY.`);
        } else {
            const today = new Date();
            today.setHours(0, 0, 0, 0);
            dobDate.setHours(0, 0, 0, 0);
            if (dobDate > today)
                errors.push(`Row ${rowNum}: Date of Birth cannot be in the future (${userObj.dateOfBirth.trim()}).`);
        }
    }
};

/** Map JSON rows from the spreadsheet into user objects and collect errors. */
const processBulkRows = (json, expectedFields, mandatoryFields, tenantId) => {
    const rows = [];
    const errors = [];
    json.forEach((row, index) => {
        const rowNum = index + 2;
        const userObj = { tenant: { id: tenantId }, isSubAdmin: false, assignedModules: "" };
        expectedFields.forEach(f => {
            const rawVal = convertExcelDateIfNeeded(parseRawValue(row, f), f);
            userObj[f] = rawVal;
        });
        validateBulkRow(userObj, rowNum, mandatoryFields, errors);
        rows.push(userObj);
    });
    return { rows, errors };
};

/** Validate formData before submitting and return an error message string,
 *  or null if everything is valid. */
const validateFormData = (formData, contactDialCode, emergencyDialCode) => {
    if (formData.aadharNo && !/^\d{12}$/.test(formData.aadharNo))
        return "❌ Aadhaar Number must be exactly 12 digits and contain only numbers.";
    if (formData.panNo && !/^[A-Z]{5}\d{4}[A-Z]$/.test(formData.panNo))
        return "❌ PAN Number must be of format: 5 letters, 4 numbers, and 1 letter (e.g., ABCDE1234F).";
    if (formData.dateOfBirth) {
        const dob = new Date(formData.dateOfBirth);
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        dob.setHours(0, 0, 0, 0);
        if (dob > today) return "❌ Date of Birth cannot be in the future.";
    }
    return null;
};

const getInitialFormData = (tenantId) => ({
    employeeId: '',
    firstName: '',
    lastName: '',
    email: '',
    role: '',
    status: 'Active',
    workLocation: '',
    personalEmail: '',
    gender: 'Male',
    dateOfBirth: '',
    aadharNo: '',
    panNo: '',
    presentAddress: '',
    permanentAddress: '',
    contactNo: '',
    emergencyContactNo: '',
    bloodGroup: 'A+',
    joiningDate: '',
    assignedProducts: '',
    tenant: { id: tenantId }
});

const mapUserToFormData = (user, tenantId) => ({
    employeeId: cleanEmpId(user.employeeId) || '',
    firstName: user.firstName || '',
    lastName: user.lastName || '',
    email: user.email || '',
    role: user.role || 'Employee',
    status: user.status || 'Active',
    workLocation: user.workLocation || '',
    personalEmail: user.personalEmail || '',
    gender: user.gender || 'Male',
    dateOfBirth: user.dateOfBirth || '',
    aadharNo: user.aadharNo || '',
    panNo: user.panNo || '',
    presentAddress: user.presentAddress || '',
    permanentAddress: user.permanentAddress || '',
    contactNo: user.contactNo || '',
    emergencyContactNo: user.emergencyContactNo || '',
    bloodGroup: user.bloodGroup || 'A+',
    joiningDate: user.joiningDate || '',
    assignedProducts: user.assignedProducts || '',
    tenant: { id: tenantId }
});

const Onboarding = ({ tenantId }) => {
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState("");
    const [debouncedSearchQuery, setDebouncedSearchQuery] = useState("");
    const [showModal, setShowModal] = useState(false);
    const [currentStep, setCurrentStep] = useState(1);
    const [tenantProducts, setTenantProducts] = useState([]);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [editingUserId, setEditingUserId] = useState(null);
    const [showBulkModal, setShowBulkModal] = useState(false);
    const [bulkFile, setBulkFile] = useState(null);
    const [bulkData, setBulkData] = useState([]);
    const [bulkErrors, setBulkErrors] = useState([]);
    const [bulkSubmitting, setBulkSubmitting] = useState(false);
    const [bulkResult, setBulkResult] = useState(null);
    const [contactDialCode, setContactDialCode] = useState("91");
    const [emergencyDialCode, setEmergencyDialCode] = useState("91");
    const [sameAsPresent, setSameAsPresent] = useState(false);
    const [formData, setFormData] = useState(() => getInitialFormData(tenantId));

    useEffect(() => {
        if (tenantId) {
            fetchUsers();
            fetchTenantProducts();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [tenantId]);

    useEffect(() => {
        const timer = setTimeout(() => setDebouncedSearchQuery(searchQuery), 300);
        return () => clearTimeout(timer);
    }, [searchQuery]);

    const fetchTenantProducts = async () => {
        try {
            const response = await api.get(`/tenant/${tenantId}/products`);
            setTenantProducts(response.data);
        } catch (error) {
            console.error("Error loading tenant products from API:", error);
        }
    };



const handleDownloadTemplate = async () => {
    try {
        const response = await api.get('/tenant-users/template-fields');
        const fields = response.data;

        const mandatoryFieldsList = new Set(["employeeId", "firstName", "lastName", "email", "role", "status", "gender", "dateOfBirth", "aadharNo", "panNo", "bloodGroup", "joiningDate", "assignedProducts"]);
        const formattedFields = fields.map(f => {
            let name = f;
            if (f === 'dateOfBirth') {
                name = 'dateOfBirth(DD/MM/YYYY)';
            } else if (f === 'joiningDate') {
                name = 'joiningDate(DD/MM/YYYY)';
            }
            return mandatoryFieldsList.has(f) ? `${name}*` : name;
        });

        // Create a worksheet
        const ws = XLSX.utils.aoa_to_sheet([formattedFields]);
        
        // Apply bold style to headers (in case styling is supported by local Excel viewer/writer)
        for (let i = 0; i < formattedFields.length; i++) {
            const cellRef = XLSX.utils.encode_cell({ r: 0, c: i });
            if (ws[cellRef]) {
                ws[cellRef].s = {
                    font: {
                        bold: true
                    }
                };
            }
        }

        // Create a new workbook
        const wb = XLSX.utils.book_new();
        XLSX.utils.book_append_sheet(wb, ws, "Onboarding Template");
        
        // Generate buffer and trigger download as Excel file
        XLSX.writeFile(wb, "user_onboarding_template.xlsx");
    } catch (error) {
        console.error("Error generating Excel template:", error);
    }
};

    const handleFileChange = async (e) => {
        const file = e.target.files[0];
        if (!file) return;
        setBulkFile(file);
        setBulkErrors([]);
        setBulkResult(null);

        try {
            const data = await file.arrayBuffer();
            const workbook = XLSX.read(data, { type: 'array' });
            const worksheet = workbook.Sheets[workbook.SheetNames[0]];
            const json = XLSX.utils.sheet_to_json(worksheet, { defval: "" });

            if (json.length === 0) {
                setBulkErrors(["The uploaded file contains no data rows."]);
                setBulkData([]);
                return;
            }

            const fieldsResponse = await api.get('/tenant-users/template-fields');
            const expectedFields = fieldsResponse.data;

            const mandatoryFields = [
                { key: "employeeId", label: "Employee ID" },
                { key: "firstName", label: "First Name" },
                { key: "lastName", label: "Last Name" },
                { key: "email", label: "Work Email" },
                { key: "role", label: "Designation" },
                { key: "status", label: "Status" },
                { key: "gender", label: "Gender" },
                { key: "dateOfBirth", label: "Date of Birth" },
                { key: "aadharNo", label: "Aadhaar Number" },
                { key: "panNo", label: "PAN Number" },
                { key: "bloodGroup", label: "Blood Group" },
                { key: "joiningDate", label: "Joining Date" },
                { key: "assignedProducts", label: "Products selection" }
            ];

            // Check for missing required column headers
            const fileHeaders = new Set(Object.keys(json[0]).map(h => h.trim().replaceAll('*', '')));
            const missingRequiredHeaders = mandatoryFields
                .filter(f => {
                    if (f.key === 'dateOfBirth')
                        return !fileHeaders.has("dateOfBirth(DD/MM/YYYY)") && !fileHeaders.has("dateOfBirth");
                    if (f.key === 'joiningDate')
                        return !fileHeaders.has("joiningDate(DD/MM/YYYY)") && !fileHeaders.has("joiningDate");
                    return !fileHeaders.has(f.key);
                })
                .map(f => {
                    if (f.key === 'dateOfBirth') {
                        return `${f.label} (dateOfBirth(DD/MM/YYYY))`;
                    }
                    if (f.key === 'joiningDate') {
                        return `${f.label} (joiningDate(DD/MM/YYYY))`;
                    }
                    return `${f.label} (${f.key})`;
                });

            if (missingRequiredHeaders.length > 0) {
                setBulkErrors([`Missing required column headers: ${missingRequiredHeaders.join(", ")}`]);
                setBulkData([]);
                return;
            }

            // Map rows and validate using extracted helpers
            const { rows, errors } = processBulkRows(json, expectedFields, mandatoryFields, tenantId);

            if (errors.length > 0) {
                setBulkErrors(errors);
                setBulkData([]);
            } else {
                setBulkData(rows);
            }
        } catch (err) {
            console.error("Error parsing file:", err);
            setBulkErrors(["Failed to read or parse the file. Please ensure it is a valid CSV or Excel file."]);
            setBulkData([]);
        }
    };

    const handleBulkSubmit = async () => {
        if (bulkData.length === 0 || bulkSubmitting) return;
        setBulkSubmitting(true);
        setBulkErrors([]);
        try {
            const response = await api.post('/tenant-users/bulk-onboard', bulkData);
            setBulkResult(response.data);
            fetchUsers();
        } catch (error) {
            console.error("Error submitting bulk onboarding:", error);
            setBulkErrors(["An error occurred on the server during bulk onboarding. Please check your data."]);
        } finally {
            setBulkSubmitting(false);
        }
    };

    const resetBulkState = () => {
        setBulkFile(null);
        setBulkData([]);
        setBulkErrors([]);
        setBulkResult(null);
        setShowBulkModal(false);
    };

    const fetchUsers = async () => {
        try {
            const response = await api.get(`/tenant-users/tenant/${tenantId}`);
            setUsers(response.data);
            setLoading(false);
        } catch (error) {
            console.error("Error fetching users:", error);
            setLoading(false);
        }
    };

    const handleCheckboxChange = (e) => {
        const checked = e.target.checked;
        setSameAsPresent(checked);
        if (checked) {
            setFormData(prev => ({
                ...prev,
                permanentAddress: prev.presentAddress
            }));
        }
    };

    const resetForm = () => {
        setFormData(getInitialFormData(tenantId));
        setEditingUserId(null);
        setCurrentStep(1);
        setContactDialCode("91");
        setEmergencyDialCode("91");
        setSameAsPresent(false);
    };

    const handleFormSubmit = async (e) => {
        e.preventDefault();
        if (isSubmitting) return;
        setIsSubmitting(true);

        const validationError = validateFormData(formData, contactDialCode, emergencyDialCode);
        if (validationError) {
            alert(validationError);
            setIsSubmitting(false);
            return;
        }

        const cleanedContactNo = (!formData.contactNo || formData.contactNo === contactDialCode) ? "" : formData.contactNo;
        const cleanedEmergencyContactNo = (!formData.emergencyContactNo || formData.emergencyContactNo === emergencyDialCode) ? "" : formData.emergencyContactNo;
        const submitData = { ...formData, contactNo: cleanedContactNo, emergencyContactNo: cleanedEmergencyContactNo };

        try {
            if (editingUserId) {
                await api.put(`/tenant-users/${editingUserId}`, submitData);
            } else {
                await api.post('/tenant-users/onboard', submitData);
            }
            setShowModal(false);
            fetchUsers();
            resetForm();
        } catch (error) {
            const msg = error.response?.data?.message;
            if (msg) {
                alert(`❌ ${msg}`);
            } else {
                alert(`❌ Failed to ${editingUserId ? 'update' : 'onboard'} user. Please check your network or try again.`);
            }
            console.error("Error submitting user form:", error);
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleEditClick = (user) => {
        setEditingUserId(user.id);
        setFormData(mapUserToFormData(user, tenantId));
        setContactDialCode("91");
        setEmergencyDialCode("91");
        setSameAsPresent(!!(user.presentAddress && user.presentAddress === user.permanentAddress));
        setCurrentStep(1);
        setShowModal(true);
    };

    const handleDelete = async (id) => {
        if (globalThis.confirm("Are you sure you want to delete this user?")) {
            try {
                await api.delete(`/tenant-users/${id}`);
                fetchUsers();
            } catch (error) {
                console.error("Error deleting user:", error);
            }
        }
    };

    const filteredUsers = useMemo(() => {
        return users.filter(u =>
            (`${u.firstName || ""} ${u.lastName || ""}`).toLowerCase().includes(debouncedSearchQuery.toLowerCase()) ||
            cleanEmpId(u.employeeId).toLowerCase().includes(debouncedSearchQuery.toLowerCase()) ||
            (u.email || "").toLowerCase().includes(debouncedSearchQuery.toLowerCase())
        );
    }, [users, debouncedSearchQuery]);

    const handleProductToggle = (productCode) => {
        const currentProducts = formData.assignedProducts ? formData.assignedProducts.split(',') : [];
        const codeStr = productCode ? productCode.toString() : '';
        if (!codeStr) return;

        let newProducts;
        if (currentProducts.includes(codeStr)) {
            newProducts = currentProducts.filter(c => c !== codeStr);
        } else {
            newProducts = [...currentProducts, codeStr];
        }
        setFormData({ ...formData, assignedProducts: newProducts.join(',') });
    };

    return (
        <div className="onboarding-container">
            <div className="launchpad-view-header onboarding-header-section">
                <div className="onboarding-header-top">
                    <div>
                        <h1>Onboarding</h1>
                        <p>Manage and onboard your organization's members.</p>
                    </div>
                    <div className="onboarding-header-actions">
                        <button className="invite-btn bulk-btn" onClick={() => { resetBulkState(); setShowBulkModal(true); }}>
                            <Upload size={18} />
                            <span>Bulk Onboard</span>
                        </button>
                        <button className="invite-btn new-btn" onClick={() => { resetForm(); setShowModal(true); }}>
                            <UserPlus size={18} />
                            <span>Onboard New User</span>
                        </button>
                    </div>
                </div>
            </div>

            <div className="onboarding-controls">
                <div className="search-input-wrapper header-search" style={{ flex: 1, maxWidth: '400px' }}>
                    <Search size={16} className="search-icon-inside" />
                    <input
                        type="text"
                        placeholder="Search by name, ID or email..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                    />
                </div>
            </div>

            <div className="users-table-container">
                <table className="users-table">
                    <thead>
                        <tr>
                            <th>User Details</th>
                            <th>Employee ID</th>
                            <th>Designation</th>
                            <th>Status</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        {(() => {
                            if (loading) {
                                return <tr><td colSpan="5" style={{ textAlign: 'center', padding: '40px' }}>Loading users...</td></tr>;
                            }
                            if (filteredUsers.length === 0) {
                                return <tr><td colSpan="5" style={{ textAlign: 'center', padding: '40px' }}>No users found.</td></tr>;
                            }
                            return <>{filteredUsers.map(user => (
                                <tr key={user.id}>
                                    <td>
                                        <div className="user-info-cell">
                                            <div className="user-avatar-small">
                                                {(`${user.firstName || user.lastName || ""}`).trim().charAt(0).toUpperCase()}
                                            </div>
                                            <div className="user-meta">
                                                <span className="user-name">{`${user.firstName || ""} ${user.lastName || ""}`.trim()}</span>
                                                <span className="user-email">{user.email}</span>
                                            </div>
                                        </div>
                                    </td>
                                    <td><span className="emp-id-badge">{cleanEmpId(user.employeeId)}</span></td>
                                    <td>
                                        <div className="role-chip">
                                            <span>{user.role}</span>
                                        </div>
                                    </td>
                                    <td>
                                        <span className={`status-badge ${user.status.toLowerCase()}`}>
                                            {user.status === 'Active' ? <CheckCircle size={12} /> : <XCircle size={12} />}
                                            {user.status}
                                        </span>
                                    </td>
                                    <td>
                                        <div className="action-btns">
                                            <button className="icon-btn edit" onClick={() => handleEditClick(user)} title="Edit User"><Edit2 size={16} /></button>
                                        </div>
                                    </td>
                                </tr>
                            ))}</>;
                        })()}
                    </tbody>
                </table>
            </div>

            {showModal && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <div className="modal-header">
                            <h2>{(() => {
                                if (editingUserId) return "Edit User Details";
                                if (currentStep === 1) return "Select Products";
                                return "Onboard New User";
                            })()}</h2>
                            <button className="close-btn" onClick={() => { setShowModal(false); resetForm(); }}><XCircle size={24} /></button>
                        </div>
                        <form onSubmit={handleFormSubmit} className="onboard-form">
                            <div className="form-sections-container">

                                {currentStep === 1 ? (
                                    <div className="form-section">
                                        <h3>Products Access</h3>
                                        <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '16px' }}>
                                            Select the products this user will have access to.
                                        </p>
                                        <div className="product-selection-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '16px' }}>
                                            {tenantProducts.map(product => (
                                                <button
                                                    key={product.productId}
                                                    type="button"
                                                    onClick={() => handleProductToggle(product.productCode)}
                                                    style={{
                                                        padding: '16px',
                                                        border: `2px solid ${(formData.assignedProducts?.split(',').includes(product.productCode?.toString())) ? '#2563EB' : 'var(--border-color)'}`,
                                                        borderRadius: '8px',
                                                        cursor: 'pointer',
                                                        backgroundColor: (formData.assignedProducts?.split(',').includes(product.productCode?.toString())) ? '#EFF6FF' : '#FFF',
                                                        transition: 'all 0.2s',
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        gap: '12px',
                                                        width: '100%',
                                                        textAlign: 'left',
                                                        fontFamily: 'inherit'
                                                    }}
                                                >
                                                    <div style={{
                                                        width: '20px',
                                                        height: '20px',
                                                        borderRadius: '4px',
                                                        border: `2px solid ${(formData.assignedProducts?.split(',').includes(product.productCode?.toString())) ? '#2563EB' : '#CBD5E1'}`,
                                                        backgroundColor: (formData.assignedProducts?.split(',').includes(product.productCode?.toString())) ? '#2563EB' : 'transparent',
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'center'
                                                    }}>
                                                        {(formData.assignedProducts?.split(',').includes(product.productCode?.toString())) && <CheckCircle size={14} color="#FFF" />}
                                                    </div>
                                                    <span style={{ fontWeight: '600', fontSize: '14px', color: 'var(--text-main)' }}>{product.productName}</span>
                                                </button>
                                            ))}
                                            {tenantProducts.length === 0 && (
                                                <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>No products available for this tenant.</p>
                                            )}
                                        </div>
                                    </div>
                                ) : (
                                    <>
                                        <div className="form-section">
                                            <h3>Work & Account Details</h3>
                                            <div className="form-grid">
                                                <div className="form-group">
                                                    <label htmlFor="onboard-firstName">First Name <span style={{ color: '#DC2626' }}>*</span></label>
                                                    <input
                                                        id="onboard-firstName"
                                                        type="text"
                                                        required
                                                        maxLength={50}
                                                        value={formData.firstName}
                                                        onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                                                        placeholder="e.g. John"
                                                    />
                                                    <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: '11px', color: '#9CA3AF', marginTop: '2px' }}>
                                                        {(formData.firstName || "").length}/50
                                                    </div>
                                                </div>
                                                <div className="form-group">
                                                    <label htmlFor="onboard-lastName">Last Name <span style={{ color: '#DC2626' }}>*</span></label>
                                                    <input
                                                        id="onboard-lastName"
                                                        type="text"
                                                        required
                                                        maxLength={50}
                                                        value={formData.lastName}
                                                        onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                                                        placeholder="e.g. Doe"
                                                    />
                                                    <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: '11px', color: '#9CA3AF', marginTop: '2px' }}>
                                                        {(formData.lastName || "").length}/50
                                                    </div>
                                                </div>
                                                <div className="form-group">
                                                    <label htmlFor="onboard-employeeId">Employee ID <span style={{ color: '#DC2626' }}>*</span></label>
                                                    <input
                                                        id="onboard-employeeId"
                                                        type="text"
                                                        required
                                                        maxLength={50}
                                                        value={formData.employeeId}
                                                        onChange={(e) => setFormData({ ...formData, employeeId: e.target.value })}
                                                        placeholder="e.g. EMP123"
                                                        disabled={!!editingUserId}
                                                        style={editingUserId ? { backgroundColor: '#E2E8F0', cursor: 'not-allowed', color: '#64748B' } : {}}
                                                    />
                                                    <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: '11px', color: '#9CA3AF', marginTop: '2px' }}>
                                                        {(formData.employeeId || "").length}/50
                                                    </div>
                                                </div>
                                                <div className="form-group">
                                                    <label htmlFor="onboard-email">Work Email <span style={{ color: '#DC2626' }}>*</span></label>
                                                    <input
                                                        id="onboard-email"
                                                        type="email"
                                                        required
                                                        maxLength={100}
                                                        value={formData.email}
                                                        onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                                                        placeholder="e.g. john@company.com"
                                                        disabled={!!editingUserId}
                                                        style={editingUserId ? { backgroundColor: '#E2E8F0', cursor: 'not-allowed', color: '#64748B' } : {}}
                                                    />
                                                    <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: '11px', color: '#9CA3AF', marginTop: '2px' }}>
                                                        {(formData.email || "").length}/100
                                                    </div>
                                                </div>
                                                <div className="form-group">
                                                    <label htmlFor="onboard-role">Designation <span style={{ color: '#DC2626' }}>*</span></label>
                                                    <input
                                                        id="onboard-role"
                                                        type="text"
                                                        placeholder="e.g. Software Engineer"
                                                        maxLength={50}
                                                        value={formData.role}
                                                        onChange={(e) => setFormData({ ...formData, role: e.target.value })}
                                                        required
                                                    />
                                                    <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: '11px', color: '#9CA3AF', marginTop: '2px' }}>
                                                        {(formData.role || "").length}/50
                                                    </div>
                                                </div>
                                                <div className="form-group">
                                                    <label htmlFor="onboard-workLocation">Work Location <span style={{ color: '#DC2626' }}>*</span></label>
                                                    <input
                                                        id="onboard-workLocation"
                                                        type="text"
                                                        placeholder="e.g. Hyderabad, Bangalore"
                                                        maxLength={100}
                                                        value={formData.workLocation}
                                                        onChange={(e) => setFormData({ ...formData, workLocation: e.target.value })}
                                                        required
                                                    />
                                                    <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: '11px', color: '#9CA3AF', marginTop: '2px' }}>
                                                        {(formData.workLocation || "").length}/100
                                                    </div>
                                                </div>
                                                <div className="form-group">
                                                    <label htmlFor="onboard-joiningDate">Date of Joining <span style={{ color: '#DC2626' }}>*</span></label>
                                                    <input
                                                        id="onboard-joiningDate"
                                                        type="date"
                                                        required
                                                        value={formData.joiningDate}
                                                        onChange={(e) => setFormData({ ...formData, joiningDate: e.target.value })}
                                                    />
                                                </div>
                                                <div className="form-group">
                                                    <label htmlFor="onboard-status">Status <span style={{ color: '#DC2626' }}>*</span></label>
                                                    <select
                                                        id="onboard-status"
                                                        value={formData.status}
                                                        onChange={(e) => setFormData({ ...formData, status: e.target.value })}
                                                        required
                                                    >
                                                        <option value="Active">Active</option>
                                                        <option value="Inactive">Inactive</option>
                                                    </select>
                                                </div>
                                            </div>
                                        </div>

                                        <div className="form-section">
                                            <h3>Personal Details</h3>
                                            <div className="form-grid">
                                                <div className="form-group">
                                                    <label htmlFor="onboard-personalEmail">Personal Email</label>
                                                    <input
                                                        id="onboard-personalEmail"
                                                        type="email"
                                                        maxLength={100}
                                                        value={formData.personalEmail}
                                                        onChange={(e) => setFormData({ ...formData, personalEmail: e.target.value })}
                                                        placeholder="e.g. john.doe@gmail.com"
                                                    />
                                                    <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: '11px', color: '#9CA3AF', marginTop: '2px' }}>
                                                        {(formData.personalEmail || "").length}/100
                                                    </div>
                                                </div>
                                                <div className="form-group">
                                                    <label htmlFor="onboard-contactNo">Contact Number</label>
                                                    <PhoneInput
                                                        country="in"
                                                        value={formData.contactNo}
                                                        onChange={(phone, country) => {
                                                            setFormData({ ...formData, contactNo: phone });
                                                            if (country?.dialCode) {
                                                                setContactDialCode(country.dialCode);
                                                            }
                                                        }}
                                                        enableSearch={true}
                                                        searchPlaceholder="Search country..."
                                                        countryCodeEditable={false}
                                                        inputProps={{ id: "onboard-contactNo" }}
                                                    />
                                                </div>
                                                <div className="form-group">
                                                    <label htmlFor="onboard-emergencyContactNo">Emergency Contact Number</label>
                                                    <PhoneInput
                                                        country="in"
                                                        value={formData.emergencyContactNo}
                                                        onChange={(phone, country) => {
                                                            setFormData({ ...formData, emergencyContactNo: phone });
                                                            if (country?.dialCode) {
                                                                setEmergencyDialCode(country.dialCode);
                                                            }
                                                        }}
                                                        enableSearch={true}
                                                        searchPlaceholder="Search country..."
                                                        countryCodeEditable={false}
                                                        inputProps={{ id: "onboard-emergencyContactNo" }}
                                                    />
                                                </div>
                                                <div className="form-group">
                                                    <label htmlFor="onboard-gender">Gender <span style={{ color: '#DC2626' }}>*</span></label>
                                                    <select
                                                        id="onboard-gender"
                                                        value={formData.gender}
                                                        onChange={(e) => setFormData({ ...formData, gender: e.target.value })}
                                                    >
                                                        <option value="Male">Male</option>
                                                        <option value="Female">Female</option>
                                                        <option value="Other">Other</option>
                                                    </select>
                                                </div>
                                                <div className="form-group">
                                                    <label htmlFor="onboard-dateOfBirth">Date of Birth <span style={{ color: '#DC2626' }}>*</span></label>
                                                    <input
                                                        id="onboard-dateOfBirth"
                                                        type="date"
                                                        required
                                                        value={formData.dateOfBirth}
                                                        onChange={(e) => setFormData({ ...formData, dateOfBirth: e.target.value })}
                                                        max={(() => {
                                                            const d = new Date();
                                                            return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
                                                        })()}
                                                    />
                                                </div>
                                                <div className="form-group">
                                                    <label htmlFor="onboard-bloodGroup">Blood Group <span style={{ color: '#DC2626' }}>*</span></label>
                                                    <select
                                                        id="onboard-bloodGroup"
                                                        value={formData.bloodGroup}
                                                        onChange={(e) => setFormData({ ...formData, bloodGroup: e.target.value })}
                                                    >
                                                        <option value="A+">A+</option>
                                                        <option value="A-">A-</option>
                                                        <option value="B+">B+</option>
                                                        <option value="B-">B-</option>
                                                        <option value="AB+">AB+</option>
                                                        <option value="AB-">AB-</option>
                                                        <option value="O+">O+</option>
                                                        <option value="O-">O-</option>
                                                    </select>
                                                </div>
                                            </div>
                                        </div>

                                        <div className="form-section">
                                            <h3>Statutory & Address Details</h3>
                                            <div className="form-grid">
                                                <div className="form-group">
                                                    <label htmlFor="onboard-aadharNo">Aadhaar Number <span style={{ color: '#DC2626' }}>*</span></label>
                                                    <input
                                                        id="onboard-aadharNo"
                                                        type="text"
                                                        maxLength="12"
                                                        required
                                                        value={formData.aadharNo}
                                                        onChange={(e) => {
                                                            const val = e.target.value.replace(/\D/g, '');
                                                            setFormData({ ...formData, aadharNo: val });
                                                        }}
                                                        placeholder="e.g. 123456789012"
                                                        disabled={!!editingUserId}
                                                        style={editingUserId ? { backgroundColor: '#E2E8F0', cursor: 'not-allowed', color: '#64748B' } : {}}
                                                    />
                                                </div>
                                                <div className="form-group">
                                                    <label htmlFor="onboard-panNo">PAN Number <span style={{ color: '#DC2626' }}>*</span></label>
                                                    <input
                                                        id="onboard-panNo"
                                                        type="text"
                                                        maxLength="10"
                                                        required
                                                        value={formData.panNo}
                                                        onChange={(e) => {
                                                            let val = e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
                                                            if (val.length > 10) val = val.substring(0, 10);
                                                            setFormData({ ...formData, panNo: val });
                                                        }}
                                                        placeholder="e.g. ABCDE1234F"
                                                        disabled={!!editingUserId}
                                                        style={editingUserId ? { backgroundColor: '#E2E8F0', cursor: 'not-allowed', color: '#64748B' } : {}}
                                                    />
                                                </div>
                                                <div className="form-group" style={{ gridColumn: 'span 2' }}>
                                                    <label htmlFor="onboard-presentAddress">Present Address</label>
                                                    <input
                                                        id="onboard-presentAddress"
                                                        type="text"
                                                        maxLength={1000}
                                                        value={formData.presentAddress}
                                                        onChange={(e) => {
                                                            const val = e.target.value;
                                                            setFormData(prev => ({
                                                                ...prev,
                                                                presentAddress: val,
                                                                permanentAddress: sameAsPresent ? val : prev.permanentAddress
                                                            }));
                                                        }}
                                                        placeholder="Street address, City, State"
                                                    />
                                                    <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: '11px', color: '#9CA3AF', marginTop: '2px' }}>
                                                        {(formData.presentAddress || "").length}/1000
                                                    </div>
                                                </div>
                                                <div className="form-group" style={{ gridColumn: 'span 2' }}>
                                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                                        <label htmlFor="onboard-permanentAddress" style={{ margin: 0 }}>Permanent Address</label>
                                                        <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', fontWeight: '500', color: 'var(--text-sub)', cursor: 'pointer', userSelect: 'none', margin: 0 }}>
                                                            <input
                                                                type="checkbox"
                                                                checked={sameAsPresent}
                                                                onChange={handleCheckboxChange}
                                                                style={{ cursor: 'pointer', margin: 0, width: '14px', height: '14px' }}
                                                            />
                                                            <span>Same as Present Address</span>
                                                        </label>
                                                    </div>
                                                    <input
                                                        id="onboard-permanentAddress"
                                                        type="text"
                                                        maxLength={1000}
                                                        value={formData.permanentAddress}
                                                        onChange={(e) => setFormData({ ...formData, permanentAddress: e.target.value })}
                                                        placeholder={sameAsPresent ? "Same as present address" : "Street address, City, State"}
                                                        disabled={sameAsPresent}
                                                        style={sameAsPresent ? { backgroundColor: '#E2E8F0', cursor: 'not-allowed', color: '#64748B' } : {}}
                                                    />
                                                    <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: '11px', color: '#9CA3AF', marginTop: '2px' }}>
                                                        {(formData.permanentAddress || "").length}/1000
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </>
                                )}

                            </div>
                            <div className="modal-footer">
                                <button type="button" className="cancel-btn" onClick={() => { setShowModal(false); resetForm(); }}>Cancel</button>

                                {currentStep === 1 ? (
                                    <button type="button" className="submit-btn" onClick={() => setCurrentStep(2)}>
                                        Next
                                    </button>
                                ) : (
                                    <>
                                        <button type="button" className="cancel-btn" onClick={() => setCurrentStep(1)} style={{ marginRight: 'auto' }}>
                                            Back
                                        </button>
                                        <button type="submit" className="submit-btn" disabled={isSubmitting}>
                                            {(() => {
                                                if (isSubmitting) {
                                                    return editingUserId ? 'Updating...' : 'Onboarding...';
                                                }
                                                return editingUserId ? 'Update User' : 'Onboard User';
                                            })()}
                                        </button>
                                    </>
                                )}
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {showBulkModal && (
                <div className="modal-overlay">
                    <div className="modal-content" style={{ width: '650px' }}>
                        <div className="modal-header">
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                <Upload size={22} color="#10B981" />
                                <h2>Bulk User Onboarding</h2>
                            </div>
                            <button className="close-btn" onClick={resetBulkState}><XCircle size={24} /></button>
                        </div>

                        <div className="form-sections-container" style={{ padding: '24px' }}>
                            {/* Instructions */}
                            <div style={{ display: 'flex', gap: '16px', backgroundColor: '#F8FAFC', padding: '16px', borderRadius: '12px', marginBottom: '20px', border: '1px solid #E2E8F0' }}>
                                <Info size={24} color="#3B82F6" style={{ flexShrink: 0 }} />
                                <div>
                                    <h4 style={{ margin: '0 0 4px 0', fontSize: '14px', fontWeight: '700', color: '#1E293B' }}>Instructions</h4>
                                    <p style={{ margin: 0, fontSize: '13px', color: '#475569', lineHeight: '1.5' }}>
                                        Download the onboarding CSV template, fill in all dynamic column details accurately, then select and upload the file below. Supported formats: <strong>CSV</strong> and <strong>Excel (.xlsx, .xls)</strong>.
                                    </p>
                                </div>
                            </div>

                            {/* Download Button */}
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px', border: '1px dashed #CBD5E1', borderRadius: '8px', marginBottom: '24px', backgroundColor: '#FFF' }}>
                                <div>
                                    <span style={{ fontWeight: '600', fontSize: '13px', color: '#1E293B' }}>Dynamic Template</span>
                                    <p style={{ margin: '2px 0 0 0', fontSize: '11px', color: '#64748B' }}>Includes all columns dynamically synchronized from the User schema.</p>
                                </div>
                                <button className="cancel-btn" onClick={handleDownloadTemplate} style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 16px', fontSize: '13px', borderColor: '#3B82F6', color: '#3B82F6' }}>
                                    <Download size={14} />
                                    <span>Download Template</span>
                                </button>
                            </div>

                            {/* Upload Area */}
                            <label style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '32px 16px', border: '2px dashed #E2E8F0', borderRadius: '12px', backgroundColor: '#F8FAFC', position: 'relative', cursor: 'pointer', transition: 'border-color 0.2s' }}
                                onDragOver={(e) => e.preventDefault()}
                                onDrop={(e) => {
                                    e.preventDefault();
                                    if (e.dataTransfer?.files?.[0]) {
                                        handleFileChange({ target: { files: e.dataTransfer.files } });
                                    }
                                }}
                            >
                                <input
                                    type="file"
                                    accept=".csv, .xlsx, .xls"
                                    onChange={handleFileChange}
                                    style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, opacity: 0, cursor: 'pointer' }}
                                />
                                <div style={{ width: '48px', height: '48px', borderRadius: '50%', backgroundColor: '#ECFDF5', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '12px' }}>
                                    <FileText size={24} color="#10B981" />
                                </div>
                                <span style={{ fontSize: '14px', fontWeight: '600', color: '#334155' }}>
                                    {bulkFile ? bulkFile.name : "Choose CSV or Excel file, or drag it here"}
                                </span>
                                <span style={{ fontSize: '11px', color: '#94A3B8', marginTop: '4px' }}>
                                    {bulkFile ? `${(bulkFile.size / 1024).toFixed(1)} KB` : "Max file size: 5MB"}
                                </span>
                            </label>

                            {/* Local Validation Success Details */}
                            {bulkData.length > 0 && !bulkResult && (
                                <div style={{ marginTop: '20px', padding: '12px 16px', backgroundColor: '#ECFDF5', borderRadius: '8px', border: '1px solid #A7F3D0', display: 'flex', alignItems: 'center', gap: '10px' }}>
                                    <CheckCircle size={18} color="#059669" />
                                    <span style={{ fontSize: '13px', fontWeight: '600', color: '#065F46' }}>
                                        File parsed successfully! {bulkData.length} users ready for onboarding.
                                    </span>
                                </div>
                            )}

                            {/* Local Parse Errors */}
                            {bulkErrors.length > 0 && (
                                <div style={{ marginTop: '20px', padding: '16px', backgroundColor: '#FEF2F2', borderRadius: '12px', border: '1px solid #FEE2E2' }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                                        <AlertCircle size={18} color="#DC2626" />
                                        <span style={{ fontSize: '13px', fontWeight: '700', color: '#991B1B' }}>Validation Failures</span>
                                    </div>
                                    <ul style={{ margin: 0, paddingLeft: '20px', fontSize: '12px', color: '#B91C1C', maxHeight: '120px', overflowY: 'auto', lineHeight: '1.6' }}>
                                        {bulkErrors.map((err) => (
                                            <li key={err}>{err}</li>
                                        ))}
                                    </ul>
                                </div>
                            )}

                            {/* Server Bulk Result Output */}
                            {bulkResult && (
                                <div style={{ marginTop: '20px', padding: '20px', backgroundColor: '#F8FAFC', borderRadius: '12px', border: '1px solid #E2E8F0' }}>
                                    <h4 style={{ margin: '0 0 12px 0', fontSize: '14px', fontWeight: '700', color: '#1E293B', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Import Results Summary</h4>
                                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '16px' }}>
                                        <div style={{ padding: '12px', backgroundColor: '#ECFDF5', borderRadius: '8px', border: '1px solid #A7F3D0', textAlign: 'center' }}>
                                            <span style={{ display: 'block', fontSize: '24px', fontWeight: '800', color: '#059669' }}>{bulkResult.successCount}</span>
                                            <span style={{ fontSize: '11px', fontWeight: '600', color: '#047857' }}>Successfully Onboarded</span>
                                        </div>
                                        <div style={{ padding: '12px', backgroundColor: bulkResult.failureCount > 0 ? '#FEF2F2' : '#F8FAFC', borderRadius: '8px', border: bulkResult.failureCount > 0 ? '1px solid #FEE2E2' : '1px solid #E2E8F0', textAlign: 'center' }}>
                                            <span style={{ display: 'block', fontSize: '24px', fontWeight: '800', color: bulkResult.failureCount > 0 ? '#DC2626' : '#64748B' }}>{bulkResult.failureCount}</span>
                                            <span style={{ fontSize: '11px', fontWeight: '600', color: bulkResult.failureCount > 0 ? '#B91C1C' : '#475569' }}>Failed Rows</span>
                                        </div>
                                    </div>

                                    {bulkResult.failures && bulkResult.failures.length > 0 && (
                                        <div>
                                            <span style={{ fontSize: '12px', fontWeight: '700', color: '#475569', display: 'block', marginBottom: '6px' }}>Failure Details:</span>
                                            <div style={{ maxHeight: '150px', overflowY: 'auto', border: '1px solid #E2E8F0', borderRadius: '8px', backgroundColor: '#FFF' }}>
                                                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '12px' }}>
                                                    <thead>
                                                        <tr style={{ backgroundColor: '#F1F5F9', textAlign: 'left' }}>
                                                            <th style={{ padding: '8px 12px', fontWeight: '600' }}>Identifier</th>
                                                            <th style={{ padding: '8px 12px', fontWeight: '600' }}>Error Description</th>
                                                        </tr>
                                                    </thead>
                                                    <tbody>
                                                        {bulkResult.failures.map((fail) => (
                                                            <tr key={fail.identifier} style={{ borderBottom: '1px solid #F1F5F9' }}>
                                                                <td style={{ padding: '8px 12px', fontWeight: '600', color: '#475569' }}>{fail.identifier}</td>
                                                                <td style={{ padding: '8px 12px', color: '#DC2626' }}>{fail.error}</td>
                                                            </tr>
                                                        ))}
                                                    </tbody>
                                                </table>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>

                        <div className="modal-footer">
                            <button type="button" className="cancel-btn" onClick={resetBulkState}>
                                {bulkResult ? "Done" : "Cancel"}
                            </button>
                            {!bulkResult && (
                                <button
                                    type="button"
                                    className="submit-btn"
                                    style={{ backgroundColor: '#10B981', boxShadow: '0 4px 6px -1px rgba(16, 185, 129, 0.2)' }}
                                    disabled={bulkData.length === 0 || bulkSubmitting}
                                    onClick={handleBulkSubmit}
                                >
                                    {bulkSubmitting ? "Onboarding..." : `Onboard ${bulkData.length} Users`}
                                </button>
                            )}
                        </div>
                    </div>
                </div>
            )}

            <style>{`
                .users-table-container {
                    background: #fff;
                    border-radius: 12px;
                    border: 1px solid var(--border-color);
                    max-height: 550px;
                    overflow-y: auto;
                    box-shadow: 0 1px 3px rgba(0,0,0,0.05);
                }
                .users-table {
                    width: 100%;
                    border-collapse: collapse;
                    text-align: left;
                }
                .users-table th {
                    position: sticky;
                    top: 0;
                    z-index: 10;
                    padding: 16px 24px;
                    background: #F8FAFC;
                    font-size: 12px;
                    font-weight: 700;
                    color: var(--text-muted);
                    text-transform: uppercase;
                    letter-spacing: 0.05em;
                    border-bottom: 1px solid var(--border-color);
                }
                .users-table td {
                    padding: 16px 24px;
                    border-bottom: 1px solid var(--border-color);
                    vertical-align: middle;
                }
                .user-info-cell {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                }
                .user-avatar-small {
                    width: 32px;
                    height: 32px;
                    background: #EFF6FF;
                    color: #2563EB;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 11px;
                    font-weight: 700;
                }
                .user-meta {
                    display: flex;
                    flex-direction: column;
                }
                .user-name {
                    font-size: 14px;
                    font-weight: 600;
                    color: var(--text-main);
                }
                .user-email {
                    font-size: 12px;
                    color: var(--text-muted);
                }
                .emp-id-badge {
                    font-size: 12px;
                    font-weight: 600;
                    background: #F1F5F9;
                    padding: 4px 8px;
                    border-radius: 6px;
                    color: var(--text-sub);
                }
                .role-chip {
                    display: inline-flex;
                    align-items: center;
                    gap: 6px;
                }
                .status-badge {
                    display: inline-flex;
                    align-items: center;
                    gap: 6px;
                    font-size: 12px;
                    font-weight: 600;
                }
                .status-badge.active { color: #059669; }
                .status-badge.inactive { color: #DC2626; }
                
                .action-btns {
                    display: flex;
                    gap: 8px;
                }
                .icon-btn {
                    padding: 6px;
                    border: 1px solid var(--border-color);
                    background: #fff;
                    border-radius: 6px;
                    cursor: pointer;
                    color: var(--text-muted);
                    transition: all 0.2s;
                }
                .icon-btn:hover.edit { color: #2563EB; border-color: #2563EB; }
                .icon-btn:hover.delete { color: #DC2626; border-color: #DC2626; }

                /* Modal Styles */
                .modal-overlay {
                    position: fixed;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    background: rgba(15, 23, 42, 0.4);
                    backdrop-filter: blur(4px);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    z-index: 1000;
                    animation: fadeIn 0.2s ease-out;
                }
                .modal-content {
                    background: #fff;
                    width: 700px;
                    max-height: 85vh;
                    border-radius: 16px;
                    box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.15);
                    display: flex;
                    flex-direction: column;
                    overflow: hidden;
                    animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1);
                }
                .onboard-form {
                    display: flex;
                    flex-direction: column;
                    flex: 1;
                    overflow: hidden;
                }
                .form-sections-container {
                    flex: 1;
                    overflow-y: auto;
                    padding: 24px 32px;
                    display: flex;
                    flex-direction: column;
                    gap: 24px;
                }
                .form-sections-container::-webkit-scrollbar {
                    width: 6px;
                }
                .form-sections-container::-webkit-scrollbar-track {
                    background: transparent;
                }
                .form-sections-container::-webkit-scrollbar-thumb {
                    background: #cbd5e1;
                    border-radius: 3px;
                }
                .form-sections-container::-webkit-scrollbar-thumb:hover {
                    background: #94a3b8;
                }
                .form-section h3 {
                    font-size: 13px;
                    font-weight: 700;
                    color: #2563EB;
                    margin-top: 0;
                    margin-bottom: 12px;
                    padding-bottom: 6px;
                    border-bottom: 1px dashed var(--border-color);
                    text-transform: uppercase;
                    letter-spacing: 0.05em;
                }
                .modal-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    padding: 24px 32px 16px 32px;
                    border-bottom: 1px solid var(--border-color);
                }
                .modal-header h2 { 
                    font-size: 20px; 
                    font-weight: 800; 
                    color: #0f172a;
                    margin: 0;
                }
                .form-grid {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 16px;
                }
                .form-group {
                    display: flex;
                    flex-direction: column;
                    gap: 6px;
                }
                .form-group label {
                    font-size: 12px;
                    font-weight: 600;
                    color: var(--text-sub);
                }
                .form-group input, .form-group select {
                    padding: 10px 14px;
                    border: 1px solid var(--border-color);
                    border-radius: 8px;
                    outline: none;
                    font-size: 14px;
                    background-color: #F8FAFC;
                    transition: all 0.2s;
                }
                .form-group input:focus, .form-group select:focus { 
                    border-color: #2563EB; 
                    background-color: #FFF; 
                    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
                }
                .modal-footer {
                    display: flex;
                    justify-content: flex-end;
                    align-items: center;
                    gap: 12px;
                    padding: 16px 32px 24px 32px;
                    border-top: 1px solid var(--border-color);
                    background: #FFF;
                }
                .cancel-btn { 
                    padding: 10px 20px; 
                    border: 1px solid #e2e8f0; 
                    background: #fff; 
                    color: #475569;
                    border-radius: 8px; 
                    font-weight: 600; 
                    font-size: 14px;
                    cursor: pointer; 
                    transition: all 0.2s;
                }
                .cancel-btn:hover {
                    background: #f8fafc;
                    color: #0f172a;
                    border-color: #cbd5e1;
                }
                .submit-btn { 
                    padding: 10px 24px; 
                    border: none; 
                    background: #2563EB; 
                    color: #fff; 
                    border-radius: 8px; 
                    font-weight: 600; 
                    font-size: 14px;
                    cursor: pointer; 
                    transition: all 0.2s;
                    box-shadow: 0 4px 6px -1px rgba(37, 99, 235, 0.2);
                }
                .submit-btn:hover {
                    background: #1d4ed8;
                    box-shadow: 0 10px 15px -3px rgba(37, 99, 235, 0.3);
                }
                .submit-btn:disabled {
                    background: #94a3b8;
                    cursor: not-allowed;
                    box-shadow: none;
                }
                .close-btn { 
                    background: none; 
                    border: none; 
                    color: var(--text-muted); 
                    cursor: pointer; 
                    padding: 4px;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    transition: all 0.2s;
                }
                .close-btn:hover {
                    background: #f1f5f9;
                    color: #0f172a;
                }
                @keyframes fadeIn {
                    from { opacity: 0; }
                    to { opacity: 1; }
                }
                @keyframes slideUp {
                    from { transform: translateY(20px); opacity: 0; }
                    to { transform: translateY(0); opacity: 1; }
                }
            `}</style>
        </div>
    );
};

Onboarding.propTypes = {
    tenantId: PropTypes.string.isRequired
};

export default Onboarding;
