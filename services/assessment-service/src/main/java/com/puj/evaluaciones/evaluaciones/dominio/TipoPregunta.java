package com.puj.evaluaciones.evaluaciones.dominio;

/**
 * Tipo de pregunta soportado por el motor de calificación automática.
 *
 * <ul>
 *   <li>{@link #SINGLE_CHOICE} — una sola opción correcta.</li>
 *   <li>{@link #MULTIPLE_CHOICE} — varias opciones correctas; el estudiante
 *       debe seleccionar exactamente el conjunto correcto para obtener puntos.</li>
 *   <li>{@link #TRUE_FALSE} — pregunta de verdadero o falso (caso especial
 *       de {@code SINGLE_CHOICE}).</li>
 *   <li>{@link #SHORT_ANSWER} — respuesta libre; requiere revisión manual y
 *       no se puntúa automáticamente.</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since  1.0
 */
public enum TipoPregunta {
    /** Pregunta de selección única: solo una opción correcta. */
    SINGLE_CHOICE,

    /** Pregunta de selección múltiple: el estudiante debe elegir todas las opciones correctas. */
    MULTIPLE_CHOICE,

    /** Pregunta de verdadero o falso. */
    TRUE_FALSE,

    /** Pregunta de respuesta corta libre; requiere calificación manual. */
    SHORT_ANSWER
}
