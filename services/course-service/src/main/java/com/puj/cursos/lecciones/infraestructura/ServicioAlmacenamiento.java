package com.puj.cursos.lecciones.infraestructura;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * Servicio de infraestructura para la generación de URLs pre-firmadas de Amazon S3.
 *
 * <p>Permite a los clientes cargar y descargar archivos directamente desde/hacia S3
 * sin exponer las credenciales AWS. La región y el bucket se configuran mediante las
 * variables de entorno {@code AWS_REGION} y {@code AWS_S3_BUCKET}.
 *
 * <p>Las URLs de carga tienen validez de 15 minutos; las de descarga, de 1 hora.
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@ApplicationScoped
public class ServicioAlmacenamiento {

    /** Generador de URLs pre-firmadas de S3. */
    private S3Presigner presignador;

    /** Nombre del bucket S3 donde se almacenan los recursos del curso. */
    private String bucket;

    /**
     * Inicializa el {@link S3Presigner} con la región y el bucket obtenidos
     * de las variables de entorno al arrancar el bean.
     */
    @PostConstruct
    void inicializar() {
        String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
        this.bucket   = System.getenv().getOrDefault("AWS_S3_BUCKET", "puj-learning-resources");
        this.presignador = S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }

    /**
     * Genera una URL pre-firmada para cargar un archivo en S3.
     *
     * <p>La clave S3 se construye con el patrón:
     * {@code courses/{idCurso}/{uuid}/{nombreArchivo}}.
     * La URL tiene validez de 15 minutos.
     *
     * @param  idCurso       identificador del curso al que pertenece el recurso
     * @param  nombreArchivo nombre original del archivo a subir
     * @param  tipoContenido tipo MIME del archivo (ej. {@code application/pdf})
     * @return URL pre-firmada de carga (PUT) lista para usar desde el cliente
     */
    public String generarUrlCarga(String idCurso, String nombreArchivo, String tipoContenido) {
        String clave = "courses/" + idCurso + "/" + UUID.randomUUID() + "/" + nombreArchivo;

        PutObjectRequest solicitudPut = PutObjectRequest.builder()
                .bucket(bucket)
                .key(clave)
                .contentType(tipoContenido)
                .build();

        PutObjectPresignRequest solicitudPresign = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(solicitudPut)
                .build();

        return presignador.presignPutObject(solicitudPresign).url().toString();
    }

    /**
     * Genera una URL pre-firmada para descargar un archivo de S3.
     *
     * <p>La URL tiene validez de 1 hora.
     *
     * @param  claveS3 clave S3 completa del objeto a descargar
     * @return URL pre-firmada de descarga (GET) lista para usar desde el cliente
     */
    public String generarUrlDescarga(String claveS3) {
        GetObjectRequest solicitudGet = GetObjectRequest.builder()
                .bucket(bucket)
                .key(claveS3)
                .build();

        GetObjectPresignRequest solicitudPresign = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(solicitudGet)
                .build();

        return presignador.presignGetObject(solicitudPresign).url().toString();
    }
}
