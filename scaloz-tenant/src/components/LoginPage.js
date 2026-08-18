/* global globalThis */
import React, { useState, useEffect, useRef } from "react";
import PropTypes from "prop-types";
import { Link, useNavigate } from "react-router-dom";
import api from "../api";
import { getTenantSubdomain } from "../utils/tenant";
import { encryptPayload } from "../utils/crypto";
import "./LoginPage.css";
import { FiEye, FiEyeOff, FiChevronRight, FiMail, FiArrowLeft, FiCheckCircle, FiUsers, FiLayers, FiShield } from "react-icons/fi";
import scalozLogo from "../assets/Scaloz.png";
import scalozFlowImg from "../assets/scaloz flow 1.png";
import { QRCodeSVG } from "qrcode.react";

/* ─────────────────────────────────────────────────────────────
   Helper: debounce
───────────────────────────────────────────────────────────── */
function useDebounce(value, delay) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

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



/* ─────────────────────────────────────────────────────────────
   Custom hook: PWA install prompt & banner
   (extracted to reduce LoginPage cognitive complexity)
───────────────────────────────────────────────────────────── */
function usePWAInstall() {
  const [installPrompt, setInstallPrompt] = useState(null);
  const [showInstallBanner, setShowInstallBanner] = useState(false);
  const [isIOS, setIsIOS] = useState(false);

  useEffect(() => {
    const isPWAInstalled = () =>
      globalThis.matchMedia("(display-mode: standalone)").matches ||
      globalThis.navigator.standalone === true;

    const checkPWA = () => {
      if (isPWAInstalled()) { setShowInstallBanner(false); return; }
      const ua = globalThis.navigator.userAgent.toLowerCase();
      if (/iphone|ipad|ipod/.test(ua)) setIsIOS(true);
      if (localStorage.getItem("pwa_install_dismissed") !== "true") setShowInstallBanner(true);
    };
    checkPWA();

    const handleBeforeInstallPrompt = (e) => {
      e.preventDefault();
      setInstallPrompt(e);
      if (!isPWAInstalled() && localStorage.getItem("pwa_install_dismissed") !== "true")
        setShowInstallBanner(true);
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

  return { showInstallBanner, isIOS, handleInstallClick, handleDismissBanner };
}

/* ─────────────────────────────────────────────────────────────
   Helper: write login session data to sessionStorage
   (extracted to reduce handleLogin cognitive complexity)
───────────────────────────────────────────────────────────── */
const storeLoginSession = (d, token) => {
  sessionStorage.setItem("token", token);
  sessionStorage.setItem("tenantId", d.tenant?.id ?? "");
  sessionStorage.setItem("tenantCode", d.tenant?.code ?? "");
  sessionStorage.setItem("tenantName", d.tenant?.name ?? "");
  const name = d.user?.name || (d.user?.firstName
    ? `${d.user.firstName} ${d.user.lastName || ""}`.trim()
    : "Admin");
  sessionStorage.setItem("userName", name);
  sessionStorage.setItem("userRole", d.user?.role ?? "Admin");
  sessionStorage.setItem("isSubAdmin", d.user?.isSubAdmin ? "true" : "false");
  safeSetSessionStorage("products", JSON.stringify(d.products || []));
};

const getWorkspaceUrl = (tenant) => {
  // Use sanitized tenant name as the primary identifier for the URL
  const nameSlug = (tenant.tenantName || "").toLowerCase()
    .replace(/[^a-z0-9]/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');

  // Fallback to existing code/domain if name is missing
  const identifier = nameSlug || (tenant.code || tenant.domain || "").split('.')[0].toLowerCase();

  const { hostname, port, protocol } = globalThis.location;
  const mainDomain = process.env.REACT_APP_MAIN_DOMAIN;
  const workspacePrefix = process.env.REACT_APP_WORKSPACE_PREFIX;

  if (hostname.includes('localhost') || hostname === '127.0.0.1') {
    const portStr = port ? `:${port}` : '';
    return `${protocol}//${identifier}.localhost${portStr}`;
  }
  return `https://${identifier}.${workspacePrefix}.${mainDomain}`;
};

const getWorkspaceDisplayUrl = (tenant) => {
  const nameSlug = (tenant.tenantName || "").toLowerCase()
    .replace(/[^a-z0-9]/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');

  const identifier = nameSlug || (tenant.code || tenant.domain || "").split('.')[0].toLowerCase();

  const { hostname, port } = globalThis.location;
  const mainDomain = process.env.REACT_APP_MAIN_DOMAIN;
  const workspacePrefix = process.env.REACT_APP_WORKSPACE_PREFIX;

  if (hostname.includes('localhost') || hostname === '127.0.0.1') {
    const portStr = port ? `:${port}` : '';
    return `${identifier}.localhost${portStr}`;
  }
  return `${identifier}.${workspacePrefix}.${mainDomain}`;
};

const prepareLoginPayload = (email, password, tenantInfo) => {
  let loginId = email;
  if (tenantInfo?.code && !loginId.includes('@') && !loginId.startsWith(tenantInfo.code + "_")) {
    loginId = `${tenantInfo.code}_${loginId}`;
  }
  const rawPayload = { email: loginId, password };
  if (tenantInfo?.code) rawPayload.tenantCode = tenantInfo.code;
  return { loginId, rawPayload };
};

const parseLoginError = (err) => {
  if (err.response) {
    return err.response.data?.message || "Login failed. Please contact your administrator.";
  }
  if (err.request) {
    return "Cannot reach the server. Make sure the backend is running.";
  }
  return err.message || "Login failed. Please try again.";
};

const isValidRedirectTarget = (url) => {
  if (!url) return false;
  if (url.startsWith('/') && !url.startsWith('//')) {
    return true;
  }
  try {
    const parsed = new URL(url);
    const host = parsed.hostname;
    if (host === 'localhost' || host === '127.0.0.1' || host.endsWith('.localhost')) {
      return true;
    }
    const mainDomain = process.env.REACT_APP_MAIN_DOMAIN;
    if (mainDomain && (host === mainDomain || host.endsWith('.' + mainDomain))) {
      return true;
    }
  } catch (e) { }
  return false;
};

const handleLoginSuccess = (d, loginId, password, tenantInfo, redirectTo, navigate) => {
  if (d.mustChangePassword) {
    navigate("/change-password", {
      state: {
        employeeId: d.employeeId || loginId,
        email: d.email || loginId,
        tempPassword: password,
        tenantCode: d.tenantCode || tenantInfo?.code
      }
    });
    return true;
  }
  if (d.token) {
    storeLoginSession(d, d.token);
    if (redirectTo && isValidRedirectTarget(redirectTo)) {
      const separator = redirectTo.includes('?') ? '&' : '?';
      globalThis.location.href = `${redirectTo}${separator}scaloz_token=${encodeURIComponent(d.token)}`;
      return true;
    }
    navigate("/Home");
    return true;
  }
  return false;
};

/* ─────────────────────────────────────────────────────────────
   Main Component
   ───────────────────────────────────────────────────────── */
function useLoginPageState(navigate) {
  const [redirectTo, setRedirectTo] = useState(null);
  const { showInstallBanner, isIOS, handleInstallClick, handleDismissBanner } = usePWAInstall();
  const [qrUrl, setQrUrl] = useState("");
  const [slides, setSlides] = useState([]);
  const [currentSlideIndex, setCurrentSlideIndex] = useState(0);
  const [slidesLoading, setSlidesLoading] = useState(true);
  const [step, setStep] = useState("email");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [subdomain, setSubdomain] = useState(null);
  const [lookupState, setLookupState] = useState("idle");
  const [tenantInfo, setTenantInfo] = useState(null);
  const debouncedEmail = useDebounce(email, 600);
  const pwRef = useRef(null);

  useEffect(() => {
    setQrUrl(globalThis.location.href);
  }, []);

  useEffect(() => {
    api.get("/public/slides")
      .then(res => {
        if (Array.isArray(res.data)) {
          setSlides(res.data);
        }
      })
      .catch(err => {
        console.error("Error fetching slides:", err);
      })
      .finally(() => {
        setSlidesLoading(false);
      });
  }, []);

  useEffect(() => {
    const displayCount = slides.length;
    if (displayCount <= 1) return;
    const interval = setInterval(() => {
      setCurrentSlideIndex(prev => (prev + 1) % displayCount);
    }, 5000);
    return () => clearInterval(interval);
  }, [slides]);

  useEffect(() => {
    const params = new URLSearchParams(globalThis.location.search);
    const rt = params.get('redirect_to');
    if (rt) {
      console.log('[SSO] Login request came from:', rt);
      setRedirectTo(rt);
      params.delete('redirect_to');
      const newSearch = params.toString();
      const cleanURL = globalThis.location.pathname + (newSearch ? `?${newSearch}` : '');
      globalThis.history.replaceState({}, document.title, cleanURL);
    }
  }, []);

  useEffect(() => {
    const sub = getTenantSubdomain();
    if (sub) {
      setSubdomain(sub);
      setLoading(true);
      api.get(`/auth/lookup?code=${encodeURIComponent(sub)}`)
        .then(res => {
          if (res.data?.inactive) {
            setError(res.data.message || "You don't have access for this. Please contact your administrator.");
            setLookupState("notfound");
            return;
          }
          if (res.data?.found) {
            setTenantInfo(res.data);
            setLookupState("found");
            setEmail("");
            setStep("password");
          } else {
            setError(`Workspace "${sub}" not found.`);
            setLookupState("notfound");
          }
        })
        .catch((err) => {
          const msg = err.response?.data?.message || `Workspace "${sub}" not found or cannot be reached.`;
          setError(msg);
          setLookupState("notfound");
        })
        .finally(() => {
          setLoading(false);
        });
    }
  }, []);

  useEffect(() => {
    if (subdomain) return;
    const isEmail = /^[^\s@]+@[^\s@.]+(?:\.[^\s@.]+)*\.[^\s@.]{2,}$/.test(debouncedEmail);
    if (!isEmail) {
      setLookupState("idle");
      setTenantInfo(null);
      return;
    }
    let cancelled = false;
    setLookupState("loading");
    setTenantInfo(null);

    api.get(`/auth/lookup?email=${encodeURIComponent(debouncedEmail)}`)
      .then(res => {
        if (cancelled) return;
        if (res.data?.inactive) {
          setError(res.data.message || "You don't have access for this. Please contact your administrator.");
          setLookupState("notfound");
          return;
        }
        if (res.data?.found) {
          setTenantInfo(res.data);
          setLookupState("found");
        } else {
          setLookupState("notfound");
        }
      })
      .catch((err) => {
        if (!cancelled) {
          const msg = err.response?.data?.message || "No workspace for this domain";
          setError(msg);
          setLookupState("notfound");
        }
      });

    return () => { cancelled = true; };
  }, [debouncedEmail, subdomain]);

  const isAccessDenied = error && (
    error.toLowerCase().includes("access") ||
    error.toLowerCase().includes("inactive") ||
    error.toLowerCase().includes("administrator")
  );

  const getFooterText = () => {
    const year = new Date().getFullYear();
    return `© ${year} Scaloz. Powered by Xevyte Technologies Pvt. Ltd.`;
  };

  const handleContinue = (e) => {
    e.preventDefault();
    if (lookupState !== "found" || !tenantInfo) return;
    setError("");
    let targetUrl = getWorkspaceUrl(tenantInfo);
    if (redirectTo && isValidRedirectTarget(redirectTo)) {
      const separator = targetUrl.includes('?') ? '&' : '?';
      targetUrl = `${targetUrl}${separator}redirect_to=${encodeURIComponent(redirectTo)}`;
    }
    globalThis.location.href = targetUrl;
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const { loginId, rawPayload } = prepareLoginPayload(email, password, tenantInfo);
      const encryptedPayload = await encryptPayload(rawPayload);
      const res = await api.post("/auth/login", encryptedPayload);
      if (res.status === 200) {
        const success = handleLoginSuccess(res.data, loginId, password, tenantInfo, redirectTo, navigate);
        if (!success) {
          setError("Invalid credentials. Please try again.");
        }
      } else {
        setError("Invalid credentials. Please try again.");
      }
    } catch (err) {
      setError(parseLoginError(err));
    } finally {
      setLoading(false);
    }
  };

  const handleBack = () => {
    setStep("email");
    setPassword("");
    setError("");
  };

  const lookupLabel = () => {
    if (isAccessDenied) return null;
    if (lookupState === "loading") return <span className="lookup-status loading"><span className="spin-icon">⟳</span> Looking up workspace…</span>;
    if (lookupState === "found") return <span className="lookup-status found"><FiCheckCircle size={13} /> Workspace found</span>;
    if (lookupState === "notfound") return <span className="lookup-status notfound">⚠ No workspace for this domain</span>;
    return null;
  };

  return {
    redirectTo,
    showInstallBanner,
    isIOS,
    handleInstallClick,
    handleDismissBanner,
    qrUrl,
    slides,
    slidesLoading,
    currentSlideIndex,
    setCurrentSlideIndex,
    step,
    setStep,
    email,
    setEmail,
    password,
    setPassword,
    showPw,
    setShowPw,
    error,
    setError,
    loading,
    setLoading,
    subdomain,
    isAccessDenied,
    getFooterText,
    lookupState,
    tenantInfo,
    pwRef,
    handleContinue,
    handleLogin,
    handleBack,
    lookupLabel
  };
}

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
              <FiUsers size={20} />
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
              <FiLayers size={20} />
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
              <FiShield size={20} />
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

const LoginSlides = ({ slides, slidesLoading, currentSlideIndex, setCurrentSlideIndex }) => {
  if (slidesLoading) {
    /* Silent placeholder while API loads — prevents flicker of default content */
    return <div className="static-feature-view" style={{ visibility: 'hidden' }} />;
  }
  if (slides && slides.length > 0) {
    const currentSlide = slides[currentSlideIndex];
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
        {currentSlide.badge && (
          <span className="slide-badge">{currentSlide.badge}</span>
        )}
        <h2 className="slide-title">{currentSlide.title}</h2>
        <p className="slide-description">{currentSlide.description}</p>
        {slides.length > 1 && (
          <div className="slide-dots">
            {slides.map((slide, idx) => (
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
  }

  return <StaticFeatureView />;
};



const EmailStepForm = ({
  email,
  setEmail,
  error,
  setError,
  lookupState,
  tenantInfo,
  handleContinue,
  isAccessDenied,
  lookupLabel
}) => {
  return (
    <>
      <div className="form-card-header">
        <h2>Sign in to your workspace</h2>
        <p>Enter your work email — we'll find your domain's workspace instantly</p>
      </div>

      <form onSubmit={handleContinue} className="enterprise-form" noValidate>
        <div className="enterprise-field" title={isAccessDenied ? error : ""}>
          <label htmlFor="email">Work Email</label>
          <div className="input-wrapper" style={isAccessDenied ? { cursor: "not-allowed" } : {}}>
            <input
              id="email"
              type="email"
              placeholder="Type here..."
              value={email}
              onChange={e => { if (!isAccessDenied) { setEmail(e.target.value); setError(""); } }}
              disabled={isAccessDenied}
              title={isAccessDenied ? error : ""}
              style={isAccessDenied ? { cursor: "not-allowed", backgroundColor: "#E5E7EB", color: "#9CA3AF" } : {}}
              autoFocus={!isAccessDenied}
              autoComplete="email"
            />
          </div>
          {lookupLabel()}
        </div>

        {/* Tenant found card */}
        {lookupState === "found" && tenantInfo && (
          <div className="tenant-found-card">
            {/* Row 1: tenant identity */}
            <div className="tenant-found-header">
              {tenantInfo.logo
                ? <img src={getImageUrl(tenantInfo.logo)} alt="" className="tenant-found-logo" />
                : <div className="tenant-found-initial">{(tenantInfo.tenantName || "?")[0].toUpperCase()}</div>
              }
              <div className="tenant-found-info">
                <span className="tenant-found-name">{tenantInfo.tenantName}</span>
              </div>
            </div>

            {/* Row 2: email being used */}
            <div className="tenant-found-email-row">
              <FiMail size={12} style={{ flexShrink: 0 }} />
              <span>{email}</span>
            </div>

            {/* Dedicated workspace URL section */}
            <div className="tenant-workspace-url-section">
              <span className="tenant-found-products-label">
                Dedicated workspace link
              </span>
              <a
                href={getWorkspaceUrl(tenantInfo)}
                className="tenant-workspace-url-link"
              >
                {getWorkspaceDisplayUrl(tenantInfo)}
              </a>
            </div>
          </div>
        )}

        {error && lookupState !== "notfound" && <div className="enterprise-error" role="alert">{error}</div>}

        <button
          type="submit"
          className="enterprise-signin-btn"
          disabled={lookupState !== "found" || isAccessDenied}
        >
          <span>{lookupState === "found" ? "Go to Workspace" : "Continue"}</span>
          <FiChevronRight size={18} />
        </button>
      </form>
    </>
  );
};

const PasswordStepForm = ({
  subdomain,
  tenantInfo,
  email,
  setEmail,
  password,
  setPassword,
  showPw,
  setShowPw,
  error,
  setError,
  loading,
  pwRef,
  handleLogin,
  handleBack
}) => {
  return (
    <>
      {/* Back button — only if not on subdomain */}
      {!subdomain && (
        <button className="back-btn" onClick={handleBack} type="button">
          <FiArrowLeft size={15} /> Back
        </button>
      )}

      {/* Compact tenant summary — only if not on subdomain */}
      {tenantInfo && !subdomain && (
        <div className="step2-tenant-summary">
          {tenantInfo.logo
            ? <img src={getImageUrl(tenantInfo.logo)} alt="" className="step2-logo" />
            : <div className="step2-initial">{(tenantInfo.tenantName || "?")[0].toUpperCase()}</div>
          }
          <div>
            <div className="step2-tenant-name">{tenantInfo.tenantName}</div>
            <div className="step2-email">{email}</div>
          </div>
        </div>
      )}

      <div className="form-card-header" style={{ marginTop: (!subdomain && tenantInfo) ? "20px" : "0px" }}>
        {subdomain && tenantInfo && (
          <>
            <div style={{ textAlign: "center", marginBottom: "12px" }}>
              {tenantInfo.logo ? (
                <img
                  src={getImageUrl(tenantInfo.logo)}
                  alt={tenantInfo.tenantName}
                  style={{ height: "32px", maxWidth: "150px", objectFit: "contain", display: "block", margin: "0 auto" }}
                />
              ) : (
                <div style={{
                  width: 32, height: 32,
                  background: "linear-gradient(135deg, #0f172a, #1e40af)",
                  borderRadius: "8px",
                  display: "flex", alignItems: "center", justifyContent: "center",
                  color: "#fff", fontSize: 16, fontWeight: 800,
                  margin: "0 auto"
                }}>
                  {(tenantInfo.tenantName || "?")[0].toUpperCase()}
                </div>
              )}
            </div>
            <div className="tenant-brand-subtitle" style={{ fontSize: "14px", fontWeight: "600", color: "#334155", margin: "8px 0 12px 0", textAlign: "center" }}>
              Workforce Intelligence Platform
            </div>
          </>
        )}
        {!subdomain && <h2>Enter your password</h2>}
        <p>{subdomain ? "Sign in to securely access your unified workforce intelligence workspace." : <>Sign in to <strong>{tenantInfo?.tenantName || "your workspace"}</strong></>}</p>
      </div>

      <form onSubmit={handleLogin} className="enterprise-form">
        {/* Emp ID / Mail ID field (only shown on subdomain/tenant login) */}
        {subdomain && (
          <div className="enterprise-field">
            <label htmlFor="email">Employee ID or Work Email</label>
            <div className="input-wrapper">
              <input
                id="email"
                type="text"
                placeholder="Enter your Emp ID or Email"
                value={email}
                onChange={e => { setEmail(e.target.value); setError(""); }}
                required
                autoComplete="username"
                autoFocus
              />
            </div>
          </div>
        )}

        <div className="enterprise-field">
          <div className="field-label-row">
            <label htmlFor="password">Password</label>
            <Link to="/forgot-password" className="forgot-link">Reset Password</Link>
          </div>
          <div className="password-field-wrap">
            <input
              id="password"
              ref={pwRef}
              type={showPw ? "text" : "password"}
              placeholder="Enter your password"
              value={password}
              onChange={e => { setPassword(e.target.value); setError(""); }}
              required
              autoComplete="current-password"
              autoFocus={false}
            />
            <button type="button" className="pw-toggle" onClick={() => setShowPw(!showPw)} aria-label="Toggle password">
              {showPw ? <FiEyeOff size={18} /> : <FiEye size={18} />}
            </button>
          </div>
        </div>

        {error && <div className="enterprise-error" role="alert">{error}</div>}

        <button type="submit" className="enterprise-signin-btn" disabled={loading || !password}>
          {loading ? "Signing in…" : <><span>{subdomain ? "Secure Sign In" : "Sign In"}</span><FiChevronRight size={18} /></>}
        </button>
      </form>
    </>
  );
};

const LoginFooter = ({ footerText }) => {
  return (
    <div className="global-footer-text">
      <span>{footerText}</span>
      <div className="footer-links">
        <a href="/policy/terms_and_conditions" target="_blank" rel="noopener noreferrer">Terms &amp; Conditions</a>
        <span className="separator">|</span>
        <a href="/policy/privacy_policy" target="_blank" rel="noopener noreferrer">Privacy Policy</a>
        <span className="separator">|</span>
        <a href="/policy/cookies_policy" target="_blank" rel="noopener noreferrer">Cookies Policy</a>
      </div>
    </div>
  );
};

const PWAInstallBanner = ({ showInstallBanner, isIOS, handleInstallClick, handleDismissBanner }) => {
  if (!showInstallBanner) return null;
  return (
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
  );
};

function LoginPage() {
  const navigate = useNavigate();
  const state = useLoginPageState(navigate);

  return (
    <div className="enterprise-login-container">
      {/* Logo */}
      <div className="top-left-logo">
        <img src={scalozLogo} alt="Scaloz Logo" />
      </div>

      <div className="left-panel">
        <div className="left-panel-inner">
          <LoginSlides
            slides={state.slides}
            slidesLoading={state.slidesLoading}
            currentSlideIndex={state.currentSlideIndex}
            setCurrentSlideIndex={state.setCurrentSlideIndex}
          />
        </div>


      </div>

      {/* ── RIGHT PANEL ── */}
      <div className="right-panel">
        <div className="right-panel-inner">

          {/* ── Install on Mobile QR (top) ── */}
          {state.qrUrl && state.subdomain && (
            <>
              <div className="mobile-install-card">
                <h3 className="mobile-install-card-title">Access on Mobile</h3>
                <div className="mobile-install-qr-large">
                  <QRCodeSVG
                    value={state.qrUrl}
                    size={110}
                    bgColor="#ffffff"
                    fgColor="#0F172A"
                    level="M"
                  />
                </div>
                <p className="mobile-install-card-hint">Scan the QR code using your mobile device to download and securely access the application.</p>
              </div>
              <div className="or-divider">
                <span>Or continue with credentials</span>
              </div>
            </>
          )}

          {/* ── Login Form Card (bottom) ── */}
          <div className="login-form-card">
            {/* ─── STEP 1: Email ─── */}
            {state.step === "email" && (
              <EmailStepForm
                email={state.email}
                setEmail={state.setEmail}
                error={state.error}
                setError={state.setError}
                lookupState={state.lookupState}
                tenantInfo={state.tenantInfo}
                handleContinue={state.handleContinue}
                isAccessDenied={state.isAccessDenied}
                lookupLabel={state.lookupLabel}
              />
            )}

            {/* ─── STEP 2 & 3: Password / Login ─── */}
            {state.step === "password" && (
              <PasswordStepForm
                subdomain={state.subdomain}
                tenantInfo={state.tenantInfo}
                email={state.email}
                setEmail={state.setEmail}
                password={state.password}
                setPassword={state.setPassword}
                showPw={state.showPw}
                setShowPw={state.setShowPw}
                error={state.error}
                setError={state.setError}
                loading={state.loading}
                pwRef={state.pwRef}
                handleLogin={state.handleLogin}
                handleBack={state.handleBack}
              />
            )}
          </div>

        </div>

        {/* Footer — anchored to right-panel bottom */}
        <LoginFooter footerText={state.getFooterText()} />
      </div>

      <PWAInstallBanner
        showInstallBanner={state.showInstallBanner}
        isIOS={state.isIOS}
        handleInstallClick={state.handleInstallClick}
        handleDismissBanner={state.handleDismissBanner}
      />
    </div>
  );
}

LoginSlides.propTypes = {
  slides: PropTypes.array.isRequired,
  slidesLoading: PropTypes.bool.isRequired,
  currentSlideIndex: PropTypes.number.isRequired,
  setCurrentSlideIndex: PropTypes.func.isRequired
};



EmailStepForm.propTypes = {
  email: PropTypes.string.isRequired,
  setEmail: PropTypes.func.isRequired,
  error: PropTypes.string.isRequired,
  setError: PropTypes.func.isRequired,
  lookupState: PropTypes.string.isRequired,
  tenantInfo: PropTypes.shape({
    tenantName: PropTypes.string,
    logo: PropTypes.string,
    code: PropTypes.string,
    domain: PropTypes.string
  }),
  handleContinue: PropTypes.func.isRequired,
  isAccessDenied: PropTypes.bool.isRequired,
  lookupLabel: PropTypes.func.isRequired
};

PasswordStepForm.propTypes = {
  subdomain: PropTypes.string,
  tenantInfo: PropTypes.shape({
    tenantName: PropTypes.string,
    logo: PropTypes.string,
    code: PropTypes.string
  }),
  email: PropTypes.string.isRequired,
  setEmail: PropTypes.func.isRequired,
  password: PropTypes.string.isRequired,
  setPassword: PropTypes.func.isRequired,
  showPw: PropTypes.bool.isRequired,
  setShowPw: PropTypes.func.isRequired,
  error: PropTypes.string.isRequired,
  setError: PropTypes.func.isRequired,
  loading: PropTypes.bool.isRequired,
  pwRef: PropTypes.object.isRequired,
  handleLogin: PropTypes.func.isRequired,
  handleBack: PropTypes.func.isRequired
};

LoginFooter.propTypes = {
  footerText: PropTypes.string.isRequired
};

PWAInstallBanner.propTypes = {
  showInstallBanner: PropTypes.bool.isRequired,
  isIOS: PropTypes.bool.isRequired,
  handleInstallClick: PropTypes.func.isRequired,
  handleDismissBanner: PropTypes.func.isRequired
};

export default LoginPage;

