using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;

namespace Puj.Analytics.Endpoints;

public static class CertificateEndpoints
{
    public static void MapCertificateEndpoints(this WebApplication app)
    {
        app.MapGet("/api/v1/certificates/{studentId}/{courseId}",
            async (Guid studentId, Guid courseId, AnalyticsDbContext db) =>
            {
                var cert = await db.Certificates
                    .FirstOrDefaultAsync(c => c.StudentId == studentId && c.CourseId == courseId);
                return cert is null ? Results.NotFound() : Results.Ok(cert);
            })
            .RequireAuthorization()
            .WithTags("Certificados");

        app.MapGet("/api/v1/certificates/student/{studentId}",
            async (Guid studentId, AnalyticsDbContext db) =>
            {
                var certs = await db.Certificates
                    .Where(c => c.StudentId == studentId)
                    .OrderByDescending(c => c.IssuedAt)
                    .ToListAsync();
                return Results.Ok(certs);
            })
            .RequireAuthorization()
            .WithTags("Certificados");

        app.MapGet("/api/v1/certificates/verify/{code}",
            async (Guid code, AnalyticsDbContext db) =>
            {
                var cert = await db.Certificates
                    .FirstOrDefaultAsync(c => c.VerificationCode == code);
                return cert is null
                    ? Results.NotFound(new { valid = false })
                    : Results.Ok(new { valid = true, certificate = cert });
            })
            .WithTags("Certificados");
    }
}
