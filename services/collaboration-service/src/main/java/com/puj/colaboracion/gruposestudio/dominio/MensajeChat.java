package com.puj.colaboracion.gruposestudio.dominio;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensaje de chat persistido en un grupo de estudio.
 *
 * <p>Los mensajes se guardan en base de datos para proporcionar historial
 * de conversación a través del endpoint REST {@code GET /groups/{id}/messages}.
 * El canal en tiempo real opera a través del WebSocket y Redis Pub/Sub.</p>
 *
 * <p>El índice {@code idx_chat_group_ts} optimiza las consultas de historial
 * reciente ordenadas por grupo y tiempo de envío.</p>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
@Entity
@Table(name = "chat_messages", schema = "collaboration",
        indexes = @Index(
                name       = "idx_chat_group_ts",
                columnList = "group_id, sent_at DESC"))
public class MensajeChat {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Identificador del grupo de estudio al que pertenece el mensaje. */
    @Column(name = "group_id", nullable = false)
    private UUID idGrupo;

    /** Identificador del usuario que envió el mensaje. */
    @Column(name = "author_id", nullable = false)
    private UUID idAutor;

    /** Email del autor en el momento del envío (desnormalizado para historial). */
    @Column(name = "author_email", nullable = false, length = 255)
    private String emailAutor;

    @NotBlank
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    /** Instante en que el mensaje fue enviado y persistido. */
    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant enviadoEn;

    /**
     * Genera el UUID y establece la marca de tiempo de envío al persistir.
     */
    @PrePersist
    void alCrear() {
        if (id == null) id = UUID.randomUUID();
        enviadoEn = Instant.now();
    }

    /** @return identificador único del mensaje */
    public UUID    getId()           { return id; }

    /** @return identificador del grupo de estudio propietario */
    public UUID    getIdGrupo()      { return idGrupo; }

    /** @return identificador del usuario que envió el mensaje */
    public UUID    getIdAutor()      { return idAutor; }

    /** @return email del autor en el momento del envío */
    public String  getEmailAutor()   { return emailAutor; }

    /** @return contenido textual del mensaje */
    public String  getContenido()    { return contenido; }

    /** @return instante de envío del mensaje */
    public Instant getEnviadoEn()    { return enviadoEn; }

    /**
     * Establece el grupo de estudio al que pertenece el mensaje.
     *
     * @param idGrupo UUID del grupo de estudio
     */
    public void setIdGrupo(UUID idGrupo)       { this.idGrupo = idGrupo; }

    /**
     * Establece el identificador del autor del mensaje.
     *
     * @param idAutor UUID del usuario remitente
     */
    public void setIdAutor(UUID idAutor)       { this.idAutor = idAutor; }

    /**
     * Establece el email del autor (desnormalizado para historial).
     *
     * @param emailAutor dirección de correo del remitente
     */
    public void setEmailAutor(String emailAutor) { this.emailAutor = emailAutor; }

    /**
     * Establece el contenido textual del mensaje.
     *
     * @param contenido texto del mensaje (no debe estar en blanco)
     */
    public void setContenido(String contenido) { this.contenido = contenido; }
}
