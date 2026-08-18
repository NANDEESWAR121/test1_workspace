import React, { useState, useEffect, useMemo } from 'react';
import PropTypes from 'prop-types';
import { Settings, Shield, Lock, Users, Save, Search, UserPlus, Globe } from 'lucide-react';
import api from '../api';

const getDisplayEmployeeId = (id) => {
    if (!id) return "";
    if (typeof id === 'string') {
        if (id.includes('_')) return id.split('_').pop();
        if (id.includes('-')) return id.split('-').pop();
    }
    return id;
};

const ProductIconRenderer = ({ product }) => {
    if (product?.productIcon) {
        return <img src={product.productIcon} alt={product.productName} style={{ width: '20px', height: '20px', objectFit: 'contain' }} />;
    }
    return <Globe size={20} />;
};

/* ── Pure helpers (outside component to avoid re-creation on each render) ── */
const subAdminRequiredModules = ['Management_Onboarding', 'Management_Settings'];

const applySubAdminModules = (modules, enable) => {
    if (enable) {
        const additions = subAdminRequiredModules.filter(m => !modules.includes(m));
        return [...modules, ...additions];
    }
    return modules.filter(m => !subAdminRequiredModules.includes(m));
};

const filterUsersHelper = (users, query, selectedUser) => {
    const trimmedQuery = query.trim().toLowerCase();
    if (trimmedQuery === '') return selectedUser ? [selectedUser] : [];
    return users.filter(u =>
        (`${u.firstName || ""} ${u.lastName || ""}`).toLowerCase().includes(trimmedQuery) ||
        getDisplayEmployeeId(u.employeeId).toLowerCase().includes(trimmedQuery) ||
        (u.email || "").toLowerCase().includes(trimmedQuery)
    );
};

const useSettingsModuleState = (tenantId) => {
    const [users, setUsers] = useState([]);
    const [searchQuery, setSearchQuery] = useState('');
    const [debouncedSearchQuery, setDebouncedSearchQuery] = useState('');
    const [selectedUser, setSelectedUser] = useState(null);
    const [userPermissions, setUserPermissions] = useState({
        products: [],
        modules: []
    });

    useEffect(() => {
        if (tenantId) {
            fetchUsers();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [tenantId]);

    useEffect(() => {
        const timer = setTimeout(() => setDebouncedSearchQuery(searchQuery), 300);
        return () => clearTimeout(timer);
    }, [searchQuery]);

    const fetchUsers = async () => {
        try {
            const response = await api.get(`/tenant-users/tenant/${tenantId}`);
            setUsers(response.data);
        } catch (error) {
            console.error("Error fetching users:", error);
        }
    };

    const handleUserSelect = (user) => {
        setSelectedUser(user);
        setUserPermissions({
            products: user.assignedProducts ? user.assignedProducts.split(',').map(p => p.trim()).filter(Boolean) : [],
            modules: user.assignedModules ? user.assignedModules.split(',').map(m => m.trim()).filter(Boolean) : []
        });
    };

    const toggleProduct = (productCode) => {
        if (!productCode) return;
        const code = productCode.trim();
        const newList = userPermissions.products.includes(code)
            ? userPermissions.products.filter(p => p !== code)
            : [...userPermissions.products, code];
        setUserPermissions({ ...userPermissions, products: newList });
    };

    const toggleSubAdmin = () => {
        if (!selectedUser) return;
        const nextVal = !selectedUser.isSubAdmin;
        const newModules = applySubAdminModules(userPermissions.modules, nextVal);
        setSelectedUser({ ...selectedUser, isSubAdmin: nextVal });
        setUserPermissions({ ...userPermissions, modules: newModules });
    };

    const toggleModule = (moduleName) => {
        const newModules = userPermissions.modules.includes(moduleName)
            ? userPermissions.modules.filter(m => m !== moduleName)
            : [...userPermissions.modules, moduleName];
        setUserPermissions({ ...userPermissions, modules: newModules });

        const shouldBeSubAdmin = subAdminRequiredModules.every(m => newModules.includes(m));
        if (selectedUser.isSubAdmin !== shouldBeSubAdmin) {
            setSelectedUser({ ...selectedUser, isSubAdmin: shouldBeSubAdmin });
        }
    };

    const savePermissions = async () => {
        if (!selectedUser) return;
        try {
            const response = await api.put(`/tenant-users/${selectedUser.id}`, {
                ...selectedUser,
                assignedProducts: userPermissions.products.join(','),
                assignedModules: userPermissions.modules.join(',')
            });
            alert("Permissions saved successfully!");
            if (response.data) {
                setSelectedUser(response.data);
            }
            fetchUsers();
        } catch (error) {
            console.error("Error saving permissions:", error);
        }
    };

    const filteredUsers = useMemo(() => {
        return filterUsersHelper(users, debouncedSearchQuery, selectedUser);
    }, [users, debouncedSearchQuery, selectedUser]);

    return {
        searchQuery,
        setSearchQuery,
        selectedUser,
        userPermissions,
        toggleProduct,
        toggleSubAdmin,
        toggleModule,
        savePermissions,
        filteredUsers,
        handleUserSelect
    };
};

const UsersListSidebar = ({
    filteredUsers,
    selectedUser,
    searchQuery,
    setSearchQuery,
    handleUserSelect
}) => {
    return (
        <div className="users-list-sidebar">
            <h3>Users</h3>
            <div style={{ padding: '0 20px 12px 20px', borderBottom: '1px solid var(--border-color)' }}>
                <div className="search-input-wrapper header-search" style={{ width: '100%' }}>
                    <Search size={16} className="search-icon-inside" />
                    <input
                        type="text"
                        placeholder="Search by name, ID or email..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        style={{ width: '100%', fontSize: '13px' }}
                    />
                </div>
            </div>
            <div className="rbac-users-list">
                {filteredUsers.map(user => (
                    <button
                        key={user.id}
                        type="button"
                        className={`rbac-user-item ${selectedUser?.id === user.id ? 'active' : ''}`}
                        onClick={() => handleUserSelect(user)}
                    >
                        <div className="user-avatar-tiny">
                            {user.firstName ? user.firstName[0] : ''}
                        </div>
                        <div className="user-text">
                            <span className="name">{`${user.firstName || ""} ${user.lastName || ""}`.trim()}</span>
                            <span className="role">{getDisplayEmployeeId(user.employeeId)} {user.role ? `— ${user.role}` : ''}</span>
                        </div>
                    </button>
                ))}
            </div>
        </div>
    );
};

UsersListSidebar.propTypes = {
    filteredUsers: PropTypes.array.isRequired,
    selectedUser: PropTypes.object,
    searchQuery: PropTypes.string.isRequired,
    setSearchQuery: PropTypes.func.isRequired,
    handleUserSelect: PropTypes.func.isRequired
};

const PermissionsEditor = ({
    selectedUser,
    userPermissions,
    allowedProducts,
    toggleSubAdmin,
    toggleModule,
    toggleProduct,
    savePermissions
}) => {
    if (!selectedUser) {
        return (
            <div className="no-user-selected">
                <Users size={64} color="#E2E8F0" />
                <h3>Select a user to manage permissions</h3>
            </div>
        );
    }

    return (
        <>
            <div className="editor-header">
                <div>
                    <h2>Access Mapping for {`${selectedUser.firstName || ""} ${selectedUser.lastName || ""}`.trim()}</h2>
                    <p>Managing access for Employee ID: {getDisplayEmployeeId(selectedUser.employeeId)}</p>
                </div>
                <button className="save-btn" onClick={savePermissions}>
                    <Save size={16} />
                    <span>Save Access</span>
                </button>
            </div>

            <div className="product-access-section" style={{ marginTop: '32px' }}>
                <h3>Workspace Management</h3>
                <div className="access-grid">
                    <button
                        type="button"
                        className={`access-item ${selectedUser.isSubAdmin ? 'granted' : ''}`}
                        onClick={toggleSubAdmin}
                    >
                        <div className="access-icon">
                            {selectedUser.isSubAdmin ? <Shield size={20} /> : <Lock size={20} />}
                        </div>
                        <div className="access-info">
                            <span className="p-name">Sub Admin Status</span>
                            <span className="p-status">{selectedUser.isSubAdmin ? 'Full Authority' : 'No Authority'}</span>
                        </div>
                    </button>

                    <button
                        type="button"
                        className={`access-item ${userPermissions.modules.includes('Management_Onboarding') ? 'granted' : ''}`}
                        onClick={() => toggleModule('Management_Onboarding')}
                    >
                        <div className="access-icon">
                            {userPermissions.modules.includes('Management_Onboarding') ? <UserPlus size={20} /> : <Lock size={20} />}
                        </div>
                        <div className="access-info">
                            <span className="p-name">User Onboarding</span>
                            <span className="p-status">{userPermissions.modules.includes('Management_Onboarding') ? 'Access Granted' : 'No Access'}</span>
                        </div>
                    </button>

                    <button
                        type="button"
                        className={`access-item ${userPermissions.modules.includes('Management_Settings') ? 'granted' : ''}`}
                        onClick={() => toggleModule('Management_Settings')}
                    >
                        <div className="access-icon">
                            {userPermissions.modules.includes('Management_Settings') ? <Settings size={20} /> : <Lock size={20} />}
                        </div>
                        <div className="access-info">
                            <span className="p-name">Product Settings</span>
                            <span className="p-status">{userPermissions.modules.includes('Management_Settings') ? 'Access Granted' : 'No Access'}</span>
                        </div>
                    </button>
                </div>
            </div>

            <div className="product-access-section" style={{ marginTop: '24px' }}>
                <h3>Product Access</h3>
                <div className="access-grid">
                    {allowedProducts.map(prod => {
                        const code = prod.productCode || "";
                        const isGranted = userPermissions.products.includes(code);
                        return (
                            <button
                                key={prod.productId || code}
                                type="button"
                                className={`access-item ${isGranted ? 'granted' : ''}`}
                                onClick={() => toggleProduct(code)}
                            >
                                <div className="access-icon">
                                    <ProductIconRenderer product={prod} />
                                </div>
                                <div className="access-info">
                                    <span className="p-name">{prod.productName}</span>
                                    <span className="p-status">{code} — {isGranted ? 'Access Granted' : 'No Access'}</span>
                                </div>
                            </button>
                        );
                    })}
                </div>
            </div>
        </>
    );
};

PermissionsEditor.propTypes = {
    selectedUser: PropTypes.object,
    userPermissions: PropTypes.shape({
        products: PropTypes.array.isRequired,
        modules: PropTypes.array.isRequired
    }).isRequired,
    allowedProducts: PropTypes.array.isRequired,
    toggleSubAdmin: PropTypes.func.isRequired,
    toggleModule: PropTypes.func.isRequired,
    toggleProduct: PropTypes.func.isRequired,
    savePermissions: PropTypes.func.isRequired
};

const SettingsModule = ({ tenantId, allowedProducts }) => {
    const [activeTab] = useState('rbac');
    const {
        searchQuery,
        setSearchQuery,
        selectedUser,
        userPermissions,
        toggleProduct,
        toggleSubAdmin,
        toggleModule,
        savePermissions,
        filteredUsers,
        handleUserSelect
    } = useSettingsModuleState(tenantId);

    return (
        <div className="settings-container">
            <div className="launchpad-view-header">
                <h1>Settings</h1>
                <p>Configure user access permissions and role-based control.</p>
            </div>

            <div className="settings-content">
                {activeTab === 'rbac' && (
                    <div className="rbac-settings">
                        <div className="rbac-layout">
                            <UsersListSidebar
                                filteredUsers={filteredUsers}
                                selectedUser={selectedUser}
                                searchQuery={searchQuery}
                                setSearchQuery={setSearchQuery}
                                handleUserSelect={handleUserSelect}
                            />
                            <PermissionsEditor
                                selectedUser={selectedUser}
                                userPermissions={userPermissions}
                                allowedProducts={allowedProducts}
                                toggleSubAdmin={toggleSubAdmin}
                                toggleModule={toggleModule}
                                toggleProduct={toggleProduct}
                                savePermissions={savePermissions}
                            />
                        </div>
                    </div>
                )}
            </div>

            <style>{`
                .settings-tabs {
                    display: flex;
                    gap: 12px;
                    margin-bottom: 24px;
                    border-bottom: 1px solid var(--border-color);
                    padding-bottom: 12px;
                }
                .tab-btn {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    padding: 10px 20px;
                    background: transparent;
                    border: none;
                    border-radius: 8px;
                    color: var(--text-muted);
                    font-weight: 600;
                    cursor: pointer;
                    transition: all 0.2s;
                }
                .tab-btn.active {
                    background: #2563EB;
                    color: #fff;
                }
                
                .settings-card {
                    background: #fff;
                    border: 1px solid var(--border-color);
                    border-radius: 12px;
                    padding: 24px;
                }
                .settings-card h3 { margin-bottom: 8px; }
                .settings-card p { color: var(--text-muted); margin-bottom: 24px; font-size: 14px; }

                .product-visibility-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
                    gap: 20px;
                }
                .visibility-card {
                    padding: 20px;
                    border: 1px solid var(--border-color);
                    border-radius: 12px;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                }
                .visibility-card-header {
                    display: flex;
                    gap: 12px;
                    align-items: center;
                }
                .prod-icon-wrap {
                    width: 40px;
                    height: 40px;
                    background: #F1F5F9;
                    border-radius: 8px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: #3B82F6;
                }
                .visibility-card h4 { font-size: 15px; margin: 0; }
                .status-label { font-size: 11px; font-weight: 700; color: #059669; }

                /* Switch toggle */
                .switch {
                    position: relative;
                    display: inline-block;
                    width: 40px;
                    height: 20px;
                }
                .switch input { opacity: 0; width: 0; height: 0; }
                .slider {
                    position: absolute;
                    cursor: pointer;
                    top: 0; left: 0; right: 0; bottom: 0;
                    background-color: #ccc;
                    transition: .4s;
                }
                .slider:before {
                    position: absolute;
                    content: "";
                    height: 14px; width: 14px;
                    left: 3px; bottom: 3px;
                    background-color: white;
                    transition: .4s;
                }
                input:checked + .slider { background-color: #2563EB; }
                input:checked + .slider:before { transform: translateX(20px); }
                .slider.round { border-radius: 34px; }
                .slider.round:before { border-radius: 50%; }

                .rbac-layout {
                    display: grid;
                    grid-template-columns: 280px 1fr;
                    gap: 24px;
                    background: #fff;
                    border: 1px solid var(--border-color);
                    border-radius: 12px;
                    min-height: 500px;
                }
                .users-list-sidebar {
                    border-right: 1px solid var(--border-color);
                    padding: 20px 0;
                }
                .users-list-sidebar h3 { 
                    padding: 0 20px 16px; 
                    font-size: 14px; 
                    font-weight: 700;
                    color: #1E293B;
                    text-align: left;
                    border-bottom: 1px solid var(--border-color); 
                }
                .rbac-users-list { max-height: 450px; overflow-y: auto; }
                
                .rbac-user-item {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    padding: 12px 20px;
                    cursor: pointer;
                    transition: all 0.2s ease-in-out;
                    border: none;
                    border-bottom: 1px solid #F8FAFC;
                    background: transparent;
                    width: 100%;
                    text-align: left;
                    font-family: inherit;
                    color: inherit;
                }
                .rbac-user-item:hover { 
                    background: #F8FAFC; 
                }
                .rbac-user-item.active { 
                    background: #EFF6FF; 
                    border-left: 4px solid #2563EB; 
                    padding-left: 16px;
                }
                
                .user-avatar-tiny {
                    width: 36px; height: 36px;
                    background: linear-gradient(135deg, #3B82F6 0%, #1D4ED8 100%);
                    color: #FFFFFF;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 13px;
                    font-weight: 700;
                    flex-shrink: 0;
                    box-shadow: 0 2px 4px rgba(59, 130, 246, 0.15);
                }
                
                .user-text {
                    display: flex;
                    flex-direction: column;
                    gap: 2px;
                    overflow: hidden;
                    text-align: left;
                }
                .name { 
                    font-size: 14px; 
                    font-weight: 600; 
                    color: #1E293B;
                    white-space: nowrap; 
                    overflow: hidden; 
                    text-overflow: ellipsis; 
                }
                .role { 
                    font-size: 11px; 
                    color: #64748B; 
                    white-space: nowrap; 
                    overflow: hidden; 
                    text-overflow: ellipsis; 
                    max-width: 190px;
                }

                .permissions-editor { padding: 32px; position: relative; }
                .editor-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 24px;
                    border-bottom: 1px solid #F1F5F9;
                    padding-bottom: 20px;
                    text-align: left;
                }
                .editor-header h2 { 
                    font-size: 22px; 
                    font-weight: 700;
                    color: #0F172A;
                    margin: 0 0 6px 0; 
                }
                .editor-header p {
                    font-size: 14px;
                    color: #64748B;
                    margin: 0;
                }
                .save-btn {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    padding: 10px 20px;
                    background: #10B981;
                    color: #fff;
                    border: none;
                    border-radius: 8px;
                    font-weight: 600;
                    font-size: 14px;
                    cursor: pointer;
                    transition: all 0.2s ease;
                    box-shadow: 0 4px 6px rgba(16, 185, 129, 0.15);
                }
                .save-btn:hover {
                    background: #059669;
                    transform: translateY(-1px);
                    box-shadow: 0 6px 12px rgba(16, 185, 129, 0.2);
                }
                .save-btn:active {
                    transform: translateY(0);
                }
                
                .product-access-section {
                    text-align: left;
                }
                .product-access-section h3 {
                    font-size: 14px;
                    font-weight: 700;
                    color: #475569;
                    margin-bottom: 16px;
                    text-transform: uppercase;
                    letter-spacing: 0.05em;
                }
                
                .access-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
                    gap: 16px;
                    margin-top: 16px;
                    margin-bottom: 32px;
                }
                .access-item {
                    padding: 18px;
                    background: #FFFFFF;
                    border: 1px solid #E2E8F0;
                    border-radius: 12px;
                    display: flex;
                    align-items: center;
                    gap: 16px;
                    cursor: pointer;
                    transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
                    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
                    text-align: left;
                    font-family: inherit;
                    width: 100%;
                    color: inherit;
                }
                .access-item:hover { 
                    border-color: #CBD5E1; 
                    transform: translateY(-2px);
                    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
                }
                .access-item.granted { 
                    border-color: #3B82F6; 
                    background: #F8FAFC; 
                    box-shadow: 0 4px 12px rgba(59, 130, 246, 0.06);
                }
                .access-icon { 
                    width: 44px;
                    height: 44px;
                    background: #F1F5F9;
                    border-radius: 10px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: #64748B;
                    flex-shrink: 0;
                    transition: all 0.2s ease;
                }
                .access-item.granted .access-icon { 
                    background: #EFF6FF; 
                    color: #2563EB; 
                }
                .p-name { 
                    font-size: 15px; 
                    font-weight: 600; 
                    color: #1E293B;
                    margin-bottom: 2px;
                    display: block; 
                }
                .p-status { 
                    font-size: 12px; 
                    color: #64748B; 
                    font-weight: 500;
                }
                .access-item.granted .p-status {
                    color: #2563EB;
                }

                .no-user-selected {
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    height: 100%;
                    color: var(--text-muted);
                    min-height: 400px;
                }

                @media (max-width: 768px) {
                    .rbac-layout {
                        display: flex;
                        flex-direction: column;
                    }
                    .users-list-sidebar {
                        border-right: none;
                        border-bottom: 1px solid var(--border-color);
                    }
                    .permissions-editor {
                        padding: 20px 16px;
                    }
                    .editor-header {
                        flex-direction: column;
                        align-items: flex-start;
                        gap: 16px;
                    }
                    .editor-header .save-btn {
                        width: 100%;
                        justify-content: center;
                    }
                    .access-grid {
                        grid-template-columns: 1fr;
                    }
                }
            `}</style>
        </div>
    );
};

ProductIconRenderer.propTypes = {
    product: PropTypes.shape({
        productIcon: PropTypes.string,
        productName: PropTypes.string
    })
};

SettingsModule.propTypes = {
    tenantId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    allowedProducts: PropTypes.arrayOf(
        PropTypes.shape({
            productId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
            productCode: PropTypes.string,
            productName: PropTypes.string,
            productIcon: PropTypes.string
        })
    ).isRequired
};

export default SettingsModule;
