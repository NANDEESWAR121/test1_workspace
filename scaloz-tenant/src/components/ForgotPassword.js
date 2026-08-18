import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import api from "../api";
import { getTenantSubdomain } from "../utils/tenant";
import { encryptPayload } from "../utils/crypto";
import "./LoginPage.css";
import { FiArrowLeft, FiChevronRight, FiCheckCircle } from "react-icons/fi";
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

function ForgotPassword() {
  const [employeeId, setEmployeeId] = useState("");
  const [tenantInfo, setTenantInfo] = useState(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const sub = getTenantSubdomain();
    if (sub && sub !== "www" && sub !== "scaloz") {
      api.get(`/public/tenant-info/subdomain/${sub}`)
        .then(res => setTenantInfo(res.data))
        .catch(err => console.error("Subdomain lookup failed:", err));
    }
  }, []);

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

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      let finalId = employeeId.trim();
      if (tenantInfo?.code && !finalId.includes('@')) {
        if (!finalId.startsWith(tenantInfo.code + "_")) {
          finalId = `${tenantInfo.code}_${finalId}`;
        }
      }
      const rawPayload = { employeeId: finalId, portal: "tenant" };
      const encryptedPayload = await encryptPayload(rawPayload);
      const res = await api.post("/auth/forgot-password", encryptedPayload);
      if (res.status === 200) {
        setSuccess(true);
      } else {
        setError("Something went wrong. Try again.");
      }
    } catch (err) {
      if (err.response?.status === 404) {
        setError(err.response?.data?.message || "Invalid Employee ID or Email. Please try again.");
      } else {
        setError(err.response?.data?.message || "Server error. Try again later.");
      }
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
            {success ? (
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
                  Request Received
                </h1>
                <p style={{ fontSize: 14, color: "#64748b", marginBottom: 28, lineHeight: "1.6" }}>
                  If an account exists for this Employee ID, a password reset link has been sent.
                </p>
                <Link to="/" className="auth-back-link" style={{ display: "inline-flex", alignItems: "center", gap: 8, color: "#2563eb", fontWeight: 600, textDecoration: "none" }}>
                  <FiArrowLeft size={16} /> Back to Login
                </Link>
              </div>
            ) : (
              <>
                <div className="form-card-header">
                  <h2>Forgot Password?</h2>
                  <p>Enter your employee ID or email and we'll send a password reset link to your registered work email.</p>
                </div>

                <form onSubmit={handleSubmit} className="enterprise-form">
                  <div className="enterprise-field">
                    <label htmlFor="empId">Employee ID or Work Email</label>
                    <input
                      id="empId"
                      type="text"
                      placeholder="Enter employee ID or email"
                      value={employeeId}
                      onChange={(e) => { setEmployeeId(e.target.value); if (error) setError(""); }}
                      required
                      autoFocus
                    />
                  </div>

                  {error && (
                    <div className="enterprise-error" role="alert">{error}</div>
                  )}

                  <button type="submit" className="enterprise-signin-btn" disabled={loading || !employeeId}>
                    {loading
                      ? "Sending..."
                      : <><span>Send Reset Link</span><FiChevronRight size={18} /></>
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

export default ForgotPassword;
