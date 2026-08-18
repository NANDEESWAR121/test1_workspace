import React, { useState, useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import api from "../api";
import { encryptPayload } from "../utils/crypto";
import "./LoginPage.css";
import { FiChevronRight, FiEye, FiEyeOff, FiCheckCircle } from "react-icons/fi";
import scalozLogo from "../assets/Scaloz.png";

/* ─────────────────────────────────────────────────────────────
   Safe sessionStorage writer to prevent QuotaExceededError
   for large payloads (e.g. huge product icons)
───────────────────────────────────────────────────────────── */
const safeSetSessionStorage = (key, value) => {
  try {
    sessionStorage.setItem(key, value);
  } catch (e) {
    console.warn(`[Storage] Failed to save ${key} directly, attempting recovery:`, e);
    if (key === "products") {
      try {
        const products = JSON.parse(value);
        const strippedProducts = products.map(p => {
          // If the icon is a base64 string and is too large (e.g. > 50KB), strip it
          if (p.icon && p.icon.length > 50 * 1024) {
            console.warn(`[Storage] Product icon for ${p.productName} is too large (${p.icon.length} bytes). Stripping it.`);
            return { ...p, icon: "" };
          }
          return p;
        });
        sessionStorage.setItem(key, JSON.stringify(strippedProducts));
      } catch (innerError) {
        console.error("[Storage] Failed to save stripped products:", innerError);
      }
    } else {
      throw e;
    }
  }
};

function ChangePassword() {
  const location = useLocation();
  const navigate = useNavigate();

  const { employeeId, tempPassword, tenantCode } = location.state || {};

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // If we don't have the routing state, redirect to login page
    if (!employeeId || !tempPassword) {
      navigate("/");
    }
  }, [employeeId, tempPassword, navigate]);

  const getStrength = (pw) => {
    if (!pw) return 0;
    let score = 0;
    if (pw.length >= 8) score++;
    if (/[A-Z]/.test(pw)) score++;
    if (/\d/.test(pw)) score++;
    if (/[^A-Za-z0-9]/.test(pw)) score++;
    return score;
  };
  const strength = getStrength(newPassword);
  const strengthLabel = ["", "Weak", "Fair", "Good", "Strong"][strength];
  const strengthColor = ["", "#ef4444", "#f97316", "#eab308", "#22c55e"][strength];

  const handleChangePassword = async (e) => {
    e.preventDefault();
    setError("");
    setMessage("");

    if (newPassword !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    const passwordPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{8,}$/;
    if (!passwordPattern.test(newPassword)) {
      setError("Password must be at least 8 characters, include uppercase, lowercase, number, and special character.");
      return;
    }

    setLoading(true);

    try {
      const rawPayload = {
        employeeId,
        tempPassword,
        newPassword,
        tenantCode
      };
      const encryptedPayload = await encryptPayload(rawPayload);
      const res = await api.post("/auth/change-password", encryptedPayload);

      if (res.status === 200 && res.data.token) {
        const d = res.data;
        setMessage("Password changed successfully! Redirecting...");

        // Auto-login: Store session details
        sessionStorage.setItem("token", d.token);
        sessionStorage.setItem("tenantId", d.tenant?.id ?? "");
        sessionStorage.setItem("tenantCode", d.tenant?.code ?? "");
        sessionStorage.setItem("tenantName", d.tenant?.name ?? "");
        sessionStorage.setItem("userName", d.user?.name || (d.user?.firstName ? `${d.user.firstName} ${d.user.lastName || ""}`.trim() : "Admin"));
        sessionStorage.setItem("userRole", d.user?.role ?? "Admin");
        safeSetSessionStorage("products", JSON.stringify(d.products || []));

        setTimeout(() => {
          navigate("/Home");
        }, 1500);
      } else {
        setError("Failed to update password. Try again.");
      }
    } catch (err) {
      setError(err.response?.data?.message || "Server error. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="enterprise-login-container">
      {/* Top Left Global Logo */}
      <div className="top-left-logo">
        <img src={scalozLogo} alt="Scaloz Logo" />
      </div>

      {/* LEFT PANEL */}
      <div className="left-panel">
        <div className="left-panel-inner">
        </div>
      </div>

      {/* RIGHT PANEL */}
      <div className="right-panel">
        <div className="right-panel-inner">
          <div className="login-form-card">
            {message ? (
              <div style={{ textAlign: "center", padding: "20px 0" }}>
                <div style={{
                  width: 72, height: 72,
                  background: "linear-gradient(135deg,#16a34a,#22c55e)",
                  borderRadius: "50%",
                  display: "flex", alignItems: "center", justifyContent: "center",
                  color: "#fff",
                  margin: "0 auto 24px",
                  boxShadow: "0 12px 28px rgba(34,197,94,0.25)"
                }}>
                  <FiCheckCircle size={32} />
                </div>
                <h1 style={{ fontSize: 26, fontWeight: 800, color: "#1e293b", marginBottom: 8 }}>
                  Password Updated!
                </h1>
                <p style={{ fontSize: 14, color: "#64748b", marginBottom: 28 }}>
                  {message}
                </p>
              </div>
            ) : (
              <>
                <div className="form-card-header">
                  <h2>Change Your Password</h2>
                  <p>This is your first login. Please update your password to secure your account.</p>
                </div>

                <form onSubmit={handleChangePassword} className="enterprise-form">
                  {/* New Password */}
                  <div className="enterprise-field">
                    <label htmlFor="newPassword">New Password</label>
                    <div className="password-field-wrap">
                      <input
                        id="newPassword"
                        type={showPassword ? "text" : "password"}
                        placeholder="Enter new password"
                        value={newPassword}
                        onChange={(e) => { setNewPassword(e.target.value); if (error) setError(""); }}
                        required
                      />
                      <button type="button" className="pw-toggle" onClick={() => setShowPassword(!showPassword)} aria-label="Toggle visibility">
                        {showPassword ? <FiEyeOff size={18} /> : <FiEye size={18} />}
                      </button>
                    </div>

                    {/* Password strength bars */}
                    {newPassword && (
                      <div className="pw-strength" style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 8 }}>
                        <div className="pw-strength-bars" style={{ display: "flex", gap: 4, flex: 1 }}>
                          {[1, 2, 3, 4].map(i => (
                            <div key={i} className="pw-bar" style={{ height: 4, flex: 1, borderRadius: 2, background: i <= strength ? strengthColor : "#E5E7EB" }} />
                          ))}
                        </div>
                        <span style={{ color: strengthColor, fontSize: 12, fontWeight: 600, whiteSpace: "nowrap" }}>
                          {strengthLabel}
                        </span>
                      </div>
                    )}
                  </div>

                  {/* Confirm Password */}
                  <div className="enterprise-field">
                    <label htmlFor="confirmPassword">Confirm New Password</label>
                    <div className="password-field-wrap">
                      <input
                        id="confirmPassword"
                        type={showConfirm ? "text" : "password"}
                        placeholder="Re-enter new password"
                        value={confirmPassword}
                        onChange={(e) => { setConfirmPassword(e.target.value); if (error) setError(""); }}
                        required
                      />
                      <button type="button" className="pw-toggle" onClick={() => setShowConfirm(!showConfirm)} aria-label="Toggle visibility">
                        {showConfirm ? <FiEyeOff size={18} /> : <FiEye size={18} />}
                      </button>
                    </div>
                    {confirmPassword && newPassword !== confirmPassword && (
                      <span style={{ fontSize: 12, color: "#DC2626", fontWeight: 500, marginTop: 4, display: "block" }}>Passwords do not match</span>
                    )}
                    {confirmPassword && newPassword === confirmPassword && (
                      <span style={{ fontSize: 12, color: "#16a34a", fontWeight: 500, marginTop: 4, display: "block" }}>✓ Passwords match</span>
                    )}
                  </div>

                  {error && (
                    <div className="enterprise-error" role="alert">{error}</div>
                  )}

                  <button type="submit" className="enterprise-signin-btn" disabled={loading || !newPassword || newPassword !== confirmPassword}>
                    {loading
                      ? "Updating..."
                      : <><span>Update & Login</span><FiChevronRight size={18} /></>
                    }
                  </button>
                </form>
              </>
            )}
          </div>
        </div>

        <div className="global-footer-text">
          <span>© {new Date().getFullYear()} Scaloz. Powered by Xevyte Technologies Pvt. Ltd.</span>
          <div className="footer-links">
            <a href="/policy/terms_and_conditions" target="_blank" rel="noopener noreferrer">Terms & Conditions</a>
            <span className="separator">|</span>
            <a href="/policy/privacy_policy" target="_blank" rel="noopener noreferrer">Privacy Policy</a>
            <span className="separator">|</span>
            <a href="/policy/cookies_policy" target="_blank" rel="noopener noreferrer">Cookies Policy</a>
          </div>
        </div>
      </div>
    </div>
  );
}

export default ChangePassword;