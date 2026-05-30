namespace Puj.Analytics.Endpoints;

/// <summary>
/// Endpoints REST relacionados con el progreso individual de estudiantes.
///
/// <para>
/// En el diseño actual, analytics-service solo mantiene agregados de
/// plataforma. El progreso individual se consulta directamente en
/// <c>assessment-service</c>. Este endpoint existe únicamente para
/// devolver una respuesta 404 informativa que guíe al cliente hacia
/// el servicio correcto.
/// </para>
/// </summary>
public static class StudentProgressEndpoints
{
    /// <summary>
    /// Registra el endpoint de progreso de estudiante en la aplicación web.
    /// El endpoint siempre retorna 404 con un mensaje que indica el servicio
    /// correcto donde consultar el progreso individual.
    /// </summary>
    /// <param name="app">La instancia de <see cref="WebApplication"/> en la que
    /// se registra el endpoint.</param>
    public static void MapStudentProgressEndpoints(this WebApplication app)
    {
        app.MapGet("/api/v1/analytics/students/{userId}/progress", (Guid userId) =>
            Results.NotFound(new {
                error   = "NOT_AVAILABLE",
                message = "El progreso individual se consulta en " +
                          "assessment-service (/api/v1/submissions/my)."
            }))
        .WithTags("Progreso de Estudiantes")
        .RequireAuthorization();
    }
}
