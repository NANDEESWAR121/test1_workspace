import React, { useState, useEffect, useCallback } from "react";
import Login from "./components/Login";
import Dashboard from "./components/Dashboard";
import ForgotPassword from "./components/ForgotPassword";
import ResetPassword from "./components/ResetPassword";
import SessionTimeoutHandler from "./components/SessionTimeoutHandler";

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(
    !!localStorage.getItem("super_admin_token")
  );
  const [currentPath, setCurrentPath] = useState({
    pathname: globalThis.location.pathname,
    hash: globalThis.location.hash
  });

  useEffect(() => {
    const handleLocationChange = () => {
      setCurrentPath({
        pathname: globalThis.location.pathname,
        hash: globalThis.location.hash
      });
    };
    globalThis.addEventListener("hashchange", handleLocationChange);
    globalThis.addEventListener("popstate", handleLocationChange);
    return () => {
      globalThis.removeEventListener("hashchange", handleLocationChange);
      globalThis.removeEventListener("popstate", handleLocationChange);
    };
  }, []);

  const handleLoginSuccess = () => {
    setIsAuthenticated(true);
  };

  const handleLogout = useCallback(() => {
    const token = localStorage.getItem("super_admin_token");
    const API_BASE_URL = process.env.REACT_APP_API_BASE_URL;
    if (token && API_BASE_URL) {
      fetch(`${API_BASE_URL}/auth/logout`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${token}`
        }
      }).catch(err => console.error("Error during logout call:", err));
    }
    localStorage.removeItem("super_admin_token");
    localStorage.removeItem("super_admin_username");
    localStorage.removeItem("super_admin_email");
    setIsAuthenticated(false);
  }, []);

  const renderContent = () => {
    if (isAuthenticated) {
      return <Dashboard onLogout={handleLogout} />;
    }

    const isForgotPassword = currentPath.pathname === "/forgot-password" ||
      currentPath.hash === "#/forgot-password" ||
      currentPath.hash === "#/forgot-password/" ||
      currentPath.hash === "#forgot-password";
    const isResetPassword = currentPath.pathname.startsWith("/reset-password") || currentPath.hash.startsWith("#/reset-password");

    if (isForgotPassword) {
      return <ForgotPassword />;
    }

    if (isResetPassword) {
      return <ResetPassword />;
    }

    return <Login onLoginSuccess={handleLoginSuccess} />;
  };

  return (
    <div className="App">
      <SessionTimeoutHandler isAuthenticated={isAuthenticated} onLogout={handleLogout}>
        {renderContent()}
      </SessionTimeoutHandler>
    </div>
  );
}

export default App;
