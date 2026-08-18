import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import reportWebVitals from './reportWebVitals';
import { setupFetchInterceptor } from './utils/api';

// Install AES encrypt/decrypt interceptor on all /api fetch calls
// MUST be called before ReactDOM.createRoot so every component's fetch is intercepted
setupFetchInterceptor();

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

reportWebVitals();

// Register Service Worker for PWA support
if ('serviceWorker' in navigator) {
  // Reload page when new service worker takes control
  let refreshing = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!refreshing) {
      refreshing = true;
      globalThis.location.reload();
    }
  });

  globalThis.addEventListener('load', () => {
    navigator.serviceWorker.register('/service-worker.js')
      .then((registration) => {
        console.log('[SW] Registration successful with scope: ', registration.scope);
        // Prompt service worker update check on load
        registration.update();
      })
      .catch((error) => {
        console.error('[SW] Registration failed: ', error);
      });
  });
}
