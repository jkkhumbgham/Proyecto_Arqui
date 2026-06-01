package com.puj.seguridad.jwt;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Proveedor de claves RSA-2048 para firma y verificación de JWTs (RS256).
 *
 * <p>Carga las claves desde las variables de entorno {@code JWT_PRIVATE_KEY} y
 * {@code JWT_PUBLIC_KEY} en formato PEM Base64. Si no están configuradas, genera
 * un par efímero en memoria solo para entornos de desarrollo.</p>
 *
 * <p><strong>Producción:</strong> ambas variables deben estar configuradas en los
 * secretos de Kubernetes ({@code platform-secrets}).</p>
 *
 * <p><strong>Servicios sin firma</strong> (course-service, assessment-service, etc.)
 * solo necesitan {@code JWT_PUBLIC_KEY} para verificar tokens emitidos por user-service.</p>
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see ProveedorJwt
 */
@ApplicationScoped
public class ProveedorClaves {

    private static final Logger LOG = Logger.getLogger(ProveedorClaves.class.getName());

    private PrivateKey clavePrivada;
    private PublicKey  clavePublica;

    /**
     * Inicializa las claves RSA al arrancar el bean CDI.
     *
     * <p>Prioridad de carga:</p>
     * <ol>
     *   <li>Ambas variables presentes → carga privada + pública (firma y verificación)</li>
     *   <li>Solo {@code JWT_PUBLIC_KEY} → carga solo pública (verificación)</li>
     *   <li>Ninguna variable → genera par efímero RSA-2048 (solo DEV)</li>
     * </ol>
     */
    @PostConstruct
    void init() {
        String pemPrivada = System.getenv("JWT_PRIVATE_KEY");
        String pemPublica = System.getenv("JWT_PUBLIC_KEY");

        boolean tienePublica  = pemPublica  != null && !pemPublica.isBlank();
        boolean tienePrivada  = pemPrivada  != null && !pemPrivada.isBlank();

        if (tienePublica && tienePrivada) {
            this.clavePrivada = parsearClavePrivada(pemPrivada);
            this.clavePublica = parsearClavePublica(pemPublica);
            LOG.info("JWT RS256 keys loaded from environment variables (sign + verify).");
        } else if (tienePublica) {
            this.clavePublica = parsearClavePublica(pemPublica);
            LOG.info("JWT RS256 public key loaded from environment variables (verify only).");
        } else {
            LOG.warning(
                    "JWT_PRIVATE_KEY / JWT_PUBLIC_KEY not set — "
                    + "generating ephemeral RSA-2048 key pair (DEV ONLY).");
            generarParEfimero();
        }
    }

    /**
     * Retorna la clave privada RSA para firmar tokens.
     *
     * @return clave privada RSA-2048; puede ser {@code null} en servicios de solo verificación
     */
    public PrivateKey obtenerClavePrivada() { return clavePrivada; }

    /**
     * Retorna la clave pública RSA para verificar tokens.
     *
     * @return clave pública RSA-2048; nunca {@code null} tras {@link #init()}
     */
    public PublicKey obtenerClavePublica()  { return clavePublica; }

    // ── helpers privados ──────────────────────────────────────────────────────

    /**
     * Genera un par de claves RSA-2048 en memoria para uso exclusivo en desarrollo.
     *
     * @throws IllegalStateException si el algoritmo RSA no está disponible en la JVM
     */
    private void generarParEfimero() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp      = kpg.generateKeyPair();
            this.clavePrivada = kp.getPrivate();
            this.clavePublica = kp.getPublic();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot generate RSA key pair", e);
        }
    }

    /**
     * Parsea una clave privada RSA en formato PEM (PKCS#8 Base64).
     *
     * @param pem clave privada en formato PEM; los encabezados son opcionales
     * @return clave privada RSA parseada
     * @throws IllegalStateException si el PEM es inválido o malformado
     */
    private PrivateKey parsearClavePrivada(String pem) {
        try {
            byte[] decoded = Base64.getDecoder().decode(limpiarEncabezadosPem(pem));
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse RSA private key", e);
        }
    }

    /**
     * Parsea una clave pública RSA en formato PEM (X.509 Base64).
     *
     * @param pem clave pública en formato PEM; los encabezados son opcionales
     * @return clave pública RSA parseada
     * @throws IllegalStateException si el PEM es inválido o malformado
     */
    private PublicKey parsearClavePublica(String pem) {
        try {
            byte[] decoded = Base64.getDecoder().decode(limpiarEncabezadosPem(pem));
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse RSA public key", e);
        }
    }

    /**
     * Elimina los encabezados PEM y normaliza los saltos de línea.
     *
     * <p>Docker Compose puede pasar {@code \n} como dos caracteres literales
     * (barra invertida + n); este método los convierte a saltos de línea reales
     * antes de aplicar la decodificación Base64.</p>
     *
     * @param pem cadena PEM cruda (con o sin encabezados)
     * @return string Base64 limpio, sin espacios ni encabezados
     */
    private String limpiarEncabezadosPem(String pem) {
        return pem.replace("\\n", "\n")
                  .replaceAll("-----[A-Z ]+-----", "")
                  .replaceAll("\\s", "");
    }
}
