package com.connecthub.paymentservice.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class SignatureUtil {

    public static String generateSignature(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Signature generation failed");
        }
    }
    public static boolean verifySignature(String orderId, String paymentId, String expectedSignature, String secret) {
        if (orderId == null || paymentId == null || expectedSignature == null || secret == null) {
            return false;
        }
        String payload = orderId + "|" + paymentId;
        String generatedSignature = generateSignature(payload, secret);
        return expectedSignature.equals(generatedSignature);
    }

}
