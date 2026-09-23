// Test controller only. Never packaged in the official JAR.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

public sealed class Bloco54ChildResult {
    public int Code;
    public string Output = "";
    public bool Limited;
}

public static class Bloco54Child {
    private static readonly SemaphoreSlim Slots = new SemaphoreSlim(2, 2);
    public static Task<Bloco54ChildResult> Run(ProcessStartInfo start) {
        if (!Slots.Wait(0)) throw new InvalidOperationException("TWO_CHILD_LIMIT");
        return Task.Run(async () => {
            using (var process = new Process { StartInfo = start }) {
                var result = new Bloco54ChildResult();
                var output = new StringBuilder();
                var gate = new object();
                int bytes = 0;
                Action kill = () => { try { if (!process.HasExited) process.Kill(true); } catch (InvalidOperationException) {} };
                Func<StreamReader, Task> read = async reader => {
                    var buffer = new char[512];
                    int count;
                    while ((count = await reader.ReadAsync(buffer, 0, buffer.Length)) > 0) {
                        lock (gate) {
                            bytes += Encoding.UTF8.GetByteCount(buffer, 0, count);
                            if (bytes > 16384) { result.Limited = true; kill(); return; }
                            output.Append(buffer, 0, count);
                        }
                    }
                };
                try {
                    if (!process.Start()) throw new InvalidOperationException("CHILD_START_FAILED");
                    var stdout = read(process.StandardOutput);
                    var stderr = read(process.StandardError);
                    using (var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(60))) {
                        try { await process.WaitForExitAsync(timeout.Token); }
                        catch (OperationCanceledException) { result.Limited = true; kill(); }
                    }
                    await Task.WhenAll(stdout, stderr);
                    result.Code = result.Limited ? 124 : process.ExitCode;
                    result.Output = output.ToString();
                    return result;
                } finally { kill(); Slots.Release(); }
            }
        });
    }
}

public sealed class Bloco54Loopback : IDisposable {
    private readonly TcpListener listener;
    private readonly CancellationTokenSource stop = new CancellationTokenSource(TimeSpan.FromMinutes(15));
    private readonly Task worker;
    private readonly Dictionary<string, byte[]> fixtures = new Dictionary<string, byte[]>();
    public readonly string Token = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
    public readonly int Port;
    public int Requests;
    public int DataRequests;
    public string Scenario = "NORMAL";
    public string FixtureDate = "2024-01-01";
    public string Failure = "";

    public Bloco54Loopback(string directory) : this(directory, 0) { }
    public Bloco54Loopback(string directory, int requestedPort) {
        if (requestedPort < 0 || requestedPort > 65535) throw new ArgumentException("LOOPBACK_PORT_INVALID");
        foreach (string template in new[] { "6908", "6389" })
            foreach (string operation in new[] { "info", "data" }) {
                byte[] value = File.ReadAllBytes(Path.Combine(directory, template + "-" + operation + ".json"));
                if (value.Length > 1048576) throw new InvalidOperationException("FIXTURE_RESPONSE_LIMIT");
                fixtures.Add(template + "/" + operation, value);
            }
        listener = new TcpListener(IPAddress.Parse("127.0.0.1"), requestedPort);
        listener.Start(2);
        Port = ((IPEndPoint)listener.LocalEndpoint).Port;
        worker = Task.Run(Serve);
    }

    private async Task Serve() {
        try {
            while (!stop.IsCancellationRequested) {
                using (var client = await listener.AcceptTcpClientAsync(stop.Token)) {
                    client.ReceiveTimeout = 30000; client.SendTimeout = 30000;
                    using (var stream = client.GetStream())
                    using (var deadline = CancellationTokenSource.CreateLinkedTokenSource(stop.Token)) {
                        deadline.CancelAfter(TimeSpan.FromSeconds(30));
                        var header = new List<byte>(4096);
                        var one = new byte[1];
                        while (header.Count < 8192) {
                            if (await stream.ReadAsync(one, deadline.Token) != 1) throw new IOException("HTTP_HEADER_EOF");
                            header.Add(one[0]);
                            int n = header.Count;
                            if (n >= 4 && header[n-4] == 13 && header[n-3] == 10 && header[n-2] == 13 && header[n-1] == 10) break;
                        }
                        if (header.Count >= 8192) throw new IOException("HTTP_HEADER_LIMIT");
                        string text = Encoding.ASCII.GetString(header.ToArray());
                        string[] lines = text.Split(new[] { "\r\n" }, StringSplitOptions.None);
                        string[] request = lines[0].Split(' ');
                        if (request.Length != 3 || request[0] != "GET" || !request[1].StartsWith("/api/analytics/reports/", StringComparison.Ordinal))
                            throw new IOException("HTTP_ALLOWLIST");
                        bool authorized = false;
                        foreach (string line in lines)
                            if (line.Equals("Authorization: Bearer " + Token, StringComparison.OrdinalIgnoreCase)) authorized = true;
                        if (!authorized) throw new IOException("SYNTHETIC_TOKEN_REQUIRED");
                        if (Interlocked.Increment(ref Requests) > 1024) throw new IOException("HTTP_CUMULATIVE_LIMIT");
                        string target = request[1].Substring("/api/analytics/reports/".Length);
                        string path = target.Split('?')[0];
                        if (!fixtures.ContainsKey(path)) throw new IOException("HTTP_RESOURCE_ALLOWLIST");
                        byte[] body = fixtures[path];
                        int status = 200;
                        if (path.EndsWith("/data", StringComparison.Ordinal)) {
                            if (!DateTime.TryParseExact(FixtureDate, "yyyy-MM-dd", System.Globalization.CultureInfo.InvariantCulture, System.Globalization.DateTimeStyles.None, out DateTime fixtureDate)
                                || fixtureDate < new DateTime(2024, 1, 1) || fixtureDate > new DateTime(2024, 3, 2)) throw new IOException("SYNTHETIC_DATE_LIMIT");
                            body = Encoding.UTF8.GetBytes(Encoding.UTF8.GetString(body).Replace("2024-01-01", FixtureDate));
                            Interlocked.Increment(ref DataRequests);
                            var uri = new Uri("http://127.0.0.1/" + target);
                            string page = null;
                            foreach (string field in uri.Query.TrimStart('?').Split('&')) {
                                string[] pair = field.Split('=', 2);
                                if (pair.Length == 2 && Uri.UnescapeDataString(pair[0]) == "page") page = pair[1];
                            }
                            if (page == null || !int.TryParse(page, out int ordinal) || ordinal < 1 || ordinal > 4) throw new IOException("HTTP_PAGE_LIMIT");
                            if (ordinal > 1) body = Encoding.UTF8.GetBytes("{\"data\":[]}");
                            if (Scenario == "PARTIAL" && ordinal > 1) { status = 503; body = Encoding.UTF8.GetBytes("{}"); }
                            if (Scenario == "DRIFT" && ordinal == 1) body = Encoding.UTF8.GetBytes("{\"data\":[{\"id\":\"invalid-synthetic-type\"}]}");
                        }
                        byte[] response = Encoding.ASCII.GetBytes("HTTP/1.1 " + status + " Synthetic\r\nContent-Type: application/json\r\nContent-Length: " + body.Length + "\r\nConnection: close\r\n\r\n");
                        await stream.WriteAsync(response, deadline.Token);
                        await stream.WriteAsync(body, deadline.Token);
                    }
                }
            }
        } catch (OperationCanceledException) {}
          catch (Exception error) { Failure = error is IOException ? error.Message : "LOOPBACK_CONTROLLER_FAILED"; stop.Cancel(); }
    }
    public void Dispose() { stop.Cancel(); listener.Stop(); try { worker.GetAwaiter().GetResult(); } catch (Exception) {} stop.Dispose(); }
}
