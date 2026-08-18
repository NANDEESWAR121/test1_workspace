/* global globalThis */
import { useEffect, useCallback, useRef } from "react";
import { useNavigate, useLocation } from "react-router-dom";

const SessionTimeoutHandler = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const timeoutDuration = 15 * 60 * 1000; // 15 minutes
  const timeoutId = useRef(null);

  const logout = useCallback(() => {
    // 1. Clear session credentials
    sessionStorage.clear();
    // 2. Alert the user (blocking pop-up)
    alert("You have been logged out due to 15 minutes of inactivity.");
    // 3. Redirect to the login page
    navigate("/");
  }, [navigate]);

  const resetTimer = useCallback(() => {
    if (timeoutId.current) clearTimeout(timeoutId.current);
    timeoutId.current = setTimeout(logout, timeoutDuration);
  }, [logout, timeoutDuration]);

  useEffect(() => {
    // Only monitor activity if the token exists and we are not on public/login/reset pages
    const token = sessionStorage.getItem("token");
    const isPublicPage = ["/", "/forgot-password", "/change-password", "/reset-password"].includes(location.pathname);
    
    if (!token || isPublicPage) {
      if (timeoutId.current) clearTimeout(timeoutId.current);
      return;
    }

    const events = ["mousemove", "keydown", "click", "scroll", "touchstart"];

    events.forEach((event) => globalThis.addEventListener(event, resetTimer));

    resetTimer(); // Start/initialize the timer

    return () => {
      events.forEach((event) => globalThis.removeEventListener(event, resetTimer));
      if (timeoutId.current) clearTimeout(timeoutId.current);
    };
  }, [resetTimer, location.pathname]);

  return children;
};

export default SessionTimeoutHandler;
