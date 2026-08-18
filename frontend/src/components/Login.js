import React, { useState, useEffect } from "react";
import { Eye, EyeOff, ArrowRight, Users, Layers, Shield } from "lucide-react";
import "./Login.css";
import scalozLogo from "../assets/Scaloz.png";
import scalozFlowImg from "../assets/scaloz flow 1.png";
import { encryptPayload, decryptPayload } from "../utils/crypto";
import { QRCodeSVG } from "qrcode.react";
import PropTypes from "prop-types";



const StaticFeatureView = () => {
  return (
    <div className="static-feature-view">
      <div className="static-feature-content">
        <h1 className="feature-main-title">
          One Platform.<br />
          <span className="blue-gradient-text">Endless</span> Possibilities.
        </h1>
        <p className="feature-main-subtitle">
          Scaloz unifies your people, processes, and business tools in a single secure workspace to help your organization grow smarter.
        </p>

        <div className="feature-list">
          <div className="feature-item">
            <div className="feature-icon-wrapper">
              <Users size={20} />
            </div>
            <div className="feature-content">
              <h3 className="feature-title">Empower Your Workforce</h3>
              <p className="feature-desc">
                Provide your teams with the tools they need to work, collaborate, and innovate anywhere.
              </p>
            </div>
          </div>

          <div className="feature-item">
            <div className="feature-icon-wrapper">
              <Layers size={20} />
            </div>
            <div className="feature-content">
              <h3 className="feature-title">Drive Operational Excellence</h3>
              <p className="feature-desc">
                Automate workflows, simplify processes, and make data-driven decisions with confidence.
              </p>
            </div>
          </div>

          <div className="feature-item">
            <div className="feature-icon-wrapper">
              <Shield size={20} />
            </div>
            <div className="feature-content">
              <h3 className="feature-title">Enterprise-Grade Security</h3>
              <p className="feature-desc">
                Advanced security, compliance standards, and role-based access keep your data safe and secure.
              </p>
            </div>
          </div>
        </div>
      </div>

      <div className="feature-bg-illustration">
        <img src={scalozFlowImg} alt="Scaloz Flow Workspace" className="feature-bg-circle" />
      </div>
    </div>
  );
};

function Login({ onLoginSuccess }) {
  const API_BASE_URL = process.env.REACT_APP_API_BASE_URL || `${globalThis.location.origin}/api`;
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  /* PWA Install States & Effects */
  const [installPrompt, setInstallPrompt] = useState(null);
  const [showInstallBanner, setShowInstallBanner] = useState(false);
  const [isIOS, setIsIOS] = useState(false);

  /* QR Code Widget */
  const [qrUrl, setQrUrl] = useState("");

  useEffect(() => {
    setQrUrl(globalThis.location.href);
  }, []);

  useEffect(() => {
    const isPWAInstalled = () => {
      return (
        globalThis.matchMedia("(display-mode: standalone)").matches ||
        globalThis.navigator.standalone === true
      );
    };

    const checkPWA = () => {
      const installed = isPWAInstalled();
      const dismissed = localStorage.getItem("pwa_install_dismissed") === "true";

      if (installed) {
        setShowInstallBanner(false);
        return;
      }

      const userAgent = globalThis.navigator.userAgent.toLowerCase();
      const isIphoneOrIpad = /iphone|ipad|ipod/.test(userAgent);
      if (isIphoneOrIpad) {
        setIsIOS(true);
      }

      if (!dismissed) {
        setShowInstallBanner(true);
      }
    };

    checkPWA();

    const handleBeforeInstallPrompt = (e) => {
      e.preventDefault();
      setInstallPrompt(e);
      // Ensure banner is showing if not dismissed
      const dismissed = localStorage.getItem("pwa_install_dismissed") === "true";
      if (!isPWAInstalled() && !dismissed) {
        setShowInstallBanner(true);
      }
    };

    const handleAppInstalled = () => {
      setShowInstallBanner(false);
      localStorage.removeItem("pwa_install_dismissed");
      setInstallPrompt(null);
    };

    globalThis.addEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
    globalThis.addEventListener("appinstalled", handleAppInstalled);

    return () => {
      globalThis.removeEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
      globalThis.removeEventListener("appinstalled", handleAppInstalled);
    };
  }, []);

  const handleInstallClick = async () => {
    if (installPrompt) {
      installPrompt.prompt();
      const { outcome } = await installPrompt.userChoice;
      console.log(`[PWA] User choice outcome: ${outcome}`);
      setInstallPrompt(null);
      setShowInstallBanner(false);
    } else {
      alert("To install Scaloz:\n\nClick your browser's menu button (⋮ or ...) and select 'Install App' or 'Add to Home Screen'.");
    }
  };

  const handleDismissBanner = () => {
    localStorage.setItem("pwa_install_dismissed", "true");
    setShowInstallBanner(false);
  };

  /* Slides States */
  const [slides, setSlides] = useState([]);
  const [currentSlideIndex, setCurrentSlideIndex] = useState(0);
  const [slidesLoading, setSlidesLoading] = useState(true);

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
      })
      .finally(() => {
        setSlidesLoading(false);
      });
  }, [API_BASE_URL]);

  // Automatic slide rotation
  useEffect(() => {
    const displayCount = slides.length;
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
      const payload = await encryptPayload({ username, password });
      const response = await fetch(`${API_BASE_URL}/auth/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
        credentials: "include",
      });

      if (!response.ok) {
        const rawErrData = await response.json().catch(() => ({}));
        const errData = await decryptPayload(rawErrData);
        throw new Error(errData.message || "Invalid credentials");
      }

      const rawData = await response.json();
      const data = await decryptPayload(rawData);
      localStorage.setItem("super_admin_token", data.token);
      localStorage.setItem("super_admin_username", data.username);
      localStorage.setItem("super_admin_email", data.email);
      onLoginSuccess();
    } catch (err) {
      setError(err.message || "Failed to log in. Please check your backend.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="left-panel">
        {/* Top Left Global Logo */}
        <div className="top-left-logo">
          <img src={scalozLogo} alt="Scaloz Logo" />
        </div>
        <div className="left-panel-inner">
          {slidesLoading ? (
            /* Silent placeholder while API loads — prevents flicker */
            <div className="static-feature-view" style={{ visibility: 'hidden' }} />
          ) : slides && slides.length > 0 ? (
            <div className="login-card-module">
              {slides[currentSlideIndex].imageUrl ? (
                <div className="slide-image-wrapper">
                  <img
                    src={getImageUrl(slides[currentSlideIndex].imageUrl)}
                    alt={slides[currentSlideIndex].title || "Slide"}
                    className="slide-image"
                  />
                </div>
              ) : (
                <div className="fallback-slide-icon">
                  {slides[currentSlideIndex].title ? slides[currentSlideIndex].title[0].toUpperCase() : "S"}
                </div>
              )}
              {slides[currentSlideIndex].badge && (
                <span className="slide-badge">{slides[currentSlideIndex].badge}</span>
              )}
              <h2 className="slide-title">{slides[currentSlideIndex].title}</h2>
              <p className="slide-description">{slides[currentSlideIndex].description}</p>
              {slides.length > 1 && (
                <div className="slide-dots">
                  {slides.map((slide, idx) => (
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
          ) : (
            <StaticFeatureView />
          )}
        </div>
      </div>

      {/* RIGHT PANEL — Login Form */}
      <div className="right-panel">
        <div className="right-panel-inner">
          {/* ── Install on Mobile QR (top) ── */}
          {qrUrl && (
            <>
              <div className="mobile-install-card">
                <h3 className="mobile-install-card-title">Install on Mobile</h3>
                <div className="mobile-install-qr-large">
                  <QRCodeSVG
                    value={qrUrl}
                    size={110}
                    bgColor="#ffffff"
                    fgColor="#0F172A"
                    level="M"
                  />
                </div>
                <p className="mobile-install-card-hint">Scan with your phone camera to open &amp; install the app</p>
              </div>
              <div className="or-divider">
                <span>OR</span>
              </div>
            </>
          )}

          {/* ── Login Form Card (bottom) ── */}
          <div className="login-card">
            <div className="login-header">
              <p>Enter your credentials to access your workspace</p>
            </div>

            <form className="login-form" onSubmit={handleSubmit}>
              <div className="form-group">
                <label htmlFor="username">Login ID</label>
                <input
                  type="text"
                  id="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="Enter your email"
                  required
                />
              </div>

              <div className="form-group">
                <div className="forgot-row">
                  <label htmlFor="password">Password</label>
                  <a
                    href="/forgot-password"
                    className="forgot-link"
                    onClick={(e) => {
                      e.preventDefault();
                      globalThis.history.pushState(null, '', '/forgot-password');
                      globalThis.dispatchEvent(new Event('popstate'));
                    }}
                  >
                    Forgot password?
                  </a>
                </div>
                <div className="password-group">
                  <input
                    type={showPassword ? "text" : "password"}
                    id="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter your password"
                    required
                  />
                  <button
                    type="button"
                    className="pw-toggle"
                    onClick={() => setShowPassword(!showPassword)}
                    aria-label={showPassword ? "Hide password" : "Show password"}
                  >
                    {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                  </button>
                </div>
                {error && (
                  <div className="error-message" style={{ marginTop: "6px" }}>
                    <span>{error}</span>
                  </div>
                )}
              </div>

              <button type="submit" className="login-btn" disabled={loading}>
                <span>{loading ? "Authenticating..." : "Log In"}</span>
                {!loading && <ArrowRight size={16} />}
              </button>
            </form>
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

      {showInstallBanner && (
        <div className="pwa-install-banner">
          <div className="pwa-banner-content">
            <div className="pwa-banner-text">
              {isIOS ? (
                <>
                  <h4 style={{ marginBottom: "6px" }}>Install Scaloz</h4>
                  <div style={{ fontSize: "12px", color: "#94a3b8", textAlign: "left", lineHeight: "1.5" }}>
                    1. Tap the Share icon<br />
                    2. Select "Add to Home Screen"<br />
                    3. Tap "Add"
                  </div>
                </>
              ) : (
                <>
                  <h4>Install Scaloz</h4>
                  <p>Install Scaloz on your device for quick and easy access.</p>
                </>
              )}
            </div>
          </div>
          <div className="pwa-banner-actions">
            {!isIOS && (
              <button className="pwa-install-btn" onClick={handleInstallClick}>
                Install
              </button>
            )}
            <button className="pwa-close-btn" onClick={handleDismissBanner}>
              ✕
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

Login.propTypes = {
  onLoginSuccess: PropTypes.func.isRequired
};

export default Login;
