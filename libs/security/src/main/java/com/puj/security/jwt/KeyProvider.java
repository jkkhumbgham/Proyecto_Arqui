package com.puj.security.jwt;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Logger;

@ApplicationScoped
public class KeyProvider {

    private static final Logger LOG = Logger.getLogger(KeyProvider.class.getName());

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    void init() {
        String privatePem = System.getenv("JWT_PRIVATE_KEY");
        String publicPem  = System.getenv("JWT_PUBLIC_KEY");

        if (privatePem != null && !privatePem.isBlank()
                && publicPem != null && !publicPem.isBlank()) {
            this.privateKey = parsePrivateKey(privatePem);
            this.publicKey  = parsePublicKey(publicPem);
            LOG.info("JWT RS256 keys loaded from environment variables.");
        } else {
            LOG.warning("JWT_PRIVATE_KEY / JWT_PUBLIC_KEY not set — generating ephemeral RSA-2048 key pair (DEV ONLY).");
            generateEphemeralKeyPair();
        }
    }

    private void generateEphemeralKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            this.privateKey = kp.getPrivate();
            this.publicKey  = kp.getPublic();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot generate RSA key pair", e);
        }
    }

    private PrivateKey parsePrivateKey(String pem) {
        try {
            byte[] decoded = Base64.getDecoder().decode(stripPemHeaders(pem));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse RSA private key", e);
        }
    }

    private PublicKey parsePublicKey(String pem) {
        try {
            byte[] decoded = Base64.getDecoder().decode(stripPemHeaders(pem));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse RSA public key", e);
        }
    }

    private String stripPemHeaders(String pem) {
        return pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
    }

    public PrivateKey getPrivateKey() { return privateKey; }
    public PublicKey  getPublicKey()  { return publicKey; }
}
