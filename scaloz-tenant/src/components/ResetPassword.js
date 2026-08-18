import React, { useState, useEffect } from "react";
import { useLocation, useNavigate, Link } from "react-router-dom";
import api from "../api";
import { encryptPayload } from "../utils/crypto";
import "./LoginPage.css";
import { FiChevronRight, FiEye, FiEyeOff, FiCheckCircle, FiArrowLeft } from "react-icons/fi";
import scalozLogo from "../assets/Scaloz.png";

const defaultSlides = [
  {
    id: "default-1",
    title: "Unified Enterprise Portal",
    description: "Access all your business tools, chat, emails, and CRM platforms seamlessly from a single secure login.",
    imageUrl: ""
  },
  {
    id: "default-2",
    title: "Real-time Collaboration",
    description: "Keep your workforce aligned and engaged with instant messaging, shared calendars, and interactive project management.",
    imageUrl: ""
  },
  {
    id: "default-3",
    title: "Secure Data Management",
    description: "Enterprise-grade safety features keep your workspace and confidential organizational details secure at all times.",
    imageUrl: ""
  }
];

const getImageUrl = (url) => {
  if (!url || typeof url !== 'string') return "";
  if (url.startsWith("data:") || url.startsWith("http://") || url.startsWith("https://")) {
    return url;
  }
  if (url.startsWith("/uploads") || url.startsWith("uploads/")) {
    const cleanPath = url.startsWith("/") ? url : `/${url}`;
    const baseURL = api.defaults.baseURL || "";
    const serverBase = baseURL.endsWith("/api") ? baseURL.slice(0, -4) : baseURL;
    return `${serverBase}${cleanPath}`;
  }
  const lower = url.toLowerCase().trim();
  const isLegacy = lower.endsWith(".png") ||
    lower.endsWith(".jpg") ||
    lower.endsWith(".jpeg") ||
    lower.endsWith(".gif") ||
    lower.endsWith(".svg") ||
    lower.includes("/") ||
    lower.includes("\\");
  if (isLegacy) {
    const cleanPath = url.startsWith("uploads/") ? `/${url}` : `/uploads/logos/${url}`;
    const baseURL = api.defaults.baseURL || "";
    const serverBase = baseURL.endsWith("/api") ? baseURL.slice(0, -4) : baseURL;
    return `${serverBase}${cleanPath}`;
  }
  return `data:image/png;base64,${url}`;
};

function ResetPassword() {
  const location = useLocation();
  const navigate = useNavigate();

  const [token, setToken] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [loading, setLoading] = useState(false);

  /* Slides States */
  const [slides, setSlides] = useState([]);
  const [currentSlideIndex, setCurrentSlideIndex] = useState(0);

  // Fetch public slides
  useEffect(() => {
    api.get("/public/slides")
      .then(res => {
        if (Array.isArray(res.data)) {
          setSlides(res.data);
        }
      })
      .catch(err => {
        console.error("Error fetching slides:", err);
      });
  }, []);

  // Automatic slide rotation
  useEffect(() => {
    const displayCount = slides.length > 0 ? slides.length : defaultSlides.length;
    if (displayCount <= 1) return;
    const interval = setInterval(() => {
      setCurrentSlideIndex(prev => (prev + 1) % displayCount);
    }, 5000);
    return () => clearInterval(interval);
  }, [slides]);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const tok = params.get("token");
    if (tok) {
      setToken(tok);
    } else {
      setError("Reset token is missing or invalid. Please check your email link again.");
    }
  }, [location]);

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

  const handleResetPassword = async (e) => {
    e.preventDefault();
    setError("");
    setMessage("");

    if (!token) {
      setError("Invalid or missing reset token.");
      return;
    }

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
      const rawPayload = { token, newPassword };
      const encryptedPayload = await encryptPayload(rawPayload);
      const res = await api.post("/auth/reset-password", encryptedPayload);

      if (res.status === 200) {
        setMessage("Password reset successfully! Redirecting to login...");
        setTimeout(() => {
          navigate("/");
        }, 2000);
      } else {
        setError("Failed to reset password. Try again.");
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
          {(() => {
            const displaySlides = slides.length > 0 ? slides : defaultSlides;
            if (displaySlides.length === 0) return null;
            const currentSlide = displaySlides[currentSlideIndex];
            return (
              <div className="login-card-module">
                {currentSlide.imageUrl ? (
                  <div className="slide-image-wrapper">
                    <img
                      src={getImageUrl(currentSlide.imageUrl)}
                      alt={currentSlide.title || "Slide"}
                      className="slide-image"
                    />
                  </div>
                ) : (
                  <div className="fallback-slide-icon">
                    {currentSlide.title ? currentSlide.title[0].toUpperCase() : "S"}
                  </div>
                )}
                <h2 className="slide-title">{currentSlide.title}</h2>
                <p className="slide-description">{currentSlide.description}</p>
                {displaySlides.length > 1 && (
                  <div className="slide-dots">
                    {displaySlides.map((slide, idx) => (
                      <button
                        key={slide.id || slide.title}
                        type="button"
                        className={`slide-dot ${idx === currentSlideIndex ? "active" : ""}`}
                        onClick={() => setCurrentSlideIndex(idx)}
                        aria-label={`Slide ${idx + 1}`}
                      />
                    ))}
                  </div>
                )}
              </div>
            );
          })()}
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
                  Password Reset!
                </h1>
                <p style={{ fontSize: 14, color: "#64748b", marginBottom: 28 }}>
                  {message}
                </p>
              </div>
            ) : (
              <>
                <div className="form-card-header">
                  <h2>Reset Your Password</h2>
                  <p>Choose a secure new password for your account.</p>
                </div>

                <form onSubmit={handleResetPassword} className="enterprise-form">
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
                        disabled={!token}
                      />
                      <button type="button" className="pw-toggle" onClick={() => setShowPassword(!showPassword)} aria-label="Toggle visibility" disabled={!token}>
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
                    <label htmlFor="confirmPassword">Confirm Password</label>
                    <div className="password-field-wrap">
                      <input
                        id="confirmPassword"
                        type={showConfirm ? "text" : "password"}
                        placeholder="Confirm your new password"
                        value={confirmPassword}
                        onChange={(e) => { setConfirmPassword(e.target.value); if (error) setError(""); }}
                        required
                        disabled={!token}
                      />
                      <button type="button" className="pw-toggle" onClick={() => setShowConfirm(!showConfirm)} aria-label="Toggle visibility" disabled={!token}>
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

                  <button type="submit" className="enterprise-signin-btn" disabled={loading || !token || !newPassword || newPassword !== confirmPassword}>
                    {loading
                      ? "Updating..."
                      : <><span>Reset Password</span><FiChevronRight size={18} /></>
                    }
                  </button>
                </form>

                <Link to="/" className="auth-back-link" style={{ marginTop: "24px", display: "inline-flex", alignItems: "center", gap: 8 }}>
                  <FiArrowLeft size={15} /> Back to Log In
                </Link>
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

export default ResetPassword;
