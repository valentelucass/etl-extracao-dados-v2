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

public sealed class Bloco55ChildResult {
    public int Code;
    public string Output = "";
    public bool Limited;
    public bool KilledByController;
    public bool ControlSent;
}

public static class Bloco55Child {
    private static readonly SemaphoreSlim Slots = new SemaphoreSlim(2, 2);
    public static Task<Bloco55ChildResult> Run(ProcessStartInfo start) {
        return Run(start, null, 0, null);
    }
    public static Task<Bloco55ChildResult> Run(ProcessStartInfo start, Bloco55Loopback source, int cancelAfterData, string killMarker) {
        if (String.IsNullOrEmpty(killMarker)) killMarker = null;
        if (source != null && (cancelAfterData < 1 || cancelAfterData > 1024 || !start.RedirectStandardInput)) throw new InvalidOperationException("OWN_CONTROL_INPUT_REQUIRED");
        if (killMarker != null && killMarker != "RUNTIME_OBSERVATION reason=NOT_FOUND" && killMarker != "RUNTIME_SQL_OBSERVATION state=PUBLISHED") throw new InvalidOperationException("OWN_KILL_MARKER_ALLOWLIST");
        if (!Slots.Wait(0)) throw new InvalidOperationException("TWO_CHILD_LIMIT");
        return Task.Run(async () => {
            using (var process = new Process { StartInfo = start }) {
                var result = new Bloco55ChildResult();
                var output = new StringBuilder();
                var gate = new object();
                using var controlStop = new CancellationTokenSource(TimeSpan.FromSeconds(60));
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
                            if (killMarker != null && output.ToString().Contains(killMarker, StringComparison.Ordinal)) {
                                result.KilledByController = true;
                                kill();
                            }
                        }
                    }
                };
                try {
                    if (!process.Start()) throw new InvalidOperationException("CHILD_START_FAILED");
                    var stdout = read(process.StandardOutput);
                    var stderr = read(process.StandardError);
                    var control = source == null ? Task.CompletedTask : Task.Run(async () => {
                        try {
                            while (Volatile.Read(ref source.DataRequests) < cancelAfterData) await Task.Delay(20, controlStop.Token);
                            if (!process.HasExited) {
                                await process.StandardInput.WriteAsync("cancel\n");
                                await process.StandardInput.FlushAsync();
                                result.ControlSent = true;
                            }
                        } catch (OperationCanceledException) {}
                    });
                    using (var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(60))) {
                        try { await process.WaitForExitAsync(timeout.Token); }
                        catch (OperationCanceledException) { result.Limited = true; kill(); }
                    }
                    await Task.WhenAll(stdout, stderr);
                    controlStop.Cancel();
                    await control;
                    result.Code = result.Limited ? 124 : process.ExitCode;
                    result.Output = output.ToString();
                    return result;
                } finally { kill(); Slots.Release(); }
            }
        });
    }
}

public sealed class Bloco55Loopback : IDisposable {
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
    public int DataDelayMilliseconds;
    public bool AllowOwnClientDisconnect;
    public int ExpectedDisconnects;
    public string ExpectedUpdatedAtFilter = "";
    public int MatchedUpdatedAtFilters;

    public Bloco55Loopback(string directory) : this(directory, 0) { }
    public Bloco55Loopback(string directory, int requestedPort) {
        if (requestedPort < 0 || requestedPort > 65535) throw new ArgumentException("LOOPBACK_PORT_INVALID");
        foreach (string template in new[] { "6908", "6389", "6399", "6906", "8656" })
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
                        if (Interlocked.Increment(ref Requests) > 200) throw new IOException("HTTP_CAMPAIGN_LIMIT");
                        string target = request[1].Substring("/api/analytics/reports/".Length);
                        string path = target.Split('?')[0];
                        if (!fixtures.ContainsKey(path)) throw new IOException("HTTP_RESOURCE_ALLOWLIST");
                        byte[] body = fixtures[path];
                        int status = 200;
                        if (path.EndsWith("/data", StringComparison.Ordinal)) {
                            if (!DateTime.TryParseExact(FixtureDate, "yyyy-MM-dd", System.Globalization.CultureInfo.InvariantCulture, System.Globalization.DateTimeStyles.None, out DateTime fixtureDate)
                                || fixtureDate < new DateTime(2018, 1, 1) || fixtureDate > new DateTime(2039, 12, 31)) throw new IOException("SYNTHETIC_DATE_LIMIT");
                            body = Encoding.UTF8.GetBytes(Encoding.UTF8.GetString(body).Replace("2024-01-01", FixtureDate));
                            var fixture = System.Text.Json.Nodes.JsonNode.Parse(body);
                            long keyOffset = (long)(fixtureDate - new DateTime(2024, 1, 1)).TotalDays * 100;
                            if (Scenario == "ROOT_CONFLICT" && path == "6399/data") fixture["data"][1]["operational_comments"] = "CONFLICTING_SYNTHETIC_VALUE";
                            foreach (var row in fixture["data"].AsArray()) {
                                foreach (string key in new[] { "id", "sequence_code", "corporation_sequence_number", "mft_pfs_pck_sequence_code", "pck_mik_mft_sequence_code", "fit_p_m_pck_sequence_code" }) {
                                    if (row[key] != null) row[key] = row[key].GetValue<long>() + keyOffset;
                                }
                            }
                            body = Encoding.UTF8.GetBytes(fixture.ToJsonString());
                            Interlocked.Increment(ref DataRequests);
                            if (DataDelayMilliseconds < 0 || DataDelayMilliseconds > 3000) throw new IOException("SYNTHETIC_DELAY_LIMIT");
                            if (DataDelayMilliseconds > 0) await Task.Delay(DataDelayMilliseconds, deadline.Token);
                            var uri = new Uri("http://127.0.0.1/" + target);
                            string page = null;
                            int updateMatches = 0;
                            foreach (string field in uri.Query.TrimStart('?').Split('&')) {
                                string[] pair = field.Split('=', 2);
                                if (pair.Length == 2 && Uri.UnescapeDataString(pair[0]) == "page") page = pair[1];
                                if (pair.Length == 2 && Uri.UnescapeDataString(pair[0]) == "search[scopes][by_updated_at]"
                                    && Uri.UnescapeDataString(pair[1]) == ExpectedUpdatedAtFilter) updateMatches++;
                            }
                            if (ExpectedUpdatedAtFilter.Length > 0) {
                                if (updateMatches != 1) throw new IOException("EXPECTED_INCREMENTAL_FILTER_MISSING");
                                Interlocked.Increment(ref MatchedUpdatedAtFilters);
                            }
                            if (page == null || !int.TryParse(page, out int ordinal) || ordinal < 1 || ordinal > 4) throw new IOException("HTTP_PAGE_LIMIT");
                            if (ordinal > 2) body = Encoding.UTF8.GetBytes("{\"data\":[]}"); else { using (var document = System.Text.Json.JsonDocument.Parse(body)) { var record = document.RootElement.GetProperty("data")[ordinal - 1].GetRawText(); body = Encoding.UTF8.GetBytes("{\"data\":[" + record + "]}"); } }
                            if (Scenario == "PARTIAL" && ordinal > 1) { status = 503; body = Encoding.UTF8.GetBytes("{}"); }
                            if (Scenario == "DRIFT" && ordinal == 1) body = Encoding.UTF8.GetBytes("{\"data\":[{\"id\":\"invalid-synthetic-type\"}]}");
                        }
                        byte[] response = Encoding.ASCII.GetBytes("HTTP/1.1 " + status + " Synthetic\r\nContent-Type: application/json\r\nContent-Length: " + body.Length + "\r\nConnection: close\r\n\r\n");
                        try {
                            await stream.WriteAsync(response, deadline.Token);
                            await stream.WriteAsync(body, deadline.Token);
                        } catch (IOException) when (AllowOwnClientDisconnect && Interlocked.Increment(ref ExpectedDisconnects) <= 6) {
                            // A cancelled owned client may close its response socket; no request is retried.
                        }
                    }
                }
            }
        } catch (OperationCanceledException) {}
          catch (Exception error) { Failure = error is IOException ? error.Message : "LOOPBACK_CONTROLLER_FAILED"; stop.Cancel(); }
    }
    public void Dispose() { stop.Cancel(); listener.Stop(); try { worker.GetAwaiter().GetResult(); } catch (Exception) {} stop.Dispose(); }
}
