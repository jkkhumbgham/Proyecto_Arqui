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
                new[] { "CourseId", "Title", "Enrollments", "Submissions", "AvgScore", "PassRate", "UpdatedAt" },
                metrics.Select(m => new object[] {
                    m.CourseId, m.CourseTitle, m.TotalEnrollments,
                    m.TotalSubmissions, $"{m.AverageScore:F2}", $"{m.PassRate:F2}",
                    m.UpdatedAt.ToString("yyyy-MM-dd HH:mm:ss")
                })
            );

            return Results.File(
                System.Text.Encoding.UTF8.GetBytes(csv),
                "text/csv; charset=utf-8",
                $"courses_{DateTime.UtcNow:yyyyMMdd}.csv");
        })
        .WithSummary("Exportar métricas de cursos en CSV (DIRECTOR/ADMIN)");

        // ── GET /api/v1/analytics/export/courses.pdf ──────────────────────────
        group.MapGet("/courses.pdf", async (AnalyticsDbContext db, HttpContext ctx) =>
        {
            if (!IsDirectorOrAdmin(ctx)) return Results.Forbid();

            var metrics = await db.CourseMetrics
                .OrderByDescending(c => c.TotalEnrollments)
                .ToListAsync();

            var stats = await db.PlatformStats.FirstOrDefaultAsync();

            var pdfBytes = BuildCourseReportPdf(
                metrics,
                stats?.TotalUsers       ?? 0,
                stats?.TotalEnrollments ?? 0,
                stats?.TotalSubmissions ?? 0);

            return Results.File(pdfBytes, "application/pdf",
                $"informe_cursos_{DateTime.UtcNow:yyyyMMdd}.pdf");
        })
        .WithSummary("Exportar informe de cursos en PDF (DIRECTOR/ADMIN)");
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

    // ── PDF builder ───────────────────────────────────────────────────────────

    private static byte[] BuildCourseReportPdf(
        List<CourseMetric> metrics, long totalUsers, long totalEnrollments, long totalSubmissions)
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
                            c.RelativeColumn(3);
                            c.RelativeColumn(1);
                            c.RelativeColumn(1);
                            c.RelativeColumn(1);
                            c.RelativeColumn(1);
                        });

                        table.Header(header =>
                        {
                            static IContainer HeaderCell(IContainer c) =>
                                c.Background(Colors.Blue.Darken3).Padding(6);

                            header.Cell().Element(HeaderCell).Text("Curso").FontColor(Colors.White).SemiBold();
                            header.Cell().Element(HeaderCell).Text("Inscritos").FontColor(Colors.White).SemiBold();
                            header.Cell().Element(HeaderCell).Text("Entregas").FontColor(Colors.White).SemiBold();
                            header.Cell().Element(HeaderCell).Text("Prom. Score").FontColor(Colors.White).SemiBold();
                            header.Cell().Element(HeaderCell).Text("Tasa Aprobación").FontColor(Colors.White).SemiBold();
                        });

                        bool odd = false;
                        foreach (var m in metrics)
                        {
                            var bg = odd ? Colors.Grey.Lighten4 : Colors.White;
                            odd = !odd;

                            static IContainer DataCell(IContainer c, string bg) =>
                                c.Background(bg).Padding(5);

                            table.Cell().Element(c => DataCell(c, bg)).Text(m.CourseTitle);
                            table.Cell().Element(c => DataCell(c, bg)).Text(m.TotalEnrollments.ToString());
                            table.Cell().Element(c => DataCell(c, bg)).Text(m.TotalSubmissions.ToString());
                            table.Cell().Element(c => DataCell(c, bg)).Text($"{m.AverageScore:F1}%");
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

    private static bool IsDirectorOrAdmin(HttpContext ctx)
    {
        var role = ctx.User.FindFirst(System.Security.Claims.ClaimTypes.Role)?.Value
                ?? ctx.User.FindFirst("role")?.Value ?? "";
        return role is "DIRECTOR" or "ADMIN";
    }
}
