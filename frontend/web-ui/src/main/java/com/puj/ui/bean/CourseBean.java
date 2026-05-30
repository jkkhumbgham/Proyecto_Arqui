package com.puj.ui.bean;

import com.fasterxml.jackson.databind.JsonNode;
import com.puj.ui.service.ApiClientService;
import com.puj.ui.util.FacesMessageUtil;
import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Bean JSF de vista que gestiona el catálogo de cursos, el detalle de un curso
 * y las operaciones de inscripción y creación.
 *
 * <p>Es {@code @ViewScoped}: se mantiene activo mientras el usuario permanece
 * en la misma vista. Es utilizado principalmente por las páginas:
 * <ul>
 *   <li>{@code courses.xhtml} — listado de cursos disponibles o del instructor</li>
 *   <li>{@code course-detail.xhtml} — detalle con módulos, lecciones y progreso</li>
 * </ul>
 *
 * <p>Al inicializarse con {@code @PostConstruct} detecta si hay un parámetro
 * {@code courseId} en la URL para cargar el detalle de un curso específico, o
 * bien carga el catálogo completo según el rol del usuario (instructor vs.
 * estudiante vs. público).
 *
 * @author Plataforma PUJ
 * @since 1.0
 */
@Named
@ViewScoped
public class CourseBean implements Serializable {

    private static final long   serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(CourseBean.class.getName());

    private static final String COURSE_URL =
            System.getenv().getOrDefault("COURSE_SERVICE_URL", "http://course-service:8080");
    private static final String ASSESSMENT_URL =
            System.getenv().getOrDefault("ASSESSMENT_SERVICE_URL", "http://assessment-service:8080");

    @Inject private SessionBean      session;
    @Inject private ApiClientService api;

    // -----------------------------------------------------------------------
    // Inner static classes (datos tipados)
    // -----------------------------------------------------------------------

    /**
     * DTO inmutable que representa los datos básicos de un curso en el listado.
     */
    public static class CourseData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id, title, description, status;
        private final int maxStudents;

        /**
         * Construye un registro de datos de curso.
         *
         * @param id          identificador único del curso
         * @param title       título del curso
         * @param description descripción corta del curso
         * @param status      estado del curso (p. ej. DRAFT, PUBLISHED)
         * @param maxStudents cupo máximo de estudiantes
         */
        public CourseData(String id, String title, String description,
                          String status, int maxStudents) {
            this.id = id; this.title = title; this.description = description;
            this.status = status; this.maxStudents = maxStudents;
        }

        /** Retorna el identificador del curso. */
        public String getId()          { return id; }
        /** Retorna el título del curso. */
        public String getTitle()       { return title; }
        /** Retorna la descripción del curso. */
        public String getDescription() { return description; }
        /** Retorna el estado del curso (DRAFT, PUBLISHED, etc.). */
        public String getStatus()      { return status; }
        /** Retorna el número máximo de estudiantes permitidos. */
        public int    getMaxStudents() { return maxStudents; }
    }

    /**
     * DTO inmutable que representa una lección dentro de un módulo.
     *
     * <p>Incluye opcionalmente la referencia a la evaluación asociada a la lección
     * y si es una lección suplementaria desbloqueada por la regla adaptativa.
     */
    public static class LessonData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String  id, title, content, assessmentId, assessmentTitle;
        private final int     orderIndex;
        private final Integer durationMinutes;
        private final boolean supplementary;

        /**
         * Construye un registro de datos de lección.
         *
         * @param id               identificador único de la lección
         * @param title            título de la lección
         * @param content          contenido textual principal
         * @param orderIndex       posición dentro del módulo
         * @param durationMinutes  duración estimada en minutos, puede ser {@code null}
         * @param assessmentId     ID de la evaluación asociada, puede ser {@code null}
         * @param assessmentTitle  título de la evaluación asociada, puede ser {@code null}
         * @param supplementary    {@code true} si es una lección suplementaria adaptativa
         */
        public LessonData(String id, String title, String content, int orderIndex,
                          Integer durationMinutes, String assessmentId,
                          String assessmentTitle, boolean supplementary) {
            this.id = id; this.title = title; this.content = content;
            this.orderIndex = orderIndex; this.durationMinutes = durationMinutes;
            this.assessmentId = assessmentId; this.assessmentTitle = assessmentTitle;
            this.supplementary = supplementary;
        }

        /** Retorna el identificador de la lección. */
        public String  getId()               { return id; }
        /** Retorna el título de la lección. */
        public String  getTitle()            { return title; }
        /** Retorna el contenido textual de la lección. */
        public String  getContent()          { return content; }
        /** Retorna el orden de la lección dentro de su módulo. */
        public int     getOrderIndex()       { return orderIndex; }
        /** Retorna la duración estimada en minutos o {@code null} si no se especificó. */
        public Integer getDurationMinutes()  { return durationMinutes; }
        /** Retorna el ID de la evaluación asociada a la lección, o {@code null}. */
        public String  getAssessmentId()     { return assessmentId; }
        /** Retorna el título de la evaluación asociada, o {@code null}. */
        public String  getAssessmentTitle()  { return assessmentTitle; }
        /** Indica si la lección es de tipo suplementaria (contenido adaptativo). */
        public boolean isSupplementary()     { return supplementary; }
    }

    /**
     * DTO inmutable que representa una evaluación asociada a un curso o lección.
     */
    public static class AssessmentData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String id, title, lessonId;
        private final double passingScorePct;

        /**
         * Construye un registro de datos de evaluación.
         *
         * @param id              identificador único de la evaluación
         * @param title           título de la evaluación
         * @param lessonId        ID de la lección a la que pertenece, o {@code null}
         *                        si es evaluación de curso completo
         * @param passingScorePct porcentaje mínimo para aprobar
         */
        public AssessmentData(String id, String title,
                              String lessonId, double passingScorePct) {
            this.id = id; this.title = title;
            this.lessonId = lessonId; this.passingScorePct = passingScorePct;
        }

        /** Retorna el identificador de la evaluación. */
        public String  getId()              { return id; }
        /** Retorna el título de la evaluación. */
        public String  getTitle()           { return title; }
        /** Retorna el ID de la lección asociada o {@code null} si es de nivel curso. */
        public String  getLessonId()        { return lessonId; }
        /** Retorna el porcentaje mínimo requerido para aprobar. */
        public double  getPassingScorePct() { return passingScorePct; }

        /**
         * Indica si la evaluación es de nivel curso (no está ligada a ninguna lección).
         *
         * @return {@code true} si {@code lessonId} es nulo o vacío
         */
        public boolean isCourseLevelOnly()  {
            return lessonId == null || lessonId.isBlank();
        }
    }

    /**
     * DTO inmutable que representa un módulo del curso con sus lecciones anidadas.
     */
    public static class ModuleData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String          id, title, description;
        private final int             orderIndex;
        private final List<LessonData> lessons;

        /**
         * Construye un registro de datos de módulo.
         *
         * @param id          identificador único del módulo
         * @param title       título del módulo
         * @param description descripción del módulo
         * @param orderIndex  posición dentro del curso
         * @param lessons     lista de lecciones que contiene el módulo
         */
        public ModuleData(String id, String title, String description,
                          int orderIndex, List<LessonData> lessons) {
            this.id = id; this.title = title; this.description = description;
            this.orderIndex = orderIndex; this.lessons = lessons;
        }

        /** Retorna el identificador del módulo. */
        public String           getId()          { return id; }
        /** Retorna el título del módulo. */
        public String           getTitle()       { return title; }
        /** Retorna la descripción del módulo. */
        public String           getDescription() { return description; }
        /** Retorna el orden del módulo dentro del curso. */
        public int              getOrderIndex()  { return orderIndex; }
        /** Retorna la lista de lecciones del módulo. */
        public List<LessonData> getLessons()     { return lessons; }
    }

    // -----------------------------------------------------------------------
    // Estado del bean
    // -----------------------------------------------------------------------

    private List<CourseData>     courses           = new ArrayList<>();
    private CourseData           courseDetail;
    private List<ModuleData>     modules           = new ArrayList<>();
    private List<AssessmentData> courseAssessments = new ArrayList<>();
    private Set<String>          enrolledCourseIds = new HashSet<>();
    private Set<String>          lockedModuleIds   = new HashSet<>();
    private Set<String>          unlockedSupplementaryLessonIds = new HashSet<>();
    private Set<String>          completedLessonIdsSet          = new HashSet<>();

    private String  selectedCourseId;
    private double  progressPct;
    private int     completedLessons;
    private int     totalLessons;
    private String  firstUncompletedLessonId;
    private boolean courseFinishable = false;

    private String newTitle;
    private String newDescription;
    private int    newMaxStudents = 30;
    private String newStatus      = "DRAFT";

    // -----------------------------------------------------------------------
    // Inicialización
    // -----------------------------------------------------------------------

    /**
     * Inicializa el bean detectando el modo de operación a partir de los
     * parámetros de la URL.
     *
     * <p>Si existe el parámetro {@code courseId} carga el detalle del curso,
     * el progreso del estudiante y calcula bloqueos de módulos; de lo contrario
     * carga el catálogo de cursos según el rol del usuario.
     */
    @PostConstruct
    public void load() {
        if (!session.isAuthenticated()) return;

        String courseIdParam = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap().get("courseId");
        LOG.fine("[CourseBean] @PostConstruct courseIdParam=" + courseIdParam);
        if (courseIdParam != null && !courseIdParam.isBlank()) {
            selectedCourseId = courseIdParam;
            if (session.hasRole("STUDENT")) {
                loadEnrolledIds();
                loadStudentProgress(courseIdParam);
            }
            loadDetail();
            if (session.hasRole("STUDENT")) {
                resolveFirstUncompletedLesson();
                computeModuleLocks();
                loadUnlockedSupplementaryLessons(courseIdParam);
                computeCourseFinishable();
            }
            return;
        }

        if (session.hasRole("INSTRUCTOR", "ADMIN", "DIRECTOR")) {
            loadMyCourses();
        } else {
            loadPublicCourses();
        }

        if (session.hasRole("STUDENT")) {
            loadEnrolledIds();
        }
    }

    /**
     * Carga el catálogo público de cursos sin autenticación.
     */
    private void loadPublicCourses() {
        try {
            populateCourses(api.getPublic(COURSE_URL + "/api/v1/courses"));
        } catch (Exception e) {
            FacesMessageUtil.warn("No se pudo cargar el catálogo de cursos.");
        }
    }

    /**
     * Carga los cursos creados por el instructor o administrador autenticado.
     */
    private void loadMyCourses() {
        try {
            populateCourses(
                    api.get(COURSE_URL + "/api/v1/courses/my", session.getAccessToken()));
        } catch (Exception e) {
            FacesMessageUtil.warn("No se pudo cargar tus cursos.");
        }
    }

    /**
     * Parsea la respuesta HTTP y popula la lista interna {@code courses}.
     *
     * <p>Acepta tanto una respuesta cuya raíz sea un array JSON como una con
     * campo {@code data} que contenga el array.
     *
     * @param resp respuesta HTTP del servicio de cursos
     * @throws Exception si el JSON no puede parsearse
     */
    private void populateCourses(HttpResponse<String> resp) throws Exception {
        if (resp.statusCode() == 200) {
            JsonNode root = api.readTree(resp.body());
            JsonNode arr  = root.isArray() ? root : root.path("data");
            arr.forEach(n -> courses.add(new CourseData(
                    n.path("id").asText(),
                    n.path("title").asText(),
                    n.path("description").asText(""),
                    n.path("status").asText(),
                    n.path("maxStudents").asInt()
            )));
        }
    }

    /**
     * Carga el progreso del estudiante autenticado para el curso indicado.
     *
     * <p>Popula {@code completedLessons}, {@code totalLessons}, {@code progressPct}
     * y {@code completedLessonIdsSet}.
     *
     * @param courseId identificador del curso
     */
    private void loadStudentProgress(String courseId) {
        try {
            HttpResponse<String> resp = api.get(
                    COURSE_URL + "/api/v1/courses/" + courseId + "/progress",
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                JsonNode n = api.readTree(resp.body());
                completedLessons = (int) n.path("completedCount").asLong();
                totalLessons     = (int) n.path("totalLessons").asLong();
                progressPct      = n.path("progressPct").asDouble();
                n.path("completedLessonIds")
                        .forEach(id -> completedLessonIdsSet.add(id.asText()));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Determina la primera lección no completada del curso para guiar al estudiante.
     *
     * <p>Recorre los módulos en orden y las lecciones ignorando las suplementarias
     * hasta encontrar la primera no presente en {@code completedLessonIdsSet}.
     */
    private void resolveFirstUncompletedLesson() {
        outer:
        for (ModuleData m : modules) {
            for (LessonData l : m.getLessons()) {
                if (l.isSupplementary()) continue;
                if (!completedLessonIdsSet.contains(l.getId())) {
                    firstUncompletedLessonId = l.getId();
                    break outer;
                }
            }
        }
    }

    /**
     * Calcula qué módulos están bloqueados según la puntuación promedio del
     * estudiante en las evaluaciones del módulo anterior.
     *
     * <p>Un módulo se bloquea si el promedio de puntajes en las evaluaciones del
     * módulo previo es inferior al 60 %. Solo aplica cuando hay más de un módulo.
     */
    private void computeModuleLocks() {
        if (modules.size() <= 1 || session.getAccessToken() == null) return;
        String userId = session.getUserId();
        if (userId == null || userId.isBlank()) return;

        Map<String, List<String>> moduleAssessments = new HashMap<>();
        for (ModuleData mod : modules) {
            Set<String> moduleLessonIds = new HashSet<>();
            mod.getLessons().forEach(l -> moduleLessonIds.add(l.getId()));
            List<String> aids = new ArrayList<>();
            courseAssessments.forEach(a -> {
                if (a.getLessonId() != null && moduleLessonIds.contains(a.getLessonId())) {
                    aids.add(a.getId());
                }
            });
            moduleAssessments.put(mod.getId(), aids);
        }

        for (int i = 1; i < modules.size(); i++) {
            ModuleData   prev     = modules.get(i - 1);
            ModuleData   curr     = modules.get(i);
            List<String> prevAids = moduleAssessments.get(prev.getId());
            if (prevAids == null || prevAids.isEmpty()) continue;

            String idsParam = String.join(",", prevAids);
            try {
                String url = ASSESSMENT_URL + "/api/v1/submissions/avg-for-assessments"
                        + "?userId=" + userId + "&assessmentIds=" + idsParam;
                HttpResponse<String> resp = api.get(url, session.getAccessToken());
                if (resp.statusCode() == 200) {
                    double avg = api.readTree(resp.body()).path("avgScorePct").asDouble(0);
                    if (avg < 60.0) lockedModuleIds.add(curr.getId());
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Indica si el módulo especificado está bloqueado para el estudiante.
     *
     * @param moduleId identificador del módulo
     * @return {@code true} si el módulo está bloqueado por no superar las
     *         evaluaciones del módulo anterior
     */
    public boolean isModuleLocked(String moduleId) {
        return lockedModuleIds.contains(moduleId);
    }

    /**
     * Indica si el estudiante puede acceder a la lección especificada.
     *
     * <p>Una lección es accesible si ya fue completada, es la primera no
     * completada, o es una lección suplementaria desbloqueada por la regla
     * adaptativa. Para roles distintos de STUDENT siempre retorna {@code true}.
     *
     * @param lessonId identificador de la lección
     * @return {@code true} si el usuario puede ver la lección
     */
    public boolean isLessonAccessible(String lessonId) {
        if (!session.hasRole("STUDENT")) return true;
        return completedLessonIdsSet.contains(lessonId)
               || lessonId.equals(firstUncompletedLessonId)
               || unlockedSupplementaryLessonIds.contains(lessonId);
    }

    /**
     * Determina si el estudiante puede finalizar el curso.
     *
     * <p>Para que el curso sea finalizable el progreso debe ser del 100 % y el
     * promedio en todas las evaluaciones del curso debe ser igual o superior al 60 %.
     */
    private void computeCourseFinishable() {
        if (progressPct < 100.0) return;
        String userId = session.getUserId();
        if (userId == null || userId.isBlank()) return;
        if (courseAssessments.isEmpty()) { courseFinishable = true; return; }
        courseFinishable = true;
        for (AssessmentData a : courseAssessments) {
            try {
                String url = ASSESSMENT_URL + "/api/v1/submissions/avg-for-assessments"
                        + "?userId=" + userId + "&assessmentIds=" + a.getId();
                HttpResponse<String> resp = api.get(url, session.getAccessToken());
                if (resp.statusCode() != 200) { courseFinishable = false; return; }
                double avg = api.readTree(resp.body()).path("avgScorePct").asDouble(0);
                if (avg < 60.0) { courseFinishable = false; return; }
            } catch (Exception ignored) { courseFinishable = false; return; }
        }
    }

    /**
     * Indica si el estudiante cumple los requisitos para finalizar el curso.
     *
     * @return {@code true} si el progreso es del 100 % y todas las evaluaciones
     *         han sido aprobadas
     */
    public boolean isCourseFinishable() { return courseFinishable; }

    /**
     * Acción JSF para marcar el curso como completado por el estudiante.
     *
     * <p>Invoca el endpoint {@code POST /api/v1/enrollments/courses/{id}/finalize}.
     *
     * @return ruta {@code dashboard?faces-redirect=true} si la finalización fue
     *         exitosa, {@code null} en caso de error
     */
    public String finalizeCourse() {
        try {
            HttpResponse<String> resp = api.postEmpty(
                    COURSE_URL + "/api/v1/enrollments/courses/" + selectedCourseId + "/finalize",
                    session.getAccessToken());
            if (resp.statusCode() == 200) return "dashboard?faces-redirect=true";
            FacesMessageUtil.warn("No se pudo finalizar el curso.");
        } catch (Exception e) {
            FacesMessageUtil.warn("Error al finalizar el curso.");
        }
        return null;
    }

    /**
     * Carga el conjunto de IDs de cursos en los que el estudiante está inscrito.
     */
    private void loadEnrolledIds() {
        try {
            HttpResponse<String> resp = api.get(
                    COURSE_URL + "/api/v1/enrollments/my", session.getAccessToken());
            if (resp.statusCode() == 200) {
                api.readTree(resp.body())
                        .forEach(n -> enrolledCourseIds.add(n.path("courseId").asText()));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Carga el detalle completo del curso seleccionado: módulos, lecciones y
     * evaluaciones asociadas.
     */
    public void loadDetail() {
        if (selectedCourseId == null || selectedCourseId.isBlank()) return;
        try {
            HttpResponse<String> resp = api.getWithOptionalAuth(
                    COURSE_URL + "/api/v1/courses/" + selectedCourseId,
                    session.getAccessToken());
            if (resp.statusCode() != 200) return;

            JsonNode n = api.readTree(resp.body());
            courseDetail = new CourseData(
                    n.path("id").asText(),
                    n.path("title").asText(),
                    n.path("description").asText(""),
                    n.path("status").asText(),
                    n.path("maxStudents").asInt()
            );

            Map<String, AssessmentData> lessonToAssessment =
                    loadAssessmentMapForCourse(selectedCourseId);

            modules.clear();
            n.path("modules").forEach(m -> {
                List<LessonData> lessons = new ArrayList<>();
                m.path("lessons").forEach(l -> {
                    String         lessonId = l.path("id").asText();
                    AssessmentData ad       = lessonToAssessment.get(lessonId);
                    lessons.add(new LessonData(
                            lessonId,
                            l.path("title").asText(),
                            l.path("content").asText(""),
                            l.path("orderIndex").asInt(),
                            l.path("durationMinutes").isNull()
                                    || l.path("durationMinutes").isMissingNode()
                                    ? null : l.path("durationMinutes").asInt(),
                            ad != null ? ad.getId()    : null,
                            ad != null ? ad.getTitle() : null,
                            l.path("supplementary").asBoolean(false)
                    ));
                });
                modules.add(new ModuleData(
                        m.path("id").asText(),
                        m.path("title").asText(),
                        m.path("description").asText(""),
                        m.path("orderIndex").asInt(),
                        lessons
                ));
            });
        } catch (Exception e) {
            FacesMessageUtil.warn("No se pudo cargar el detalle del curso.");
        }
    }

    /**
     * Carga las evaluaciones del curso y construye un mapa de lessonId a
     * {@link AssessmentData} para su uso en {@link #loadDetail()}.
     *
     * <p>También popula la lista interna {@code courseAssessments}.
     *
     * @param courseId identificador del curso
     * @return mapa de {@code lessonId} a {@link AssessmentData}; las evaluaciones
     *         de nivel curso (sin lessonId) no aparecen en el mapa
     */
    private Map<String, AssessmentData> loadAssessmentMapForCourse(String courseId) {
        Map<String, AssessmentData> map = new HashMap<>();
        courseAssessments.clear();
        if (session.getAccessToken() == null) return map;
        try {
            HttpResponse<String> resp = api.get(
                    ASSESSMENT_URL + "/api/v1/assessments/course/" + courseId,
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                api.readTree(resp.body()).forEach(a -> {
                    String lessonId = a.path("lessonId").isMissingNode()
                            || a.path("lessonId").isNull()
                            ? null : a.path("lessonId").asText(null);
                    String aId   = a.path("id").asText();
                    String title = a.path("title").asText("");
                    double pct   = a.path("passingScorePct").asDouble(60.0);
                    if (!aId.isBlank()) {
                        AssessmentData ad = new AssessmentData(aId, title, lessonId, pct);
                        courseAssessments.add(ad);
                        if (lessonId != null && !lessonId.isBlank()) {
                            map.put(lessonId, ad);
                        }
                    }
                });
            }
        } catch (Exception ignored) {}
        return map;
    }

    /**
     * Acción JSF para inscribir al estudiante en el curso seleccionado.
     *
     * <p>Invoca {@code POST /api/v1/enrollments/courses/{id}}.
     *
     * @return ruta {@code dashboard?faces-redirect=true} si la inscripción fue
     *         exitosa, {@code null} si hubo error
     */
    public String enroll() {
        try {
            HttpResponse<String> resp = api.postEmpty(
                    COURSE_URL + "/api/v1/enrollments/courses/" + selectedCourseId,
                    session.getAccessToken());
            if (resp.statusCode() == 201) return "dashboard?faces-redirect=true";
            JsonNode err = api.readTree(resp.body());
            FacesMessageUtil.warn(err.path("message").asText("No se pudo inscribir."));
        } catch (Exception e) {
            FacesMessageUtil.warn("Error al inscribirse.");
        }
        loadEnrolledIds();
        return null;
    }

    /**
     * Acción JSF para crear un nuevo curso con los datos del formulario.
     *
     * <p>Utiliza los campos {@code newTitle}, {@code newDescription},
     * {@code newMaxStudents} y {@code newStatus}.
     *
     * @return ruta {@code courses?faces-redirect=true} si el curso fue creado,
     *         {@code null} si hubo error de validación o de API
     */
    public String createCourse() {
        try {
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("title",       newTitle       != null ? newTitle       : "");
            bodyMap.put("description", newDescription != null ? newDescription : "");
            bodyMap.put("maxStudents", newMaxStudents);
            bodyMap.put("status",      newStatus      != null ? newStatus      : "DRAFT");
            HttpResponse<String> resp = api.post(
                    COURSE_URL + "/api/v1/courses",
                    api.toJson(bodyMap), session.getAccessToken());
            if (resp.statusCode() == 201) {
                FacesMessageUtil.info("Curso creado exitosamente.");
                return "courses?faces-redirect=true";
            }
            JsonNode err = api.readTree(resp.body());
            FacesMessageUtil.warn(err.path("message").asText("No se pudo crear el curso."));
        } catch (Exception e) {
            FacesMessageUtil.warn("Error al crear el curso.");
        }
        return null;
    }

    /**
     * Indica si el estudiante ya está inscrito en el curso indicado.
     *
     * @param courseId identificador del curso a verificar
     * @return {@code true} si el curso está en el conjunto de inscripciones activas
     */
    public boolean isEnrolled(String courseId) {
        return enrolledCourseIds.contains(courseId);
    }

    /**
     * Carga los IDs de lecciones suplementarias desbloqueadas por la regla
     * adaptativa para el estudiante en el curso dado.
     *
     * @param courseId identificador del curso
     */
    private void loadUnlockedSupplementaryLessons(String courseId) {
        try {
            HttpResponse<String> resp = api.get(
                    ASSESSMENT_URL + "/api/v1/adaptive-rules/unlocked-supplementary"
                            + "?courseId=" + courseId,
                    session.getAccessToken());
            if (resp.statusCode() == 200) {
                api.readTree(resp.body())
                        .forEach(id -> unlockedSupplementaryLessonIds.add(id.asText()));
            }
        } catch (Exception e) {
            LOG.warning("[CourseBean] loadUnlockedSupplementaryLessons error: "
                    + e.getMessage());
        }
    }

    /**
     * Indica si la lección suplementaria indicada está desbloqueada para el
     * estudiante por la regla adaptativa.
     *
     * @param lessonId identificador de la lección suplementaria
     * @return {@code true} si la lección fue desbloqueada
     */
    public boolean isSupplementaryUnlocked(String lessonId) {
        return unlockedSupplementaryLessonIds.contains(lessonId);
    }

    // -----------------------------------------------------------------------
    // Getters y setters
    // -----------------------------------------------------------------------

    /** Retorna el progreso del estudiante redondeado a un decimal. */
    public double  getProgressPct() {
        return Math.round(progressPct * 10.0) / 10.0;
    }

    /** Retorna el número de lecciones completadas por el estudiante. */
    public int     getCompletedLessons()         { return completedLessons; }

    /** Retorna el total de lecciones del curso. */
    public int     getTotalLessons()             { return totalLessons; }

    /** Retorna el ID de la primera lección no completada por el estudiante. */
    public String  getFirstUncompletedLessonId() { return firstUncompletedLessonId; }

    /** Retorna la lista de cursos cargados en el catálogo. */
    public List<CourseData>     getCourses()           { return courses; }

    /** Retorna el detalle del curso seleccionado. */
    public CourseData           getCourseDetail()      { return courseDetail; }

    /** Retorna los módulos del curso seleccionado. */
    public List<ModuleData>     getModules()           { return modules; }

    /** Retorna las evaluaciones del curso seleccionado. */
    public List<AssessmentData> getCourseAssessments() { return courseAssessments; }

    /** Retorna el conjunto de IDs de cursos en los que está inscrito el estudiante. */
    public Set<String>          getEnrolledCourseIds() { return enrolledCourseIds; }

    /** Retorna el ID del curso actualmente seleccionado. */
    public String               getSelectedCourseId()  { return selectedCourseId; }

    /** Establece el ID del curso a seleccionar. */
    public void             setSelectedCourseId(String id) { this.selectedCourseId = id; }

    /** Retorna el título del nuevo curso en el formulario de creación. */
    public String           getNewTitle()          { return newTitle; }
    /** Establece el título del nuevo curso en el formulario de creación. */
    public void             setNewTitle(String t)  { this.newTitle = t; }

    /** Retorna la descripción del nuevo curso en el formulario de creación. */
    public String           getNewDescription()    { return newDescription; }
    /** Establece la descripción del nuevo curso en el formulario de creación. */
    public void             setNewDescription(String d) { this.newDescription = d; }

    /** Retorna el cupo máximo del nuevo curso en el formulario de creación. */
    public int              getNewMaxStudents()    { return newMaxStudents; }
    /** Establece el cupo máximo del nuevo curso en el formulario de creación. */
    public void             setNewMaxStudents(int n) { this.newMaxStudents = n; }

    /** Retorna el estado del nuevo curso en el formulario de creación. */
    public String           getNewStatus()         { return newStatus; }
    /** Establece el estado del nuevo curso en el formulario de creación. */
    public void             setNewStatus(String s) { this.newStatus = s; }
}
