import React, { useState, useEffect } from "react";
import { ArrowLeft, ArrowRight, CheckCircle } from "lucide-react";
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

function ForgotPassword() {
  const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || `${globalThis.location.origin}/api`;
  const [employeeId, setEmployeeId] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
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

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const payload = await encryptPayload({ employeeId: employeeId.trim(), portal: "superadmin" });
      const response = await fetch(`${API_BASE_URL}/auth/forgot-password`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        const rawErrData = await response.json().catch(() => ({}));
        const errData = await decryptPayload(rawErrData);
        throw new Error(errData.message || "Failed to submit request. Please try again.");
      }

      setSuccess(true);
    } catch (err) {
      setError(err.message || "Something went wrong. Please try again later.");
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
            {success ? (
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
                  Request Received
                </h2>
                <p className="slide-description" style={{ fontSize: "13.5px", marginBottom: "24px" }}>
                  If an account exists for this Mail ID, a password reset link has been sent to your registered email.
                </p>
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
                  <ArrowLeft size={14} /> Back to Login
                </a>
              </div>
            ) : (
              <>
                <div className="login-header">
                  <h2>Forgot Password?</h2>
                  <p>Enter your registered Mail ID and we'll send you a link to reset your password.</p>
                </div>

                <form className="login-form" onSubmit={handleSubmit}>
                  <div className="form-group">
                    <label htmlFor="employeeId">Mail ID</label>
                    <input
                      type="text"
                      id="employeeId"
                      value={employeeId}
                      onChange={(e) => { setEmployeeId(e.target.value); if (error) setError(""); }}
                      placeholder="Enter your registered Mail ID"
                      required
                      autoFocus
                    />
                  </div>

                  {error && (
                    <div className="error-message">
                      <span>{error}</span>
                    </div>
                  )}

                  <button type="submit" className="login-btn" disabled={loading || !employeeId}>
                    <span>{loading ? "Sending..." : "Send Reset Link"}</span>
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

export default ForgotPassword;
