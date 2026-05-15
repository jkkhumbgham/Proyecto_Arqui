using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;

namespace Puj.Analytics.Endpoints;

public static class StudentProgressEndpoints
{
    public static void MapStudentProgressEndpoints(this WebApplication app)
    {
        var group = app.MapGroup("/api/v1/analytics/students")
            .WithTags("Progreso de Estudiantes")
            .RequireAuthorization();

        group.MapGet("/{userId}/progress", async (Guid userId, AnalyticsDbContext db, HttpContext ctx) =>
        {
            var claimUserId = ctx.User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value
                           ?? ctx.User.FindFirst("sub")?.Value ?? "";
            var role = ctx.User.FindFirst("role")?.Value ?? "";

            if (claimUserId != userId.ToString() && role != "INSTRUCTOR" && role != "ADMIN")
                return Results.Forbid();

            var enrollments  = await db.EnrollmentRecords.Where(e => e.UserId == userId).CountAsync();
            var submissions  = await db.SubmissionRecords.Where(s => s.UserId == userId).ToListAsync();
            var passedCount  = submissions.Count(s => s.Passed);
            var avgScore     = submissions.Count > 0 ? submissions.Average(s => s.Score) : 0m;

            return Results.Ok(new {
                userId,
                totalEnrollments = enrollments,
                totalSubmissions = submissions.Count,
                passed           = passedCount,
                avgScore         = Math.Round(avgScore, 2)
            });
        })
        .WithSummary("Progreso de un estudiante")
        .WithOpenApi();
    }
}
