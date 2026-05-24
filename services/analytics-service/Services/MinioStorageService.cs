using Minio;
using Minio.DataModel.Args;

namespace Puj.Analytics.Services;

public class MinioStorageService
{
    private readonly IMinioClient _minio;
    private readonly string       _bucket;
    private readonly string       _publicUrl;
    private readonly ILogger<MinioStorageService> _logger;

    public MinioStorageService(IConfiguration config, ILogger<MinioStorageService> logger)
    {
        _logger    = logger;
        _bucket    = config["Minio:Bucket"]    ?? Environment.GetEnvironmentVariable("MINIO_BUCKET")    ?? "certificates";
        _publicUrl = config["Minio:PublicUrl"] ?? Environment.GetEnvironmentVariable("MINIO_PUBLIC_URL") ?? "http://localhost:9000";

        string endpoint  = (config["Minio:Endpoint"]  ?? Environment.GetEnvironmentVariable("MINIO_ENDPOINT")  ?? "http://minio:9000")
                           .Replace("http://", "").Replace("https://", "");
        string accessKey = config["Minio:AccessKey"] ?? Environment.GetEnvironmentVariable("MINIO_ACCESS_KEY") ?? "puj_minio";
        string secretKey = config["Minio:SecretKey"] ?? Environment.GetEnvironmentVariable("MINIO_SECRET_KEY") ?? "puj_minio_secret";
        bool   useSSL    = (config["Minio:Endpoint"]  ?? Environment.GetEnvironmentVariable("MINIO_ENDPOINT")  ?? "").StartsWith("https");

        _minio = new MinioClient()
            .WithEndpoint(endpoint)
            .WithCredentials(accessKey, secretKey)
            .WithSSL(useSSL)
            .Build();
    }

    public async Task<string> UploadPdfAsync(string objectKey, byte[] data, CancellationToken ct = default)
    {
        await EnsureBucketAsync(ct);

        using var stream = new MemoryStream(data);
        var args = new PutObjectArgs()
            .WithBucket(_bucket)
            .WithObject(objectKey)
            .WithStreamData(stream)
            .WithObjectSize(data.Length)
            .WithContentType("application/pdf");

        await _minio.PutObjectAsync(args, ct);
        _logger.LogInformation("Certificate uploaded to MinIO: {Key}", objectKey);

        return $"{_publicUrl}/{_bucket}/{objectKey}";
    }

    private async Task EnsureBucketAsync(CancellationToken ct)
    {
        bool exists = await _minio.BucketExistsAsync(
            new BucketExistsArgs().WithBucket(_bucket), ct);
        if (!exists)
        {
            await _minio.MakeBucketAsync(
                new MakeBucketArgs().WithBucket(_bucket), ct);
        }
    }
}
