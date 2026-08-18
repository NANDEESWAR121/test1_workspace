/* global globalThis */
/**
 * Utility to extract tenant subdomain from the current hostname.
 * Uses environment variables for configuration to avoid hardcoding.
 */
export const getTenantSubdomain = () => {
    const hostname = globalThis.location.hostname;
    const mainDomain = process.env.REACT_APP_MAIN_DOMAIN;
    const workspacePrefix = process.env.REACT_APP_WORKSPACE_PREFIX;

    // Handle localhost development
    if (hostname.endsWith(".localhost") || hostname === "localhost" || hostname === "127.0.0.1") {
        if (hostname === "localhost" || hostname === "127.0.0.1") return null;
        const parts = hostname.split('.');
        // Extract first part (e.g. <tenant>.localhost)
        return parts[0];
    }

    // Handle production base domain
    const parts = hostname.split('.');
    const domainParts = mainDomain.split('.');

    // Check if hostname ends with mainDomain
    if (hostname.endsWith(mainDomain)) {
        // Find where the main domain starts in the parts array
        const domainStartIndex = parts.length - domainParts.length;

        // If there's at least one part before the main domain
        if (domainStartIndex > 0) {
            const prefixPart = parts[domainStartIndex - 1];

            // Check if there is a workspace prefix (e.g., <tenant>.)
            if (prefixPart === workspacePrefix) {
                // If there's a part before the workspace prefix, that's the tenant
                if (domainStartIndex > 1) {
                    return parts[domainStartIndex - 2];
                }
            } else if (prefixPart !== 'admin' && prefixPart !== 'hrms' && prefixPart !== 'hrmstest' && prefixPart !== 'consoletest' && prefixPart !== 'console') {
                // If the part before main domain is not a known prefix, it might be the tenant (e.g. <tenant>.scaloz.com)
                return prefixPart;
            }
        }
    }

    return null;
};

