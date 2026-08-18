/* global globalThis */
import React from 'react';
import ReactDOM from 'react-dom/client';
import 'bootstrap/dist/css/bootstrap.min.css';
import './index.css';
import App from './App';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

// Unregister service worker and clear caches to prevent development caching issues
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.getRegistrations().then((registrations) => {
    for (let registration of registrations) {
      registration.unregister().then(() => {
        console.log('[SW] Unregistered active service worker');
      });
    }
  });
  caches.keys().then((names) => {
    for (let name of names) {
      caches.delete(name).then(() => {
        console.log('[Cache] Deleted cache:', name);
      });
    }
  });
}

