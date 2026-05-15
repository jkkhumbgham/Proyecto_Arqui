using Microsoft.EntityFrameworkCore;
using Puj.Analytics.Models;

namespace Puj.Analytics.Data;

public class AnalyticsDbContext(DbContextOptions<AnalyticsDbContext> options)
    : DbContext(options)
{
    public DbSet<SubmissionRecord>  SubmissionRecords  { get; set; }
    public DbSet<EnrollmentRecord>  EnrollmentRecords  { get; set; }
    public DbSet<UserRecord>        UserRecords        { get; set; }
    public DbSet<CourseMetric>      CourseMetrics      { get; set; }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.HasDefaultSchema("analytics");

        modelBuilder.Entity<SubmissionRecord>(e => {
            e.ToTable("submission_records");
            e.HasKey(x => x.Id);
            e.Property(x => x.Id).HasColumnName("id");
            e.HasIndex(x => new { x.UserId, x.AssessmentId });
        });

        modelBuilder.Entity<EnrollmentRecord>(e => {
            e.ToTable("enrollment_records");
            e.HasKey(x => x.Id);
            e.HasIndex(x => new { x.UserId, x.CourseId });
        });

        modelBuilder.Entity<UserRecord>(e => {
            e.ToTable("user_records");
            e.HasKey(x => x.Id);
            e.HasIndex(x => x.UserId).IsUnique();
        });

        modelBuilder.Entity<CourseMetric>(e => {
            e.ToTable("course_metrics");
            e.HasKey(x => x.Id);
            e.HasIndex(x => x.CourseId).IsUnique();
        });
    }
}
