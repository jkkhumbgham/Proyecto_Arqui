using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Models;

namespace Puj.Analytics.Data;

/// <summary>
/// Contexto de Entity Framework Core para el servicio de analítica.
///
/// <para>
/// Administra las cuatro entidades de persistencia del servicio:
/// estadísticas globales, caché de nombres de usuarios, métricas por
/// curso y snapshots mensuales. Todas las tablas residen en el esquema
/// <c>analytics</c> de PostgreSQL.
/// </para>
/// </summary>
/// <param name="opciones">
/// Opciones de configuración del contexto inyectadas por el contenedor DI.
/// </param>
public class ContextoBaseDatosAnaliticas(DbContextOptions<ContextoBaseDatosAnaliticas> opciones)
    : DbContext(opciones)
{
    /// <summary>
    /// Singleton de estadísticas globales de la plataforma.
    /// </summary>
    public DbSet<EstadisticasPlataforma> EstadisticasPlataforma { get; set; }

    /// <summary>
    /// Caché local con nombre, correo y rol de cada usuario registrado.
    /// </summary>
    public DbSet<CacheNombreEstudiante> CachesNombreEstudiante { get; set; }

    /// <summary>
    /// Métricas agregadas por curso (inscripciones, entregas, puntajes).
    /// </summary>
    public DbSet<MetricaCurso> MetricasCurso { get; set; }

    /// <summary>
    /// Fotografías mensuales de las estadísticas globales de la plataforma.
    /// </summary>
    public DbSet<ResumenMensual> ResumenesMensuales { get; set; }

    /// <summary>
    /// Configura el mapeo objeto-relacional de todas las entidades del servicio.
    /// </summary>
    /// <param name="constructorModelo">Constructor del modelo EF Core.</param>
    protected override void OnModelCreating(ModelBuilder constructorModelo)
    {
        constructorModelo.HasDefaultSchema("analytics");

        constructorModelo.Entity<EstadisticasPlataforma>(e => {
            e.ToTable("platform_stats");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.TotalUsuarios).HasColumnName("total_users");
            e.Property(x => x.TotalInscripciones).HasColumnName("total_enrollments");
            e.Property(x => x.TotalEntregas).HasColumnName("total_submissions");
            e.Property(x => x.TotalCursos).HasColumnName("total_courses");
            e.Property(x => x.SumaPuntajesBrutos)
                .HasColumnName("raw_score_sum")
                .HasPrecision(14, 4);
            e.Property(x => x.SumaMaxPuntajesBrutos)
                .HasColumnName("raw_max_score_sum")
                .HasPrecision(14, 4);
            e.Property(x => x.ConteoAprobados).HasColumnName("pass_count");
            e.Property(x => x.ActualizadoEn).HasColumnName("updated_at");
            e.Ignore(x => x.PuntajePromedio);
            e.Ignore(x => x.TasaAprobacion);
        });

        constructorModelo.Entity<CacheNombreEstudiante>(e => {
            e.ToTable("student_name_cache");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.IdUsuario).HasColumnName("user_id");
            e.Property(x => x.NombreEstudiante)
                .HasColumnName("student_name")
                .HasMaxLength(200);
            e.Property(x => x.Correo)
                .HasColumnName("email")
                .HasMaxLength(200);
            e.Property(x => x.Rol)
                .HasColumnName("role")
                .HasMaxLength(50);
            e.Property(x => x.UltimoAccesoEn).HasColumnName("last_login_at");
            e.Property(x => x.ActualizadoEn).HasColumnName("updated_at");
            e.HasIndex(x => x.IdUsuario).IsUnique();
        });

        constructorModelo.Entity<MetricaCurso>(e => {
            e.ToTable("course_metrics");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.IdCurso).HasColumnName("course_id");
            e.Property(x => x.TituloCurso).HasColumnName("course_title");
            e.Property(x => x.TotalInscripciones).HasColumnName("total_enrollments");
            e.Property(x => x.TotalEntregas).HasColumnName("total_submissions");
            e.Property(x => x.SumaPuntajesBrutos)
                .HasColumnName("raw_score_sum")
                .HasPrecision(14, 4);
            e.Property(x => x.SumaMaxPuntajesBrutos)
                .HasColumnName("raw_max_score_sum")
                .HasPrecision(14, 4);
            e.Property(x => x.ConteoAprobados).HasColumnName("pass_count");
            e.Property(x => x.ActualizadoEn).HasColumnName("updated_at");
            e.HasIndex(x => x.IdCurso).IsUnique();
            e.Ignore(x => x.PuntajePromedio);
            e.Ignore(x => x.TasaAprobacion);
        });

        constructorModelo.Entity<ResumenMensual>(e => {
            e.ToTable("monthly_snapshots");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.Anio).HasColumnName("year");
            e.Property(x => x.Mes).HasColumnName("month");
            e.Property(x => x.TotalUsuarios).HasColumnName("total_users");
            e.Property(x => x.TotalInscripciones).HasColumnName("total_enrollments");
            e.Property(x => x.TotalEntregas).HasColumnName("total_submissions");
            e.Property(x => x.TotalCursos).HasColumnName("total_courses");
            e.Property(x => x.PuntajePromedio)
                .HasColumnName("avg_score")
                .HasPrecision(5, 2);
            e.Property(x => x.TasaAprobacion)
                .HasColumnName("pass_rate")
                .HasPrecision(5, 2);
            e.Property(x => x.UsuariosNuevosEsteMes)
                .HasColumnName("new_users_this_month");
            e.Property(x => x.InscripcionesNuevasEsteMes)
                .HasColumnName("new_enrollments_this_month");
            e.Property(x => x.EntregasNuevasEsteMes)
                .HasColumnName("new_submissions_this_month");
            e.Property(x => x.GeneradoEn).HasColumnName("generated_at");
            e.HasIndex(x => new { x.Anio, x.Mes }).IsUnique();
        });
    }
}
