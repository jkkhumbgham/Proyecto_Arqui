using Microsoft.AspNetCore.Authorization;
using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Data;
using Puj.Analytics.Models;
using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace Puj.Analytics.Exports;

public static class ExportEndpoints
{
    public static void MapExportEndpoints(this WebApplication app)
    {
        QuestPDF.Settings.License = LicenseType.Community;

        var group = app.MapGroup("/api/v1/analytics/export")
            .WithTags("Exportación")
            .RequireAuthorization();

        // ── GET /api/v1/analytics/export/courses.csv ──────────────────────────
        group.MapGet("/courses.csv", async (AnalyticsDbContext db, HttpContext ctx) =>
        {
            if (!IsDirectorOrAdmin(ctx)) return Results.Forbid();

            var metrics = await db.CourseMetrics
                .OrderByDescending(c => c.TotalEnrollments)
                .ToListAsync();

            var csv = BuildCsv(
                new[] { "CourseId", "Title", "Enrollments", "Completions", "AvgScore", "PassRate", "UpdatedAt" },
                metrics.Select(m => new object[] {
                    m.CourseId, m.CourseTitle, m.TotalEnrollments,
                    m.TotalCompletions, m.AverageScore, m.PassRate,
                    m.UpdatedAt.ToString("yyyy-MM-dd HH:mm:ss")
                })
            );

            return Results.File(
                System.Text.Encoding.UTF8.GetBytes(csv),
                "text/csv; charset=utf-8",
                $"courses_{DateTime.UtcNow:yyyyMMdd}.csv");
        })
        .WithSummary("Exportar métricas de cursos en CSV (DIRECTOR/ADMIN)")
        .WithOpenApi();

        // ── GET /api/v1/analytics/export/submissions.csv ──────────────────────
        group.MapGet("/submissions.csv", async (AnalyticsDbContext db, HttpContext ctx,
            [Microsoft.AspNetCore.Mvc.FromQuery] Guid? courseId,
            [Microsoft.AspNetCore.Mvc.FromQuery] DateTime? from,
            [Microsoft.AspNetCore.Mvc.FromQuery] DateTime? to) =>
        {
            if (!IsDirectorOrAdmin(ctx)) return Results.Forbid();

            var query = db.SubmissionRecords.AsQueryable();
            if (courseId.HasValue) query = query.Where(s => s.CourseId == courseId);
            if (from.HasValue)     query = query.Where(s => s.OccurredAt >= from);
            if (to.HasValue)       query = query.Where(s => s.OccurredAt <= to);

            var rows = await query.OrderByDescending(s => s.OccurredAt).ToListAsync();

            var csv = BuildCsv(
                new[] { "SubmissionId", "UserId", "AssessmentId", "CourseId",
                        "Score", "MaxScore", "Passed", "DurationSec", "AttemptNumber", "OccurredAt" },
                rows.Select(s => new object[] {
                    s.Id, s.UserId, s.AssessmentId, s.CourseId,
                    s.Score, s.MaxScore, s.Passed, s.DurationSeconds,
                    s.AttemptNumber, s.OccurredAt.ToString("yyyy-MM-dd HH:mm:ss")
                })
            );

            return Results.File(
                System.Text.Encoding.UTF8.GetBytes(csv),
                "text/csv; charset=utf-8",
                $"submissions_{DateTime.UtcNow:yyyyMMdd}.csv");
        })
        .WithSummary("Exportar historial de submissions en CSV (DIRECTOR/ADMIN)")
        .WithOpenApi();

        // ── GET /api/v1/analytics/export/courses.pdf ──────────────────────────
        group.MapGet("/courses.pdf", async (AnalyticsDbContext db, HttpContext ctx) =>
        {
            if (!IsDirectorOrAdmin(ctx)) return Results.Forbid();

            var metrics = await db.CourseMetrics
                .OrderByDescending(c => c.TotalEnrollments)
                .ToListAsync();

            var totalUsers       = await db.UserRecords.CountAsync();
            var totalEnrollments = await db.EnrollmentRecords.CountAsync();
            var totalSubmissions = await db.SubmissionRecords.CountAsync();

            var pdfBytes = BuildCourseReportPdf(metrics, totalUsers, totalEnrollments, totalSubmissions);

            return Results.File(pdfBytes, "application/pdf",
                $"informe_cursos_{DateTime.UtcNow:yyyyMMdd}.pdf");
        })
        .WithSummary("Exportar informe de cursos en PDF (DIRECTOR/ADMIN)")
        .WithOpenApi();

        // ── GET /api/v1/analytics/export/submissions.pdf ──────────────────────
        group.MapGet("/submissions.pdf", async (AnalyticsDbContext db, HttpContext ctx,
            [Microsoft.AspNetCore.Mvc.FromQuery] Guid? courseId) =>
        {
            if (!IsDirectorOrAdmin(ctx)) return Results.Forbid();

            var query = db.SubmissionRecords.AsQueryable();
            if (courseId.HasValue) query = query.Where(s => s.CourseId == courseId);

            var rows = await query.OrderByDescending(s => s.OccurredAt)
                .Take(500) // cap for large exports
                .ToListAsync();

            CourseMetric? metric = null;
            if (courseId.HasValue)
                metric = await db.CourseMetrics.FirstOrDefaultAsync(m => m.CourseId == courseId);

            var pdfBytes = BuildSubmissionsReportPdf(rows, metric);

            return Results.File(pdfBytes, "application/pdf",
                $"informe_evaluaciones_{DateTime.UtcNow:yyyyMMdd}.pdf");
        })
        .WithSummary("Exportar informe de evaluaciones en PDF (DIRECTOR/ADMIN)")
        .WithOpenApi();
    }

    // ── CSV builder (RFC 4180) ─────────────────────────────────────────────────

    private static string BuildCsv(string[] headers, IEnumerable<object[]> rows)
    {
        var sb = new System.Text.StringBuilder();
        sb.AppendLine(string.Join(",", headers.Select(EscapeCsv)));
        foreach (var row in rows)
            sb.AppendLine(string.Join(",", row.Select(v => EscapeCsv(v?.ToString() ?? ""))));
        return sb.ToString();
    }

    private static string EscapeCsv(string value)
    {
        if (value.Contains(',') || value.Contains('"') || value.Contains('\n'))
            return $"\"{value.Replace("\"", "\"\"")}\"";
        return value;
    }

    // ── PDF builders ──────────────────────────────────────────────────────────

    private static byte[] BuildCourseReportPdf(
        List<CourseMetric> metrics, int totalUsers, int totalEnrollments, int totalSubmissions)
    {
        return Document.Create(container =>
        {
            container.Page(page =>
            {
                page.Size(PageSizes.A4);
                page.Margin(2, Unit.Centimetre);
                page.DefaultTextStyle(t => t.FontSize(10));

                page.Header().Column(col =>
                {
                    col.Item().Text("Plataforma de Aprendizaje — PUJ 2026")
                       .SemiBold().FontSize(14).FontColor(Colors.Blue.Darken3);
                    col.Item().Text($"Informe de Cursos — Generado: {DateTime.UtcNow:yyyy-MM-dd HH:mm} UTC")
                       .FontSize(9).FontColor(Colors.Grey.Darken1);
                    col.Item().PaddingTop(4).LineHorizontal(1).LineColor(Colors.Blue.Darken2);
                });

                page.Content().Column(col =>
                {
                    // Summary cards
                    col.Item().PaddingTop(12).Row(row =>
                    {
                        row.RelativeItem().Border(1).BorderColor(Colors.Grey.Lighten2)
                           .Padding(8).Column(c => {
                               c.Item().Text("Usuarios registrados").FontSize(9).FontColor(Colors.Grey.Darken1);
                               c.Item().Text(totalUsers.ToString()).SemiBold().FontSize(20)
                                       .FontColor(Colors.Blue.Darken3);
                           });
                        row.ConstantItem(8);
                        row.RelativeItem().Border(1).BorderColor(Colors.Grey.Lighten2)
                           .Padding(8).Column(c => {
                               c.Item().Text("Inscripciones totales").FontSize(9).FontColor(Colors.Grey.Darken1);
                               c.Item().Text(totalEnrollments.ToString()).SemiBold().FontSize(20)
                                       .FontColor(Colors.Blue.Darken3);
                           });
                        row.ConstantItem(8);
                        row.RelativeItem().Border(1).BorderColor(Colors.Grey.Lighten2)
                           .Padding(8).Column(c => {
                               c.Item().Text("Evaluaciones presentadas").FontSize(9).FontColor(Colors.Grey.Darken1);
                               c.Item().Text(totalSubmissions.ToString()).SemiBold().FontSize(20)
                                       .FontColor(Colors.Blue.Darken3);
                           });
                    });

                    col.Item().PaddingTop(20).Text("Métricas por Curso").SemiBold().FontSize(12);
                    col.Item().PaddingTop(8).Table(table =>
                    {
                        table.ColumnsDefinition(c =>
                        {
                            c.RelativeColumn(3); // Title
                            c.RelativeColumn(1); // Enrollments
                            c.RelativeColumn(1); // Completions
                            c.RelativeColumn(1); // Avg Score
                            c.RelativeColumn(1); // Pass Rate
                        });

                        // Header
                        table.Header(header =>
                        {
                            static IContainer HeaderCell(IContainer c) =>
                                c.Background(Colors.Blue.Darken3).Padding(6);

                            header.Cell().Element(HeaderCell)
                                  .Text("Curso").FontColor(Colors.White).SemiBold();
                            header.Cell().Element(HeaderCell)
                                  .Text("Inscritos").FontColor(Colors.White).SemiBold();
                            header.Cell().Element(HeaderCell)
                                  .Text("Completados").FontColor(Colors.White).SemiBold();
                            header.Cell().Element(HeaderCell)
                                  .Text("Prom. Score").FontColor(Colors.White).SemiBold();
                            header.Cell().Element(HeaderCell)
                                  .Text("Tasa Aprobación").FontColor(Colors.White).SemiBold();
                        });

                        // Rows
                        bool odd = false;
                        foreach (var m in metrics)
                        {
                            var bg = odd ? Colors.Grey.Lighten4 : Colors.White;
                            odd = !odd;

                            static IContainer DataCell(IContainer c, string bg) =>
                                c.Background(bg).Padding(5);

                            table.Cell().Element(c => DataCell(c, bg)).Text(m.CourseTitle);
                            table.Cell().Element(c => DataCell(c, bg)).Text(m.TotalEnrollments.ToString());
                            table.Cell().Element(c => DataCell(c, bg)).Text(m.TotalCompletions.ToString());
                            table.Cell().Element(c => DataCell(c, bg))
                                 .Text($"{m.AverageScore:F1}%");
                            table.Cell().Element(c => DataCell(c, bg))
                                 .Text($"{m.PassRate:F1}%")
                                 .FontColor(m.PassRate >= 60 ? Colors.Green.Darken2 : Colors.Red.Darken2);
                        }
                    });
                });

                page.Footer().AlignCenter()
                    .Text(t => {
                        t.Span("Página ").FontSize(9).FontColor(Colors.Grey.Medium);
                        t.CurrentPageNumber().FontSize(9);
                        t.Span(" de ").FontSize(9).FontColor(Colors.Grey.Medium);
                        t.TotalPages().FontSize(9);
                    });
            });
        }).GeneratePdf();
    }

    private static byte[] BuildSubmissionsReportPdf(List<SubmissionRecord> rows, CourseMetric? metric)
    {
        return Document.Create(container =>
        {
            container.Page(page =>
            {
                page.Size(PageSizes.A4.Landscape());
                page.Margin(1.5f, Unit.Centimetre);
                page.DefaultTextStyle(t => t.FontSize(9));

                page.Header().Column(col =>
                {
                    var title = metric != null
                        ? $"Historial de Evaluaciones — {metric.CourseTitle}"
                        : "Historial de Evaluaciones — Todos los cursos";
                    col.Item().Text(title).SemiBold().FontSize(13).FontColor(Colors.Blue.Darken3);
                    col.Item().Text($"Generado: {DateTime.UtcNow:yyyy-MM-dd HH:mm} UTC — {rows.Count} registros")
                       .FontSize(8).FontColor(Colors.Grey.Darken1);
                    col.Item().PaddingTop(4).LineHorizontal(1).LineColor(Colors.Blue.Darken2);
                });

                page.Content().PaddingTop(10).Table(table =>
                {
                    table.ColumnsDefinition(c =>
                    {
                        c.RelativeColumn(2); // UserId
                        c.RelativeColumn(1.5f); // Score
                        c.RelativeColumn(1); // Passed
                        c.RelativeColumn(1); // Attempt
                        c.RelativeColumn(1.5f); // Duration
                        c.RelativeColumn(2); // Date
                    });

                    table.Header(header =>
                    {
                        static IContainer H(IContainer c) =>
                            c.Background(Colors.Blue.Darken3).Padding(5);

                        foreach (var h in new[] { "Usuario ID", "Score", "Aprobado", "Intento", "Duración (min)", "Fecha" })
                            header.Cell().Element(H).Text(h).FontColor(Colors.White).SemiBold();
                    });

                    bool odd = false;
                    foreach (var s in rows)
                    {
                        var bg = odd ? Colors.Grey.Lighten4 : Colors.White;
                        odd = !odd;

                        static IContainer D(IContainer c, string bg) => c.Background(bg).Padding(4);

                        table.Cell().Element(c => D(c, bg)).Text(s.UserId.ToString()[..8] + "...");
                        table.Cell().Element(c => D(c, bg)).Text($"{s.Score}/{s.MaxScore}");
                        table.Cell().Element(c => D(c, bg))
                             .Text(s.Passed ? "Sí" : "No")
                             .FontColor(s.Passed ? Colors.Green.Darken2 : Colors.Red.Darken2);
                        table.Cell().Element(c => D(c, bg)).Text(s.AttemptNumber.ToString());
                        table.Cell().Element(c => D(c, bg))
                             .Text($"{s.DurationSeconds / 60.0:F1}");
                        table.Cell().Element(c => D(c, bg))
                             .Text(s.OccurredAt.ToString("yyyy-MM-dd HH:mm"));
                    }
                });

                page.Footer().AlignCenter().Text(t => {
                    t.Span("Página ").FontSize(8).FontColor(Colors.Grey.Medium);
                    t.CurrentPageNumber().FontSize(8);
                    t.Span(" de ").FontSize(8).FontColor(Colors.Grey.Medium);
                    t.TotalPages().FontSize(8);
                });
            });
        }).GeneratePdf();
    }

    private static bool IsDirectorOrAdmin(HttpContext ctx)
    {
        var role = ctx.User.FindFirst(System.Security.Claims.ClaimTypes.Role)?.Value
                ?? ctx.User.FindFirst("role")?.Value ?? "";
        return role is "DIRECTOR" or "ADMIN";
    }
}
