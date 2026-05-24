namespace Puj.Analytics.Endpoints;

public static class StudentProgressEndpoints
{
    public static void MapStudentProgressEndpoints(this WebApplication app)
    {
        // El progreso individual de estudiantes se consulta directamente en assessment-service.
        // Analytics solo mantiene agregados de plataforma; este endpoint no aplica en el nuevo diseño.
        app.MapGet("/api/v1/analytics/students/{userId}/progress", (Guid userId) =>
            Results.NotFound(new {
                error   = "NOT_AVAILABLE",
                message = "El progreso individual se consulta en assessment-service (/api/v1/submissions/my)."
            }))
        .WithTags("Progreso de Estudiantes")
        .RequireAuthorization();
    }
}
