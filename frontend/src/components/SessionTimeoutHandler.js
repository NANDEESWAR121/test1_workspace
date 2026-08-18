import { useEffect, useCallback, useRef } from "react";

const SessionTimeoutHandler = ({ children, isAuthenticated, onLogout }) => {
  const timeoutDuration = 15 * 60 * 1000; // 15 minutes
  const timeoutId = useRef(null);

  const logout = useCallback(() => {
    // 1. Clear session credentials
    onLogout();
    // 2. Alert the user (blocking pop-up)
    alert("You have been logged out due to 15 minutes of inactivity.");
    // 3. Redirect to the super admin login page
    globalThis.location.href = "/";
  }, [onLogout]);

  const resetTimer = useCallback(() => {
    if (timeoutId.current) clearTimeout(timeoutId.current);
    timeoutId.current = setTimeout(logout, timeoutDuration);
  }, [logout, timeoutDuration]);

  useEffect(() => {
    // Only monitor activity if the user is currently authenticated/logged in
    if (!isAuthenticated) {
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
  }, [resetTimer, isAuthenticated]);

  return children;
};

export default SessionTimeoutHandler;
