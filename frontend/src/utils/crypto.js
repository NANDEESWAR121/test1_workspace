/**
 * Utility functions to encrypt and decrypt JSON payloads on the frontend using AES-GCM.
 */
const getEncryptionKey = async (passphrase) => {
  const encoder = new TextEncoder();
  const rawKey = encoder.encode(passphrase.substring(0, 16).padEnd(16, '0'));
  return await globalThis.crypto.subtle.importKey(
    "raw",
    rawKey,
    { name: "AES-GCM" },
    false,
    ["encrypt", "decrypt"]
  );
};

export const encryptPayload = async (data) => {
  if (!data) return data;
  try {
    const passphrase = process.env.REACT_APP_ENCRYPTION_KEY || "scaloz_default_encryption_key_32bytes!";
    if (!passphrase) {
      console.warn("[Crypto] REACT_APP_ENCRYPTION_KEY environment variable is not defined.");
    }
    const key = await getEncryptionKey(passphrase);
    const iv = globalThis.crypto.getRandomValues(new Uint8Array(12));
    const encoder = new TextEncoder();
    const encodedData = encoder.encode(JSON.stringify(data));
    const ciphertextBuffer = await globalThis.crypto.subtle.encrypt(
      { name: "AES-GCM", iv: iv },
      key,
      encodedData
    );
    const combined = new Uint8Array(12 + ciphertextBuffer.byteLength);
    combined.set(iv, 0);
    combined.set(new Uint8Array(ciphertextBuffer), 12);
    let binary = "";
    const len = combined.byteLength;
    for (let i = 0; i < len; i++) {
      binary += String.fromCodePoint(combined[i]);
    }
    const encryptedBase64 = btoa(binary);
    return { payload: encryptedBase64 };
  } catch (error) {
    console.error("[Crypto] Payload Encryption failed:", error);
    return data;
  }
};

export const decryptPayload = async (data) => {
  if (!data?.payload) return data;
  try {
    const passphrase = process.env.REACT_APP_ENCRYPTION_KEY || "scaloz_default_encryption_key_32bytes!";
    if (!passphrase) {
      console.warn("[Crypto] REACT_APP_ENCRYPTION_KEY environment variable is not defined.");
    }
    const key = await getEncryptionKey(passphrase);
    
    const binaryString = atob(data.payload);
    const len = binaryString.length;
    const bytes = new Uint8Array(len);
    for (let i = 0; i < len; i++) {
      bytes[i] = binaryString.codePointAt(i);
    }
    
    if (bytes.length <= 12) {
      throw new Error("Payload is too short");
    }
    
    const iv = bytes.slice(0, 12);
    const ciphertext = bytes.slice(12);
    
    const decryptedBuffer = await globalThis.crypto.subtle.decrypt(
      { name: "AES-GCM", iv: iv },
      key,
      ciphertext
    );
    
    const decoder = new TextDecoder();
    const decryptedString = decoder.decode(decryptedBuffer);
    return JSON.parse(decryptedString);
  } catch (error) {
    console.error("[Crypto] Payload Decryption failed:", error);
    return data;
  }
};

export const encryptPassword = async (password) => {
  return password;
};
