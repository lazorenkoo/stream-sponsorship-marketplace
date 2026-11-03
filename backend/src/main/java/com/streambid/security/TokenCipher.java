package com.streambid.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class TokenCipher {

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(String base64Key) {
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        if (decoded.length != 32) {
            throw new IllegalArgumentException("Token encryption requires a base64-encoded 32-byte key");
        }
        key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String token, String context) {
        if (token == null || token.isBlank() || token.getBytes(StandardCharsets.UTF_8).length > 16384) {
            throw new IllegalArgumentException("Invalid token length");
        }
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, context);
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
            return "v1." + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Token encryption failed");
        }
    }

    public String decrypt(String encoded, String context) {
        if (encoded == null || !encoded.startsWith("v1.") || encoded.length() > 32768) {
            throw new IllegalArgumentException("Invalid encrypted token");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encoded.substring(3));
            if (payload.length < 29) {
                throw new IllegalArgumentException("Invalid encrypted token");
            }
            byte[] nonce = new byte[12];
            var buffer = ByteBuffer.wrap(payload);
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            return new String(cipher(Cipher.DECRYPT_MODE, nonce, context).doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid encrypted token");
        }
    }

    private Cipher cipher(int mode, byte[] nonce, String context) throws GeneralSecurityException {
        if (context == null || context.isBlank() || context.length() > 1024) {
            throw new IllegalArgumentException("Invalid token context");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
