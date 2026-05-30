package com.puj.security.rbac;

/**
 * Roles de acceso disponibles en la plataforma.
 *
 * <p>Cada rol determina qué endpoints y operaciones están disponibles para un usuario.
 * El control de acceso se aplica en dos capas:</p>
 * <ol>
 *   <li>{@link com.puj.security.jwt.JwtFilter}: valida que el JWT sea auténtico y vigente</li>
 *   <li>{@link RbacInterceptor}: verifica que el rol del usuario esté en la lista permitida</li>
 * </ol>
 *
 * <ul>
 *   <li>{@link #STUDENT}    — accede a cursos, evaluaciones, foros y grupos de estudio</li>
 *   <li>{@link #INSTRUCTOR} — crea y administra cursos, evaluaciones y reglas adaptativas</li>
 *   <li>{@link #DIRECTOR}   — consulta tableros institucionales (solo lectura)</li>
 *   <li>{@link #ADMIN}      — gestión completa de cuentas, roles y auditoría</li>
 * </ul>
 *
 * @author Plataforma PUJ
 * @since 1.0
 * @see RequiresRole
 * @see RbacInterceptor
 */
public enum Role {
    /** Estudiante inscrito en cursos. */
    STUDENT,
    /** Docente propietario de cursos. */
    INSTRUCTOR,
    /** Directivo con acceso a métricas institucionales agregadas. */
    DIRECTOR,
    /** Administrador del sistema con acceso completo. */
    ADMIN
}
