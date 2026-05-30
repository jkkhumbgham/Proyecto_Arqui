package com.puj.collaboration.entity;

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
public class ChatMessage {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Identificador del grupo de estudio al que pertenece el mensaje. */
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    /** Identificador del usuario que envió el mensaje. */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** Email del autor en el momento del envío (desnormalizado para historial). */
    @Column(name = "author_email", nullable = false, length = 255)
    private String authorEmail;

    @NotBlank
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Instante en que el mensaje fue enviado y persistido. */
    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    /**
     * Genera el UUID y establece la marca de tiempo de envío al persistir.
     */
    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        sentAt = Instant.now();
    }

    /** @return identificador único del mensaje */
    public UUID    getId()          { return id; }

    /** @return identificador del grupo de estudio propietario */
    public UUID    getGroupId()     { return groupId; }

    /** @return identificador del usuario que envió el mensaje */
    public UUID    getAuthorId()    { return authorId; }

    /** @return email del autor en el momento del envío */
    public String  getAuthorEmail() { return authorEmail; }

    /** @return contenido textual del mensaje */
    public String  getContent()     { return content; }

    /** @return instante de envío del mensaje */
    public Instant getSentAt()      { return sentAt; }

    /**
     * Establece el grupo de estudio al que pertenece el mensaje.
     *
     * @param groupId UUID del grupo de estudio
     */
    public void setGroupId(UUID groupId)     { this.groupId = groupId; }

    /**
     * Establece el identificador del autor del mensaje.
     *
     * @param authorId UUID del usuario remitente
     */
    public void setAuthorId(UUID authorId)   { this.authorId = authorId; }

    /**
     * Establece el email del autor (desnormalizado para historial).
     *
     * @param email dirección de correo del remitente
     */
    public void setAuthorEmail(String email) { this.authorEmail = email; }

    /**
     * Establece el contenido textual del mensaje.
     *
     * @param content texto del mensaje (no debe estar en blanco)
     */
    public void setContent(String content)   { this.content = content; }
}
