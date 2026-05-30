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

        var grupo = app.MapGroup("/api/v1/analytics/export")
            .WithTags("Exportación")
            .RequireAuthorization();

        // ── GET /api/v1/analytics/export/courses.csv ──────────────────────────
        grupo.MapGet("/courses.csv", async (ContextoBaseDatosAnaliticas baseDatos, HttpContext ctx) =>
        {
            if (!EsDirectorOAdmin(ctx)) return Results.Forbid();

            var metricas = await baseDatos.MetricasCurso
                .OrderByDescending(c => c.TotalInscripciones)
                .ToListAsync();

            var csv = ConstruirCsv(
                new[] { "CourseId", "Title", "Enrollments", "Submissions", "AvgScore", "PassRate", "UpdatedAt" },
                metricas.Select(m => new object[] {
                    m.IdCurso, m.TituloCurso, m.TotalInscripciones,
                    m.TotalEntregas, $"{m.PuntajePromedio:F2}", $"{m.TasaAprobacion:F2}",
                    m.ActualizadoEn.ToString("yyyy-MM-dd HH:mm:ss")
                })
            );

            return Results.File(
                System.Text.Encoding.UTF8.GetBytes(csv),
                "text/csv; charset=utf-8",
                $"courses_{DateTime.UtcNow:yyyyMMdd}.csv");
        })
        .WithSummary("Exportar métricas de cursos en CSV (DIRECTOR/ADMIN)");

        // ── GET /api/v1/analytics/export/courses.pdf ──────────────────────────
        grupo.MapGet("/courses.pdf", async (ContextoBaseDatosAnaliticas baseDatos, HttpContext ctx) =>
        {
            if (!EsDirectorOAdmin(ctx)) return Results.Forbid();

            var metricas = await baseDatos.MetricasCurso
                .OrderByDescending(c => c.TotalInscripciones)
                .ToListAsync();

            var estadisticas = await baseDatos.EstadisticasPlataforma.FirstOrDefaultAsync();

            var bytesPdf = ConstruirInformeCursosPdf(
                metricas,
                estadisticas?.TotalUsuarios      ?? 0,
                estadisticas?.TotalInscripciones ?? 0,
                estadisticas?.TotalEntregas      ?? 0);

            return Results.File(bytesPdf, "application/pdf",
                $"informe_cursos_{DateTime.UtcNow:yyyyMMdd}.pdf");
        })
        .WithSummary("Exportar informe de cursos en PDF (DIRECTOR/ADMIN)");
    }

    // ── CSV builder (RFC 4180) ─────────────────────────────────────────────────

    private static string ConstruirCsv(string[] encabezados, IEnumerable<object[]> filas)
    {
        var sb = new System.Text.StringBuilder();
        sb.AppendLine(string.Join(",", encabezados.Select(EscaparCsv)));
        foreach (var fila in filas)
            sb.AppendLine(string.Join(",", fila.Select(v => EscaparCsv(v?.ToString() ?? ""))));
        return sb.ToString();
    }

    private static string EscaparCsv(string valor)
    {
        if (valor.Contains(',') || valor.Contains('"') || valor.Contains('\n'))
            return $"\"{valor.Replace("\"", "\"\"")}\"";
        return valor;
    }

    // ── PDF builder ───────────────────────────────────────────────────────────

    private static byte[] ConstruirInformeCursosPdf(
        List<MetricaCurso> metricas, long totalUsuarios, long totalInscripciones, long totalEntregas)
    {
        return Document.Create(contenedor =>
        {
            contenedor.Page(pagina =>
            {
                pagina.Size(PageSizes.A4);
                pagina.Margin(2, Unit.Centimetre);
                pagina.DefaultTextStyle(t => t.FontSize(10));

                pagina.Header().Column(col =>
                {
                    col.Item().Text("Plataforma de Aprendizaje — PUJ 2026")
                       .SemiBold().FontSize(14).FontColor(Colors.Blue.Darken3);
                    col.Item().Text($"Informe de Cursos — Generado: {DateTime.UtcNow:yyyy-MM-dd HH:mm} UTC")
                       .FontSize(9).FontColor(Colors.Grey.Darken1);
                    col.Item().PaddingTop(4).LineHorizontal(1).LineColor(Colors.Blue.Darken2);
                });

                pagina.Content().Column(col =>
                {
                    col.Item().PaddingTop(12).Row(fila =>
                    {
                        fila.RelativeItem().Border(1).BorderColor(Colors.Grey.Lighten2)
                           .Padding(8).Column(c => {
                               c.Item().Text("Usuarios registrados").FontSize(9).FontColor(Colors.Grey.Darken1);
                               c.Item().Text(totalUsuarios.ToString()).SemiBold().FontSize(20)
                                       .FontColor(Colors.Blue.Darken3);
                           });
                        fila.ConstantItem(8);
                        fila.RelativeItem().Border(1).BorderColor(Colors.Grey.Lighten2)
                           .Padding(8).Column(c => {
                               c.Item().Text("Inscripciones totales").FontSize(9).FontColor(Colors.Grey.Darken1);
                               c.Item().Text(totalInscripciones.ToString()).SemiBold().FontSize(20)
                                       .FontColor(Colors.Blue.Darken3);
                           });
                        fila.ConstantItem(8);
                        fila.RelativeItem().Border(1).BorderColor(Colors.Grey.Lighten2)
                           .Padding(8).Column(c => {
                               c.Item().Text("Evaluaciones presentadas").FontSize(9).FontColor(Colors.Grey.Darken1);
                               c.Item().Text(totalEntregas.ToString()).SemiBold().FontSize(20)
                                       .FontColor(Colors.Blue.Darken3);
                           });
                    });

                    col.Item().PaddingTop(20).Text("Métricas por Curso").SemiBold().FontSize(12);
                    col.Item().PaddingTop(8).Table(tabla =>
                    {
                        tabla.ColumnsDefinition(c =>
                        {
                            c.RelativeColumn(3);
                            c.RelativeColumn(1);
                            c.RelativeColumn(1);
                            c.RelativeColumn(1);
                            c.RelativeColumn(1);
                        });

                        tabla.Header(encabezado =>
                        {
                            static IContainer CeldaEncabezado(IContainer c) =>
                                c.Background(Colors.Blue.Darken3).Padding(6);

                            encabezado.Cell().Element(CeldaEncabezado).Text("Curso").FontColor(Colors.White).SemiBold();
                            encabezado.Cell().Element(CeldaEncabezado).Text("Inscritos").FontColor(Colors.White).SemiBold();
                            encabezado.Cell().Element(CeldaEncabezado).Text("Entregas").FontColor(Colors.White).SemiBold();
                            encabezado.Cell().Element(CeldaEncabezado).Text("Prom. Score").FontColor(Colors.White).SemiBold();
                            encabezado.Cell().Element(CeldaEncabezado).Text("Tasa Aprobación").FontColor(Colors.White).SemiBold();
                        });

                        bool impar = false;
                        foreach (var m in metricas)
                        {
                            var fondo = impar ? Colors.Grey.Lighten4 : Colors.White;
                            impar = !impar;

                            static IContainer CeldaDatos(IContainer c, string fondo) =>
                                c.Background(fondo).Padding(5);

                            tabla.Cell().Element(c => CeldaDatos(c, fondo)).Text(m.TituloCurso);
                            tabla.Cell().Element(c => CeldaDatos(c, fondo)).Text(m.TotalInscripciones.ToString());
                            tabla.Cell().Element(c => CeldaDatos(c, fondo)).Text(m.TotalEntregas.ToString());
                            tabla.Cell().Element(c => CeldaDatos(c, fondo)).Text($"{m.PuntajePromedio:F1}%");
                            tabla.Cell().Element(c => CeldaDatos(c, fondo))
                                 .Text($"{m.TasaAprobacion:F1}%")
                                 .FontColor(m.TasaAprobacion >= 60 ? Colors.Green.Darken2 : Colors.Red.Darken2);
                        }
                    });
                });

                pagina.Footer().AlignCenter()
                    .Text(t => {
                        t.Span("Página ").FontSize(9).FontColor(Colors.Grey.Medium);
                        t.CurrentPageNumber().FontSize(9);
                        t.Span(" de ").FontSize(9).FontColor(Colors.Grey.Medium);
                        t.TotalPages().FontSize(9);
                    });
            });
        }).GeneratePdf();
    }

    private static bool EsDirectorOAdmin(HttpContext ctx)
    {
        var rol = ctx.User.FindFirst(System.Security.Claims.ClaimTypes.Role)?.Value
                ?? ctx.User.FindFirst("role")?.Value ?? "";
        return rol is "DIRECTOR" or "ADMIN";
    }
}
