import React, { useState, useEffect } from "react";
import { ArrowLeft, ArrowRight, Eye, EyeOff, CheckCircle } from "lucide-react";
import scalozLogo from "../assets/Scaloz.png";
import { encryptPayload, decryptPayload } from "../utils/crypto";
import "./Login.css";

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

function ResetPassword() {
  const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || `${globalThis.location.origin}/api`;
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

  const getImageUrl = (url) => {
    if (!url || typeof url !== 'string') return "";
    if (url.startsWith("data:") || url.startsWith("http://") || url.startsWith("https://")) {
      return url;
    }
    if (url.startsWith("/uploads") || url.startsWith("uploads/")) {
      const cleanPath = url.startsWith("/") ? url : `/${url}`;
      const serverBase = API_BASE_URL.endsWith("/api") ? API_BASE_URL.slice(0, -4) : API_BASE_URL;
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
      const serverBase = API_BASE_URL.endsWith("/api") ? API_BASE_URL.slice(0, -4) : API_BASE_URL;
      return `${serverBase}${cleanPath}`;
    }
    return `data:image/png;base64,${url}`;
  };

  // Fetch public slides
  useEffect(() => {
    fetch(`${API_BASE_URL}/public/slides`)
      .then(res => {
        if (res.ok) return res.json();
        throw new Error("Failed to fetch slides");
      })
      .then(data => {
        if (Array.isArray(data)) {
          setSlides(data);
        }
      })
      .catch(err => {
        console.error("Error fetching slides:", err);
      });
  }, [API_BASE_URL]);

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
    const hash = globalThis.location.hash;
    const queryIndex = hash.indexOf("?");
    if (queryIndex !== -1) {
      const queryStr = hash.substring(queryIndex);
      const params = new URLSearchParams(queryStr);
      const tok = params.get("token");
      if (tok) {
        setToken(tok);
        return;
      }
    }

    const params = new URLSearchParams(globalThis.location.search);
    const tok = params.get("token");
    if (tok) {
      setToken(tok);
    } else {
      setError("Reset token is missing or invalid. Please check your email link again.");
    }
  }, []);

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
      const payload = await encryptPayload({ token, newPassword });
      const response = await fetch(`${API_BASE_URL}/auth/reset-password`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });

      const rawData = await response.json();
      const data = await decryptPayload(rawData);

      if (!response.ok) {
        throw new Error(data.message || "Failed to reset password. Please try again.");
      }

      setMessage("Password reset successfully! Redirecting to login...");
      setTimeout(() => {
        globalThis.location.href = "/";
      }, 2000);
    } catch (err) {
      setError(err.message || "Server error. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
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
                        key={slide.id || `slide-${idx}`}
                        type="button"
                        className={`slide-dot ${idx === currentSlideIndex ? "active" : ""}`}
                        aria-label={`Go to slide ${idx + 1}`}
                        onClick={() => setCurrentSlideIndex(idx)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            setCurrentSlideIndex(idx);
                          }
                        }}
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
          <div className="login-card">
            {message ? (
              <div style={{ textAlign: "center", padding: "10px 0" }}>
                <div style={{
                  width: 64, height: 64,
                  background: "linear-gradient(135deg, #16a34a, #22c55e)",
                  borderRadius: "50%",
                  display: "flex", alignItems: "center", justifyContent: "center",
                  color: "#fff",
                  margin: "0 auto 20px",
                  boxShadow: "0 10px 24px rgba(34,197,94,0.2)"
                }}>
                  <CheckCircle size={28} />
                </div>
                <h2 className="slide-title" style={{ fontSize: "22px", marginBottom: "8px" }}>
                  Reset Successful
                </h2>
                <p className="slide-description" style={{ fontSize: "13.5px", marginBottom: "0px" }}>
                  {message}
                </p>
              </div>
            ) : (
              <>
                <div className="login-header">
                  <h2>Reset Your Password</h2>
                  <p>Choose a secure new password for your account.</p>
                </div>

                <form onSubmit={handleResetPassword} className="login-form">
                  {/* New Password */}
                  <div className="form-group">
                    <label htmlFor="newPassword">New Password</label>
                    <div className="password-group">
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
                        {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
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
                  <div className="form-group">
                    <label htmlFor="confirmPassword">Confirm Password</label>
                    <div className="password-group">
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
                        {showConfirm ? <EyeOff size={18} /> : <Eye size={18} />}
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
                    <div className="error-message" role="alert">{error}</div>
                  )}

                  <button type="submit" className="login-btn" disabled={loading || !token || !newPassword || newPassword !== confirmPassword}>
                    {loading ? "Updating..." : "Reset Password"}
                    {!loading && <ArrowRight size={16} />}
                  </button>
                </form>

                <div style={{ textAlign: "center", marginTop: "16px" }}>
                  <a 
                    href="/" 
                    className="forgot-link" 
                    style={{ display: "inline-flex", alignItems: "center", gap: "6px", fontWeight: "600", fontSize: "13px" }}
                    onClick={(e) => {
                      e.preventDefault();
                      globalThis.history.pushState(null, '', '/');
                      globalThis.dispatchEvent(new Event('popstate'));
                    }}
                  >
                    <ArrowLeft size={14} /> Back to Log In
                  </a>
                </div>
              </>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="global-footer-text">
          <span>© 2026 Scaloz. Powered by Xevyte Technologies Pvt. Ltd.</span>
          <div className="footer-links">
            <a href="#terms">Terms & Conditions</a>
            <span className="separator">|</span>
            <a href="#privacy">Privacy Policy</a>
            <span className="separator">|</span>
            <a href="#cookies">Cookies Policy</a>
          </div>
        </div>
      </div>
    </div>
  );
}

export default ResetPassword;
