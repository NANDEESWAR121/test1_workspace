import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api";
import {
  LayoutGrid,
  Users,
  Mail,
  MessageSquare,
  Briefcase,
  Database,
  ArrowLeft,
  Globe,
  Layers,
  ArrowUpRight,
  Settings,
  UserPlus
} from "lucide-react";
import Onboarding from "./Onboarding";
import SettingsModule from "./Settings";
import "./Dashboard.css";
import scalozLogo from "../assets/Scaloz.png";


function Dashboard() {
  const navigate = useNavigate();
  const profileMenuRef = useRef(null);
  const [searchQuery] = useState("");
  const [darkMode] = useState(false);
  const [selectedApp, setSelectedApp] = useState("launchpad");
  const [showProfileDropdown, setShowProfileDropdown] = useState(false);

  const [allowedProducts, setAllowedProducts] = useState([]);
  const [allProducts, setAllProducts] = useState([]);
  const [userName, setUserName] = useState("User");
  const [tenantName, setTenantName] = useState("Enterprise");
  const [tenantId, setTenantId] = useState("");

  // Apply dark mode class to body element
  useEffect(() => {
    if (darkMode) {
      document.body.classList.add("dark-theme");
    } else {
      document.body.classList.remove("dark-theme");
    }
  }, [darkMode]);

  // Load allowed products and tenant details from sessionStorage
  useEffect(() => {
    const token = sessionStorage.getItem("token");
    if (!token) {
      navigate("/");
      return;
    }
    try {
      const stored = sessionStorage.getItem("products");
      if (stored) {
        setAllowedProducts(JSON.parse(stored));
      }
      setUserName(sessionStorage.getItem("userName") || "Admin");
      setTenantName(sessionStorage.getItem("tenantName") || "Enterprise");
      setTenantId(sessionStorage.getItem("tenantId") || "");
    } catch (e) {
      console.error("Failed to load tenant product mappings from session:", e);
    }
  }, [navigate]);

  // Fetch all products to calculate unassigned products
  useEffect(() => {
    const fetchAllProducts = async () => {
      try {
        const response = await api.get('/products');
        if (response.data) {
          setAllProducts(response.data);
        }
      } catch (error) {
        console.error("Failed to fetch all products:", error);
      }
    };
    fetchAllProducts();
  }, []);

  // Close profile dropdown when clicking outside
  useEffect(() => {
    const handleOutsideClick = (event) => {
      if (profileMenuRef.current && !profileMenuRef.current.contains(event.target)) {
        setShowProfileDropdown(false);
      }
    };
    document.addEventListener("click", handleOutsideClick);
    return () => {
      document.removeEventListener("click", handleOutsideClick);
    };
  }, []);

  const handleLogout = () => {
    sessionStorage.clear();
    navigate("/");
  };

  const getImageSrc = (iconData) => {
    if (!iconData || typeof iconData !== 'string') return null;
    if (iconData.startsWith("data:") || iconData.startsWith("http://") || iconData.startsWith("https://")) {
      return iconData;
    }
    if (iconData.startsWith("/uploads") || iconData.startsWith("uploads/")) {
      const cleanPath = iconData.startsWith("/") ? iconData : `/${iconData}`;
      const serverBase = (api.defaults.baseURL || "").replace(/\/api$/, "");
      return `${serverBase}${cleanPath}`;
    }
    const lower = iconData.toLowerCase().trim();
    const isLegacy = lower.endsWith(".png") ||
      lower.endsWith(".jpg") ||
      lower.endsWith(".jpeg") ||
      lower.endsWith(".gif") ||
      lower.endsWith(".svg") ||
      lower.includes("/") ||
      lower.includes("\\");
    if (isLegacy) {
      const cleanPath = iconData.startsWith("uploads/") ? `/${iconData}` : `/uploads/logos/${iconData}`;
      const serverBase = (api.defaults.baseURL || "").replace(/\/api$/, "");
      return `${serverBase}${cleanPath}`;
    }
    try {
      if (iconData.startsWith('aVZ')) {
        const decoded = atob(iconData);
        return `data:image/png;base64,${decoded}`;
      }
    } catch (e) {
      console.warn("Failed to decode iconData as base64:", e);
    }
    return `data:image/png;base64,${iconData}`;
  };

  const userRole = sessionStorage.getItem("userRole") || "User";
  const isSubAdmin = sessionStorage.getItem("isSubAdmin") === "true";
  const isAdmin = userRole === "Admin" || isSubAdmin;

  // Check for specific management permissions in any product's modules
  const hasOnboardingAccess = isAdmin || allowedProducts.some(p => p.modules?.includes('Management_Onboarding'));
  const hasSettingsAccess = isAdmin || allowedProducts.some(p => p.modules?.includes('Management_Settings'));

  // Build workspace sidebar menu dynamically based on allowed products
  const sidebarWorkspaceMenu = [
    { id: "launchpad", label: "My Workspace", icon: LayoutGrid },
    ...(hasOnboardingAccess ? [{ id: "onboarding", label: "Onboarding", icon: UserPlus }] : []),
    ...(hasSettingsAccess ? [{ id: "settings", label: "Settings", icon: Settings }] : [])
  ];

  // Helper to generate branded subdomain URL for products
  const getBrandedLaunchUrl = (prod) => {
    const sanitizedTenantName = (tenantName || "workspace").toLowerCase().replace(/[^a-z0-9]/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
    let baseUrl = prod.url.trim();
    if (!baseUrl.includes('://')) {
      baseUrl = `https://${baseUrl}`;
    }
    try {
      const urlObj = new URL(baseUrl);
      // Result: https://tenantname.producthost.com
      let launchUrl = `${urlObj.protocol}//${sanitizedTenantName}.${urlObj.host}${urlObj.pathname}${urlObj.search}`;

      const token = sessionStorage.getItem("token");
      if (token) {
        const separator = launchUrl.includes('?') ? '&' : '?';
        launchUrl = `${launchUrl}${separator}scaloz_token=${encodeURIComponent(token)}`;
      }
      return launchUrl;
    } catch (e) {
      console.warn("Failed to construct branded launch URL:", e);
      const token = sessionStorage.getItem("token");
      if (token) {
        const separator = baseUrl.includes('?') ? '&' : '?';
        return `${baseUrl}${separator}scaloz_token=${encodeURIComponent(token)}`;
      }
      return baseUrl;
    }
  };
  const getInitials = (name) => {
    if (!name) return "K";
    return name.charAt(0).toUpperCase();
  };

  // Find currently selected product if it's not the launchpad
  const activeProduct = allowedProducts.find((p) => p.productId.toString() === selectedApp);

  // Filter products for the search bar in Launchpad view
  const filteredProducts = allowedProducts.filter((p) =>
    p.productName.toLowerCase().includes(searchQuery.toLowerCase()) ||
    p.modules?.some(m => m.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  // Compute unassigned products by filtering out allowedProducts from allProducts
  const unassignedProducts = allProducts.filter(ap =>
    !allowedProducts.some(p =>
      (p.productId && ap.id && p.productId.toString() === ap.id.toString()) ||
      (p.productCode && ap.code && p.productCode === ap.code)
    )
  ).filter((p) =>
    p.name?.toLowerCase().includes(searchQuery.toLowerCase()) ||
    p.code?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const firstName = userName ? userName.split(' ')[0] : "Koushik";

  return (
    <div className={`workspace-layout ${darkMode ? "dark" : ""}`}>
      {/* ============================================
         LEFT SIDEBAR
         ============================================ */}
      <aside className="workspace-sidebar">
        <div className="sidebar-brand">
          <img src={scalozLogo} alt="Scaloz Logo" className="sidebar-logo" />
        </div>

        <div className="sidebar-scrollable">
          <div className="sidebar-section">
            <h3 className="sidebar-section-title">MY APPLICATIONS</h3>
            <ul className="sidebar-nav">
              {sidebarWorkspaceMenu.map((item) => {
                const IconComponent = item.icon;
                const isActive = selectedApp === item.id;

                // Color bubbles for sidebar apps
                let bubbleColor = "#3B82F6";
                if (item.label.toUpperCase().includes("MAIL")) {
                  bubbleColor = "#8B5CF6";
                } else if (item.label.toUpperCase().includes("PROJECT")) {
                  bubbleColor = "#F59E0B";
                } else if (item.label.toUpperCase().includes("CHAT")) {
                  bubbleColor = "#10B981";
                } else if (item.label.toUpperCase().includes("CRM")) {
                  bubbleColor = "#EC4899";
                }

                return (
                  <li key={item.id}>
                    <button
                      className={`sidebar-nav-btn ${isActive ? "active" : ""}`}
                      onClick={() => {
                        if (item.id === "launchpad" || item.id === "onboarding" || item.id === "settings") {
                          setSelectedApp(item.id);
                        } else {
                          const prod = allowedProducts.find((p) => p.productId.toString() === item.id);
                          if (prod) {
                            window.location.href = getBrandedLaunchUrl(prod);
                          }
                        }
                      }}
                    >
                      {["launchpad", "onboarding", "settings"].includes(item.id) ? (
                        <IconComponent size={18} className="sidebar-icon" />
                      ) : (
                        <div className="sidebar-app-bubble" style={{ backgroundColor: bubbleColor }}>
                          <IconComponent size={12} color="#FFFFFF" />
                        </div>
                      )}
                      <span>{item.label}</span>
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>
        </div>

      </aside>

      {/* ============================================
         MAIN WORKSPACE AREA
         ============================================ */}
      <main className="workspace-main">
        {/* Top Header Bar */}
        <header className="workspace-topbar">
          <div className="topbar-welcome">
            <h2>Welcome back, <span className="welcome-name">{firstName}</span>! 👋</h2>
            <p>Everything you need, all in one place.</p>
          </div>
          <div className="topbar-right">
            <div className="topbar-user-menu-container" ref={profileMenuRef} style={{ position: "relative" }}>
              <button
                type="button"
                className="topbar-user-avatar"
                onClick={() => {
                  setShowProfileDropdown(!showProfileDropdown);
                }}
                aria-haspopup="true"
                aria-expanded={showProfileDropdown}
                aria-label="Toggle user profile dropdown"
                style={{ border: "none", padding: 0, outline: "none" }}
              >
                {getInitials(userName || "Koushik Olag")}
              </button>

              {showProfileDropdown && (
                <div className="profile-dropdown-menu">
                  <div className="profile-dropdown-info">
                    <div className="profile-avatar-large">
                      {getInitials(userName || "Koushik Olag")}
                    </div>
                    <div className="profile-meta-details">
                      <h4 className="profile-name-large">{userName || "koushik"}</h4>
                      <span className="profile-role-badge">{userRole}</span>
                      <span className="profile-tenant-name">{tenantName || "Isquaretek"}</span>
                    </div>
                  </div>
                  <div className="profile-dropdown-divider"></div>
                  <button className="profile-dropdown-logout-btn" onClick={handleLogout}>
                    Log Out
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>

        {/* Workspace Body */}
        <div className="workspace-body">
          {selectedApp === "launchpad" && (
            <div className="launchpad-view">
              <div className="launchpad-view-header">
                <h1>My Applications</h1>
                <p>Click on any application to access your tools and modules.</p>
              </div>

              {/* Grid of application cards (mockup horizontal format) */}
              <div className="launchpad-grid-horizontal">
                {filteredProducts.length > 0 ? (
                  filteredProducts.map((prod) => {
                    let IconComponent = Globe;
                    let colorClass = "app-color-blue";
                    const nameUpper = prod.productName.toUpperCase();
                    if (nameUpper.includes("HRMS") || nameUpper.includes("PEOPLE")) {
                      IconComponent = Users;
                    } else if (nameUpper.includes("MAIL")) {
                      IconComponent = Mail;
                      colorClass = "app-color-purple-gradient";
                    } else if (nameUpper.includes("CHAT")) {
                      IconComponent = MessageSquare;
                      colorClass = "app-color-green-gradient";
                    } else if (nameUpper.includes("CRM")) {
                      IconComponent = Briefcase;
                      colorClass = "app-color-pink-gradient";
                    } else if (nameUpper.includes("PROJECT")) {
                      IconComponent = Layers;
                      colorClass = "app-color-orange-gradient";
                    }


                    return (
                      <a
                        key={prod.productId}
                        className="launchpad-horizontal-card"
                        href={getBrandedLaunchUrl(prod)}
                        target="_self"
                        rel="noopener noreferrer"
                        style={{ cursor: "pointer", textDecoration: "none" }}
                      >
                        <div className="horizontal-card-main" style={{ flexDirection: "column", gap: "16px" }}>
                          <div className="horizontal-card-header" style={{ display: "flex", alignItems: "center", gap: "12px", minHeight: prod.icon ? "38px" : "40px" }}>
                            {prod.icon ? (
                              /* Fixed bounding box — consistent size for all logos */
                              <div style={{
                                width: "100%",
                                height: "38px",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "flex-start",
                                flexShrink: 0
                              }}>
                                <img
                                  src={getImageSrc(prod.icon)}
                                  alt={prod.productName}
                                  style={{
                                    height: "30px",
                                    width: "auto",
                                    objectFit: "contain",
                                    display: "block"
                                  }}
                                />
                              </div>
                            ) : (
                              <>
                                <div className={`horizontal-card-icon-wrap ${colorClass}`} style={{ width: "56px", height: "56px" }}>
                                  <IconComponent size={24} />
                                </div>
                                <div className="horizontal-card-title-row" style={{ margin: 0, display: "flex", flexDirection: "column", alignItems: "flex-start", gap: "2px" }}>
                                  {(() => {
                                    const trimmedName = (prod.productName || "").trim();
                                    const firstSpaceIdx = trimmedName.indexOf(" ");
                                    if (firstSpaceIdx > 0) {
                                      const brandWord = trimmedName.substring(0, firstSpaceIdx);
                                      const restOfName = trimmedName.substring(firstSpaceIdx + 1).trim();
                                      return (
                                        <>
                                          <span style={{ fontSize: "14px", fontWeight: "600", color: "#475569", lineHeight: "1" }}>{brandWord}</span>
                                          <h3 style={{ fontSize: "20px", fontWeight: "700", color: "#0F172A", margin: 0, lineHeight: "1.2" }}>
                                            {restOfName}
                                          </h3>
                                        </>
                                      );
                                    } else {
                                      return (
                                        <h3 style={{ fontSize: "20px", fontWeight: "700", color: "#0F172A", margin: 0 }}>
                                          {trimmedName}
                                        </h3>
                                      );
                                    }
                                  })()}
                                </div>
                              </>
                            )}
                          </div>
                          <div className="horizontal-card-details" style={{ width: "100%" }}>
                            <p className="horizontal-card-description" style={{
                                display: "-webkit-box",
                                WebkitLineClamp: 2,
                                WebkitBoxOrient: "vertical",
                                overflow: "hidden",
                                paddingRight: "0",
                                marginBottom: "24px",
                                whiteSpace: "normal",
                                fontSize: "14px",
                                lineHeight: "1.6",
                                color: "#475569"
                            }}>
                              {prod.content || "Access your business tools and modules."}
                            </p>
                          </div>
                        </div>
                        <div className="horizontal-card-open-indicator">
                          <span>Open</span>
                          <ArrowUpRight size={12} />
                        </div>
                      </a>
                    );
                  })
                ) : (
                  <div className="no-results-card">
                    <Database size={40} className="no-results-icon" />
                    <h3>No Active Applications Found</h3>
                    <p>No purchased products match your current filters or search query.</p>
                  </div>
                )}
              </div>

              {/* Explore More Products Section */}
              {unassignedProducts.length > 0 && (
                <div style={{ marginTop: '48px', paddingBottom: '32px' }}>
                  <div className="explore-view-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
                    <div>
                      <h2 style={{ fontSize: '22px', fontWeight: '700', color: '#0F172A', margin: '0 0 6px 0' }}>Explore More Products</h2>
                      <p style={{ color: '#64748B', fontSize: '15px', margin: 0 }}>Discover additional Scaloz products that can help grow your business.</p>
                    </div>
                  </div>

                  <div className="explore-products-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '20px', marginBottom: '32px' }}>
                    {unassignedProducts.map((prod) => {
                      let IconComponent = Globe;
                      let colorClass = "app-color-blue";
                      const nameUpper = (prod.name || "").toUpperCase();
                      if (nameUpper.includes("HRMS") || nameUpper.includes("PEOPLE")) {
                        IconComponent = Users;
                      } else if (nameUpper.includes("MAIL")) {
                        IconComponent = Mail;
                        colorClass = "app-color-purple-gradient";
                      } else if (nameUpper.includes("CHAT")) {
                        IconComponent = MessageSquare;
                        colorClass = "app-color-green-gradient";
                      } else if (nameUpper.includes("CRM")) {
                        IconComponent = Briefcase;
                        colorClass = "app-color-pink-gradient";
                      } else if (nameUpper.includes("PROJECT")) {
                        IconComponent = Layers;
                        colorClass = "app-color-orange-gradient";
                      }

                      return (
                        <a
                          key={prod.id || prod.code}
                          className="explore-product-card"
                          href="https://scaloz.com"
                          target="_blank"
                          rel="noopener noreferrer"
                          style={{
                            background: '#FFFFFF',
                            border: '1px solid #E2E8F0',
                            borderRadius: '12px',
                            padding: '20px',
                            cursor: 'pointer',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '16px',
                            boxShadow: '0 1px 2px rgba(0,0,0,0.02)',
                            transition: 'all 0.2s ease',
                            textDecoration: 'none',
                            color: 'inherit'
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', minHeight: prod.icon ? '24px' : '80px' }}>
                            {prod.icon ? (
                              /* Fixed bounding box — consistent size for all logos */
                              <div style={{
                                width: "100%",
                                height: "24px",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "flex-start",
                                flexShrink: 0
                              }}>
                                <img
                                  src={getImageSrc(prod.icon)}
                                  alt={prod.name}
                                  style={{
                                    height: "24px",
                                    width: "auto",
                                    objectFit: "contain",
                                    display: "block"
                                  }}
                                />
                              </div>
                            ) : (
                              <>
                                <div className={`horizontal-card-icon-wrap ${colorClass}`} style={{ width: "56px", height: "56px", flexShrink: 0, borderRadius: '12px', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#EFF6FF', color: '#2563EB' }}>
                                  <IconComponent size={24} />
                                </div>
                                <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                                  {(() => {
                                    const trimmedName = (prod.name || "").trim();
                                    const firstSpaceIdx = trimmedName.indexOf(" ");
                                    if (firstSpaceIdx > 0) {
                                      const brandWord = trimmedName.substring(0, firstSpaceIdx);
                                      const restOfName = trimmedName.substring(firstSpaceIdx + 1).trim();
                                      return (
                                        <>
                                          <span style={{ fontSize: "12px", fontWeight: "600", color: "#64748B", lineHeight: "1" }}>{brandWord}</span>
                                          <h3 style={{ fontSize: "16px", fontWeight: "700", color: "#0F172A", margin: 0, lineHeight: "1.2" }}>
                                            {restOfName}
                                          </h3>
                                        </>
                                      );
                                    } else {
                                      return (
                                        <h3 style={{ fontSize: "16px", fontWeight: "700", color: "#0F172A", margin: 0 }}>
                                          {trimmedName}
                                        </h3>
                                      );
                                    }
                                  })()}
                                </div>
                              </>
                            )}
                          </div>

                          <div style={{ flex: 1 }}>
                            <p style={{
                              fontSize: "14px",
                              lineHeight: "1.6",
                              color: "#475569",
                              margin: 0,
                              display: "-webkit-box",
                              WebkitLineClamp: 2,
                              WebkitBoxOrient: "vertical",
                              overflow: "hidden"
                            }}>
                              {prod.content || "Access your business tools and modules."}
                            </p>
                          </div>

                          <div style={{ color: '#2563EB', fontSize: '13px', fontWeight: '600', display: 'flex', alignItems: 'center', gap: '4px', marginTop: 'auto' }}>
                            Learn more <span>&rarr;</span>
                          </div>
                        </a>
                      );
                    })}
                  </div>




                </div>
              )}
            </div>
          )}

          {selectedApp === "onboarding" && (
            <Onboarding tenantId={tenantId} />
          )}

          {selectedApp === "settings" && (
            <SettingsModule tenantId={tenantId} allowedProducts={allowedProducts} />
          )}

          {/* DYNAMIC PRODUCT SUB-DASHBOARD */}
          {activeProduct && (
            <div className="module-dashboard-view">
              <div className="module-nav-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", width: "100%" }}>
                <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                  <button className="back-to-launchpad-btn" onClick={() => setSelectedApp("launchpad")}>
                    <ArrowLeft size={16} />
                    <span>Back to Launchpad</span>
                  </button>
                  <div className="module-title-wrap">
                    <h2 style={{ textTransform: "uppercase" }}>{activeProduct.productName}</h2>
                    <span className="module-badge">Active</span>
                  </div>
                </div>
                <button
                  className="quick-action-btn"
                  style={{ background: "#6366F1", color: "#FFFFFF", fontWeight: "600", padding: "10px 20px" }}
                  onClick={() => {
                    window.location.href = getBrandedLaunchUrl(activeProduct);
                  }}
                >
                  Open Application Portal
                </button>
              </div>

              <div className="sub-modules-section" style={{ marginTop: "24px" }}>
                <h3 style={{ fontSize: "14px", fontWeight: "700", color: "var(--text-secondary)", marginBottom: "16px" }}>ENABLED MODULES</h3>
                <div className="sub-modules-grid">
                  {activeProduct.modules && activeProduct.modules.length > 0 ? (
                    activeProduct.modules.map((m) => (
                      <div className="sub-module-card" key={m} style={{ padding: "20px" }}>
                        <div className="sub-module-icon-container color-blue">
                          <Layers size={20} />
                        </div>
                        <div className="sub-module-details">
                          <h4 style={{ fontSize: "15px", fontWeight: "700" }}>{m}</h4>
                          <p>Manage and access live {m.toLowerCase()} dashboard operations for this tenant</p>
                        </div>
                      </div>
                    ))
                  ) : (
                    <div style={{ color: "#9CA3AF", fontStyle: "italic", padding: "20px" }}>No modules are enabled for this product.</div>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

export default Dashboard;
