package com.scaloz.superadmin.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Map;

@ControllerAdvice
public class EncryptionAdvice extends RequestBodyAdviceAdapter implements ResponseBodyAdvice<Object> {

    private static final String KEY_PAYLOAD = "payload";

    @Value("${scaloz.app.encryptionKey}")
    private String encryptionKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── RequestBodyAdviceAdapter Methods ──

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * Attempts to parse the request body as a JSON map. Returns the parsed map,
     * or null if the body is not valid JSON or not a map-shaped object.
     */
    private Map<String, Object> tryParseAsMap(byte[] bodyBytes) {
        try {
            return objectMapper.readValue(bodyBytes, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            // Body is not a JSON map — treat as plain bytes and pass through unchanged
            return Map.of();
        }
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
                                         Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        try {
            String skipHeader = inputMessage.getHeaders().getFirst("SkipPayloadEncryption");
            if ("true".equalsIgnoreCase(skipHeader)) {
                return inputMessage;
            }

            InputStream body = inputMessage.getBody();
            byte[] bodyBytes = body.readAllBytes();
            if (bodyBytes.length == 0) {
                return inputMessage;
            }

            Map<String, Object> map = tryParseAsMap(bodyBytes);

            if (map.get(KEY_PAYLOAD) instanceof String encryptedBase64) {
                String decryptedJson = AesUtils.decrypt(encryptedBase64, encryptionKey);
                byte[] decryptedBytes = decryptedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return new DecryptedInputMessage(inputMessage.getHeaders(), decryptedBytes);
            }

            return new DecryptedInputMessage(inputMessage.getHeaders(), bodyBytes);
        } catch (Exception e) {
            return inputMessage;
        }
    }

    // ── ResponseBodyAdvice Methods ──

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || isAlreadyEncrypted(body)) {
            return body;
        }

        String skipHeader = request.getHeaders().getFirst("SkipPayloadEncryption");
        if ("true".equalsIgnoreCase(skipHeader)) {
            return body;
        }

        String path = request.getURI().getPath();
        if (path == null || !path.startsWith("/api/") || path.contains("manifest")) {
            return body;
        }

        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType) || body instanceof String) {
            return encryptStringBody(body);
        }

        if (isJsonContentType(selectedContentType)) {
            return encryptJsonBody(body);
        }

        return body;
    }

    private boolean isAlreadyEncrypted(Object body) {
        if (body instanceof Map<?, ?> map) {
            return map.size() == 1 && map.containsKey(KEY_PAYLOAD);
        }
        return false;
    }

    private boolean isJsonContentType(MediaType contentType) {
        return contentType != null && (contentType.includes(MediaType.APPLICATION_JSON) || contentType.toString().contains("json"));
    }

    private Object encryptStringBody(Object body) {
        try {
            String rawString = (String) body;
            if (rawString.startsWith("{\"" + KEY_PAYLOAD + "\":") || rawString.startsWith("{\"" + KEY_PAYLOAD + "\" :")) {
                return body;
            }
            String json = objectMapper.writeValueAsString(body);
            String encrypted = AesUtils.encrypt(json, encryptionKey);
            return "{\"" + KEY_PAYLOAD + "\":\"" + encrypted + "\"}";
        } catch (Exception e) {
            return body;
        }
    }

    private Object encryptJsonBody(Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            String encrypted = AesUtils.encrypt(json, encryptionKey);
            return Map.of(KEY_PAYLOAD, encrypted);
        } catch (Exception e) {
            return body;
        }
    }

    private static class DecryptedInputMessage implements HttpInputMessage {
        private final HttpHeaders headers;
        private final byte[] body;

        public DecryptedInputMessage(HttpHeaders headers, byte[] body) {
            this.headers = headers;
            this.body = body;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
