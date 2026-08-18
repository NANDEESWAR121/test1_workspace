/* global globalThis */
import axios from "axios";
import { encryptPayload, decryptPayload } from "./utils/crypto";


const getApiBaseUrl = () => {
  if (process.env.REACT_APP_API_BASE_URL) {
    return process.env.REACT_APP_API_BASE_URL;
  }

  const { protocol, hostname, port } = globalThis.location;
  const portStr = port ? `:${port}` : '';
  const mainDomain = process.env.REACT_APP_MAIN_DOMAIN;
  const workspacePrefix = process.env.REACT_APP_WORKSPACE_PREFIX;

  // Parse clean hostname by stripping tenant subdomain if any
  let cleanHostname = hostname;
  if (hostname.includes('localhost') || hostname === '127.0.0.1') {
    cleanHostname = 'localhost';
  } else if (hostname.endsWith(mainDomain)) {
    // If it's a tenant subdomain like tenant.apps.scaloz.com
    // cleanHostname should be the base portal domain apps.scaloz.com
    cleanHostname = `${workspacePrefix}.${mainDomain}`;
  } else {
    // Fallback logic
    const parts = hostname.split('.');
    if (parts.length > 2) {
      cleanHostname = parts.slice(-2).join('.');
    }
  }

  // On localhost without an explicit API URL env var, derive from current origin
  if (hostname.includes('localhost') && !process.env.REACT_APP_API_BASE_URL) {
    return `${protocol}//${hostname}${portStr}/api`;
  }

  return `${protocol}//${cleanHostname}${portStr}/api`;
};


const API_BASE_URL = getApiBaseUrl();

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: true,
});

// ✅ Automatically add JWT token to all requests and encrypt request bodies
api.interceptors.request.use(
  async (config) => {
    const rawToken = sessionStorage.getItem("token");
    if (rawToken) {
      // Handle cases where token might be double-quoted in sessionStorage
      const token = rawToken.startsWith('"') && rawToken.endsWith('"')
        ? rawToken.slice(1, -1)
        : rawToken;
      config.headers.Authorization = `Bearer ${token}`;
    }

    if (config.data && typeof config.data === "object" && !(config.data instanceof FormData) && !config.data.payload) {
      config.data = await encryptPayload(config.data);
    }
    return config;
  },
  (error) => {
    throw error;
  }
);

// ✅ Automatically decrypt encrypted response payloads on-the-fly
api.interceptors.response.use(
  async (response) => {
    if (response.data?.payload) {
      response.data = await decryptPayload(response.data);
    }
    return response;
  },
  async (error) => {
    if (error.response?.data?.payload) {
      error.response.data = await decryptPayload(error.response.data);
    }
    throw error;
  }
);

export default api;
