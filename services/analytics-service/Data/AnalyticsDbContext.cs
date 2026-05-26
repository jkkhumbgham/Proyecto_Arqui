using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Models;

namespace Puj.Analytics.Data;

public class AnalyticsDbContext(DbContextOptions<AnalyticsDbContext> options)
    : DbContext(options)
{
    public DbSet<PlatformStats>    PlatformStats     { get; set; }
    public DbSet<StudentNameCache> StudentNameCaches { get; set; }
    public DbSet<CourseMetric>     CourseMetrics     { get; set; }
    public DbSet<MonthlySnapshot>  MonthlySnapshots  { get; set; }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.HasDefaultSchema("analytics");

        modelBuilder.Entity<PlatformStats>(e => {
            e.ToTable("platform_stats");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.TotalUsers).HasColumnName("total_users");
            e.Property(x => x.TotalEnrollments).HasColumnName("total_enrollments");
            e.Property(x => x.TotalSubmissions).HasColumnName("total_submissions");
            e.Property(x => x.TotalCourses).HasColumnName("total_courses");
            e.Property(x => x.RawScoreSum).HasColumnName("raw_score_sum").HasPrecision(14, 4);
            e.Property(x => x.RawMaxScoreSum).HasColumnName("raw_max_score_sum").HasPrecision(14, 4);
            e.Property(x => x.PassCount).HasColumnName("pass_count");
            e.Property(x => x.UpdatedAt).HasColumnName("updated_at");
            e.Ignore(x => x.AvgScore);
            e.Ignore(x => x.PassRate);
        });

        modelBuilder.Entity<StudentNameCache>(e => {
            e.ToTable("student_name_cache");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.UserId).HasColumnName("user_id");
            e.Property(x => x.StudentName).HasColumnName("student_name").HasMaxLength(200);
            e.Property(x => x.Email).HasColumnName("email").HasMaxLength(200);
            e.Property(x => x.Role).HasColumnName("role").HasMaxLength(50);
            e.Property(x => x.LastLoginAt).HasColumnName("last_login_at");
            e.Property(x => x.UpdatedAt).HasColumnName("updated_at");
            e.HasIndex(x => x.UserId).IsUnique();
        });

        modelBuilder.Entity<CourseMetric>(e => {
            e.ToTable("course_metrics");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.CourseId).HasColumnName("course_id");
            e.Property(x => x.CourseTitle).HasColumnName("course_title");
            e.Property(x => x.TotalEnrollments).HasColumnName("total_enrollments");
            e.Property(x => x.TotalSubmissions).HasColumnName("total_submissions");
            e.Property(x => x.RawScoreSum).HasColumnName("raw_score_sum").HasPrecision(14, 4);
            e.Property(x => x.RawMaxScoreSum).HasColumnName("raw_max_score_sum").HasPrecision(14, 4);
            e.Property(x => x.PassCount).HasColumnName("pass_count");
            e.Property(x => x.UpdatedAt).HasColumnName("updated_at");
            e.HasIndex(x => x.CourseId).IsUnique();
            e.Ignore(x => x.AverageScore);
            e.Ignore(x => x.PassRate);
        });

        modelBuilder.Entity<MonthlySnapshot>(e => {
            e.ToTable("monthly_snapshots");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.Year).HasColumnName("year");
            e.Property(x => x.Month).HasColumnName("month");
            e.Property(x => x.TotalUsers).HasColumnName("total_users");
            e.Property(x => x.TotalEnrollments).HasColumnName("total_enrollments");
            e.Property(x => x.TotalSubmissions).HasColumnName("total_submissions");
            e.Property(x => x.TotalCourses).HasColumnName("total_courses");
            e.Property(x => x.AvgScore).HasColumnName("avg_score").HasPrecision(5, 2);
            e.Property(x => x.PassRate).HasColumnName("pass_rate").HasPrecision(5, 2);
            e.Property(x => x.NewUsersThisMonth).HasColumnName("new_users_this_month");
            e.Property(x => x.NewEnrollmentsThisMonth).HasColumnName("new_enrollments_this_month");
            e.Property(x => x.NewSubmissionsThisMonth).HasColumnName("new_submissions_this_month");
            e.Property(x => x.GeneratedAt).HasColumnName("generated_at");
            e.HasIndex(x => new { x.Year, x.Month }).IsUnique();
        });
    }
}
