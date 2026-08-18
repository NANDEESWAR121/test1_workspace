import React from "react";
import PropTypes from "prop-types";
import { BrowserRouter as Router, Routes, Route, Navigate } from "react-router-dom";
import LoginPage from "./components/LoginPage";
import Dashboard from "./components/Dashboard";
import ForgotPassword from "./components/ForgotPassword";
import ChangePassword from "./components/ChangePassword";
import ResetPassword from "./components/ResetPassword";
import SessionTimeoutHandler from "./components/SessionTimeoutHandler";

const PolicyPage = ({ title }) => (
  <div style={{ padding: "40px", fontFamily: "sans-serif", maxWidth: "800px", margin: "0 auto" }}>
    <h2>{title}</h2>
    <p>This is a placeholder page for the {title.toLowerCase()}. Please consult company HR for details.</p>
    <a href="/">Back to Login</a>
  </div>
);

PolicyPage.propTypes = {
  title: PropTypes.string.isRequired,
};

function App() {
  return (
    <Router>
      <SessionTimeoutHandler>
        <Routes>
          <Route path="/" element={<LoginPage />} />
          <Route path="/Home" element={<Dashboard />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/change-password" element={<ChangePassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route path="/policy/privacy_policy" element={<PolicyPage title="Privacy Policy" />} />
          <Route path="/policy/cookies_policy" element={<PolicyPage title="Cookies Policy" />} />
          <Route path="*" element={<Navigate to="/" />} />
        </Routes>
      </SessionTimeoutHandler>
    </Router>
  );
}

export default App;
