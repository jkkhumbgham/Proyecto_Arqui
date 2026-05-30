package com.puj.ui.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.http.Part;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Servicio singleton para subir archivos a MinIO usando el protocolo AWS Signature V4.
 *
 * <p>Lee la configuración del bucket y las credenciales desde variables de entorno:
 * {@code MINIO_ENDPOINT}, {@code MINIO_ACCESS_KEY}, {@code MINIO_SECRET_KEY},
 * {@code MINIO_BUCKET} y {@code MINIO_PUBLIC_URL}. Si no están definidas se
 * usan los valores predeterminados apuntando a {@code http://minio:9000}.
 *
 * <p>Es invocado por {@link LessonContentBean} cuando el instructor adjunta
 * un archivo a un contenido de lección.
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@ApplicationScoped
public class MinioUploadService {

    private static final String ENDPOINT   =
            System.getenv().getOrDefault("MINIO_ENDPOINT",   "http://minio:9000");
    private static final String ACCESS_KEY =
            System.getenv().getOrDefault("MINIO_ACCESS_KEY", "puj_minio");
    private static final String SECRET_KEY =
            System.getenv().getOrDefault("MINIO_SECRET_KEY", "puj_minio_secret");
    private static final String BUCKET     =
            System.getenv().getOrDefault("MINIO_BUCKET",     "course-files");
    private static final String PUBLIC_URL =
            System.getenv().getOrDefault("MINIO_PUBLIC_URL", "http://localhost:9000");
    private static final String REGION     = "us-east-1";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Sube un archivo multipart a MinIO y retorna la URL pública del objeto almacenado.
     *
     * <p>El nombre del objeto en el bucket se genera aleatoriamente con {@link UUID}
     * conservando la extensión original del archivo. La petición se firma con
     * AWS Signature V4 usando PUT.
     *
     * @param file parte del formulario multipart que contiene el archivo a subir
     * @return URL pública del objeto subido (formato {@code PUBLIC_URL/BUCKET/objectName})
     * @throws Exception si la firma, la conexión o el servidor devuelve error
     */
    public String upload(Part file) throws Exception {
        String submittedName = file.getSubmittedFileName();
        String originalName  = submittedName != null ? submittedName : "file";
        int    dotIdx        = originalName.lastIndexOf('.');
        String ext           = dotIdx >= 0 ? originalName.substring(dotIdx) : "";
        String objectName    = UUID.randomUUID() + ext;
        String contentType   =
                file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        byte[] body;
        try (InputStream is = file.getInputStream()) {
            body = is.readAllBytes();
        }

        ZonedDateTime now      = ZonedDateTime.now(ZoneOffset.UTC);
        String        dateTime = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String        date     = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        URI    endpointUri = URI.create(ENDPOINT);
        String host        = endpointUri.getHost()
                + (endpointUri.getPort() > 0 ? ":" + endpointUri.getPort() : "");
        String path        = "/" + BUCKET + "/" + objectName;
        String bodyHash    = sha256Hex(body);

        // Canonical request (encabezados ordenados alfabéticamente)
        String canonicalHeaders = "content-type:" + contentType + "\n"
                + "host:" + host + "\n"
                + "x-amz-content-sha256:" + bodyHash + "\n"
                + "x-amz-date:" + dateTime + "\n";
        String signedHeaders    = "content-type;host;x-amz-content-sha256;x-amz-date";

        String canonicalRequest = "PUT\n" + path + "\n\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + bodyHash;

        String credentialScope = date + "/" + REGION + "/s3/aws4_request";
        String stringToSign    = "AWS4-HMAC-SHA256\n" + dateTime + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        byte[] signingKey  = buildSigningKey(SECRET_KEY, date, REGION, "s3");
        String signature   = hmacHex(signingKey, stringToSign);
        String authHeader  = "AWS4-HMAC-SHA256 Credential=" + ACCESS_KEY + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT + path))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .header("Content-Type", contentType)
                .header("x-amz-content-sha256", bodyHash)
                .header("x-amz-date", dateTime)
                .header("Authorization", authHeader)
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException(
                    "MinIO upload failed [" + resp.statusCode() + "]: " + resp.body());
        }

        return PUBLIC_URL + "/" + BUCKET + "/" + objectName;
    }

    /**
     * Comprueba si el servicio MinIO está disponible realizando un ping al
     * endpoint de salud {@code /minio/health/live}.
     *
     * @return {@code true} si MinIO responde con HTTP 200 en menos de 3 segundos
     */
    public boolean isAvailable() {
        if (ACCESS_KEY == null || ACCESS_KEY.isBlank()
                || SECRET_KEY == null || SECRET_KEY.isBlank()) {
            return false;
        }
        try {
            HttpRequest ping = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT + "/minio/health/live"))
                    .GET().timeout(Duration.ofSeconds(3)).build();
            return http.send(ping, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Helpers de firma AWS Signature V4 ────────────────────────────────────

    /**
     * Calcula el hash SHA-256 de un array de bytes y lo retorna como cadena hexadecimal.
     *
     * @param data datos de entrada
     * @return representación hexadecimal en minúsculas del hash SHA-256
     * @throws Exception si el algoritmo SHA-256 no está disponible
     */
    private static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(data));
    }

    /**
     * Calcula HMAC-SHA256 sobre {@code data} usando la clave {@code key}.
     *
     * @param key  clave secreta en bytes
     * @param data datos a firmar
     * @return resultado de HMAC-SHA256 en bytes
     * @throws Exception si el algoritmo HmacSHA256 no está disponible
     */
    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Calcula HMAC-SHA256 y retorna el resultado como cadena hexadecimal.
     *
     * @param key  clave secreta en bytes
     * @param data datos a firmar
     * @return representación hexadecimal en minúsculas del HMAC-SHA256
     * @throws Exception si el algoritmo no está disponible
     */
    private static String hmacHex(byte[] key, String data) throws Exception {
        return HexFormat.of().formatHex(hmacSha256(key, data));
    }

    /**
     * Deriva la clave de firma AWS Signature V4 encadenando cuatro rondas de HMAC-SHA256.
     *
     * @param secret  clave secreta AWS (valor de {@code MINIO_SECRET_KEY})
     * @param date    fecha en formato {@code yyyyMMdd}
     * @param region  región AWS (p. ej. {@code us-east-1})
     * @param service nombre del servicio AWS (p. ej. {@code s3})
     * @return clave de firma derivada lista para usarse con {@link #hmacHex}
     * @throws Exception si algún paso de HMAC falla
     */
    private static byte[] buildSigningKey(String secret, String date,
                                          String region, String service) throws Exception {
        byte[] kSecret  = ("AWS4" + secret).getBytes(StandardCharsets.UTF_8);
        byte[] kDate    = hmacSha256(kSecret, date);
        byte[] kRegion  = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }
}
