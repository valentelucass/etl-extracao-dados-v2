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

public sealed class Bloco60ChildResult {
    public int Code;
    public string Output = "";
    public bool Limited;
    public bool KilledByController;
    public bool ControlSent; public int ProcessId;
}

public static class Bloco60Child {
    private static readonly SemaphoreSlim Slots = new SemaphoreSlim(2, 2);
    private static readonly System.Collections.Concurrent.ConcurrentDictionary<int, Process> Owned = new System.Collections.Concurrent.ConcurrentDictionary<int, Process>();
    public static int[] ProcessIds { get { return new System.Collections.Generic.List<int>(Owned.Keys).ToArray(); } }
    public static void StopOwned() {
        foreach (var process in Owned.Values) { try { if (!process.HasExited) process.Kill(true); } catch (InvalidOperationException) {} }
    }
    public static Task<Bloco60ChildResult> Run(ProcessStartInfo start) {
        return Run(start, null, 0, null);
    }
    public static Task<Bloco60ChildResult> Run(ProcessStartInfo start, Bloco60Loopback source, int cancelAfterData, string killMarker) {
        if (String.IsNullOrEmpty(killMarker)) killMarker = null;
        if (source != null && (cancelAfterData < 1 || cancelAfterData > 1024 || !start.RedirectStandardInput)) throw new InvalidOperationException("OWN_CONTROL_INPUT_REQUIRED");
        if (killMarker != null && killMarker != "RUNTIME_OBSERVATION reason=NOT_FOUND" && killMarker != "RUNTIME_SQL_OBSERVATION state=PUBLISHED") throw new InvalidOperationException("OWN_KILL_MARKER_ALLOWLIST");
        if (!Slots.Wait(0)) throw new InvalidOperationException("TWO_CHILD_LIMIT");
        return Task.Run(async () => {
            using (var process = new Process { StartInfo = start }) {
                var result = new Bloco60ChildResult();
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
                    result.ProcessId = process.Id; Owned.TryAdd(process.Id, process); var stdout = read(process.StandardOutput);
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
                } finally { kill(); if (result.ProcessId > 0) Owned.TryRemove(result.ProcessId, out _); Slots.Release(); }
            }
        });
    }
}

// Synthetic source shared by the six verticals. No upstream socket or payload logging.
public sealed class Bloco60Loopback : IDisposable {
    private readonly TcpListener listener;
    private readonly CancellationTokenSource stop = new CancellationTokenSource(TimeSpan.FromMinutes(60));
    private readonly Task worker;
    private readonly Dictionary<string, byte[]> fixtures = new Dictionary<string, byte[]>();
    public readonly string Token = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
    public readonly int Port;
    public int Requests, DataRequests, GraphQlRequests, FirstPages, Nodes, ExpectedDisconnects;
    public long ResponseBytes, RequestBytes;
    public string Scenario = "NORMAL", FixtureDate = "2033-06-01", Failure = "", KeyGroup = "B60_SYNTHETIC";
    public int DataDelayMilliseconds;
    public bool AllowOwnClientDisconnect;
    public Bloco60Loopback(string directory, int port) {
        if (port != 62160 && port != 0) throw new ArgumentException("OWN_LOOPBACK_PORT_REQUIRED");
        foreach (string template in new[] { "6908", "6389", "6399", "6906", "8656" })
            foreach (string operation in new[] { "info", "data" }) {
                byte[] value = File.ReadAllBytes(Path.Combine(directory, template + "-" + operation + ".json"));
                if (value.Length > 1048576) throw new InvalidOperationException("FIXTURE_RESPONSE_LIMIT");
                fixtures.Add(template + "/" + operation, value);
            }
        listener = new TcpListener(IPAddress.Loopback, port); listener.Start(2);
        Port = ((IPEndPoint)listener.LocalEndpoint).Port;
        worker = Task.Run(Serve);
    }
    private async Task Serve() {
        try {
            while (!stop.IsCancellationRequested) {
                using var client = await listener.AcceptTcpClientAsync(stop.Token);
                // Each accepted request owns its scenario, including expected cancellation.
                bool allowOwnClientDisconnect = AllowOwnClientDisconnect;
                int dataDelayMilliseconds = DataDelayMilliseconds;
                string fixtureDate = FixtureDate, keyGroup = KeyGroup, scenario = Scenario;
                using var stream = client.GetStream();
                using var deadline = CancellationTokenSource.CreateLinkedTokenSource(stop.Token);
                deadline.CancelAfter(TimeSpan.FromSeconds(30));
                var header = new List<byte>(4096); var one = new byte[1];
                while (true) {
                    if (header.Count >= 8192) throw new IOException("HTTP_HEADER_LIMIT");
                    if (await stream.ReadAsync(one, deadline.Token) != 1) throw new IOException("HTTP_HEADER_EOF");
                    header.Add(one[0]); int n = header.Count;
                    if (n >= 4 && header[n-4] == 13 && header[n-3] == 10 && header[n-2] == 13 && header[n-1] == 10) break;
                }
                string[] lines = Encoding.ASCII.GetString(header.ToArray()).Split(new[] { "\r\n" }, StringSplitOptions.None);
                string[] request = lines[0].Split(' ');
                bool authorized = false; int length = 0; int lengths = 0;
                foreach (string line in lines) {
                    if (line.Equals("Authorization: Bearer " + Token, StringComparison.OrdinalIgnoreCase)) authorized = true;
                    if (line.StartsWith("Transfer-Encoding:", StringComparison.OrdinalIgnoreCase)) throw new IOException("HTTP_TRANSFER_ENCODING_REFUSED");
                    if (line.StartsWith("Content-Length:", StringComparison.OrdinalIgnoreCase)) {
                        if (++lengths != 1 || !int.TryParse(line.Substring(15).Trim(), out length) || length < 0 || length > 8192) throw new IOException("HTTP_BODY_LIMIT");
                    }
                }
                if (!authorized) throw new IOException("SYNTHETIC_TOKEN_REQUIRED");
                if (Interlocked.Increment(ref Requests) > 366) throw new IOException("HTTP_CAMPAIGN_LIMIT");
                byte[] input = new byte[length]; int read = 0;
                while (read < length) { int n = await stream.ReadAsync(input.AsMemory(read), deadline.Token); if (n == 0) throw new IOException("HTTP_BODY_EOF"); read += n; }
                if (Interlocked.Add(ref RequestBytes, header.Count + length) > 8369356) throw new IOException("HTTP_REQUEST_BYTES_LIMIT");
                int status = 200; byte[] body;
                if (request.Length != 3) throw new IOException("HTTP_REQUEST_SHAPE");
                if (request[0] == "POST" && request[1] == "/graphql") {
                    Interlocked.Increment(ref GraphQlRequests); Interlocked.Increment(ref DataRequests);
                    var query = System.Text.Json.Nodes.JsonNode.Parse(input);
                    if (query["operationName"]?.GetValue<string>() != "V2UsersSnapshot"
                        || !query["query"].GetValue<string>().StartsWith("query V2UsersSnapshot(", StringComparison.Ordinal)
                        || query["query"].GetValue<string>().Contains("mutation", StringComparison.OrdinalIgnoreCase)
                        || query["variables"]["first"].GetValue<int>() != 20
                        || query["variables"]["params"].AsObject().Count != 1
                        || query["variables"]["params"]["enabled"].GetValue<bool>() != true) throw new IOException("GRAPHQL_STATIC_OPERATION_REQUIRED");
                    string after = query["variables"]["after"]?.GetValue<string>();
                    bool first = after == null;
                    if (!first && after != "B60_SECOND_PAGE") throw new IOException("GRAPHQL_CURSOR_SEQUENCE");
                    if (first) Interlocked.Increment(ref FirstPages);
                    if (!System.Text.RegularExpressions.Regex.IsMatch(keyGroup, "^B60_[A-Z0-9_]{1,40}$")) throw new IOException("SYNTHETIC_GROUP_REQUIRED");
                    string name = scenario == "UPDATE" ? "SYNTHETIC_CHANGED" : "SYNTHETIC_INITIAL";
                    string id = keyGroup + (first ? "_1" : "_2");
                    string node = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
                    if (scenario == "INVALID_NODE") node = "{\"id\":null,\"name\":\"SYNTHETIC\"}";
                    if (scenario == "CONFLICT" && !first) node = "{\"id\":\"" + keyGroup + "_1\",\"name\":\"CONFLICT\"}";
                    bool next = first && scenario != "ONE_PAGE";
                    body = Encoding.UTF8.GetBytes("{\"data\":{\"individual\":{\"edges\":[{\"node\":" + node + "}],\"pageInfo\":{\"hasNextPage\":" + (next ? "true" : "false") + ",\"endCursor\":" + (next ? "\"B60_SECOND_PAGE\"" : "null") + "}}}}");
                    if (scenario == "PARTIAL" && !first) { status = 503; body = Encoding.UTF8.GetBytes("{}"); }
                    if (scenario == "NULL_TERMINAL") body = Encoding.UTF8.GetBytes("{\"data\":{\"individual\":{\"edges\":[],\"pageInfo\":{\"hasNextPage\":null,\"endCursor\":null}}}}");
                    if (status == 200 && scenario != "NULL_TERMINAL" && Interlocked.Increment(ref Nodes) > 788) throw new IOException("CAMPAIGN_NODE_LIMIT");
                } else if (request[0] == "GET" && request[1].StartsWith("/api/analytics/reports/", StringComparison.Ordinal) && length == 0) {
                    string target = request[1].Substring("/api/analytics/reports/".Length), path = target.Split('?')[0];
                    if (!fixtures.TryGetValue(path, out body)) throw new IOException("DATAEXPORT_TEMPLATE_ALLOWLIST");
                    if (path.EndsWith("/data", StringComparison.Ordinal)) {
                        Interlocked.Increment(ref DataRequests);
                        if (!DateTime.TryParseExact(fixtureDate, "yyyy-MM-dd", System.Globalization.CultureInfo.InvariantCulture, System.Globalization.DateTimeStyles.None, out DateTime date)
                            || date.Year != 2033) throw new IOException("SYNTHETIC_DATE_REQUIRED");
                        var data = System.Text.Json.Nodes.JsonNode.Parse(Encoding.UTF8.GetString(body).Replace("2024-01-01", fixtureDate));
                        foreach (var row in data["data"].AsArray()) foreach (string key in new[] { "id", "sequence_code", "corporation_sequence_number", "mft_pfs_pck_sequence_code", "pck_mik_mft_sequence_code", "fit_p_m_pck_sequence_code" })
                            if (row[key] != null) row[key] = row[key].GetValue<long>() + 9000000;
                        int page = 0;
                        foreach (string part in new Uri("http://127.0.0.1/" + target).Query.TrimStart('?').Split('&')) {
                            string[] pair = part.Split('=', 2); if (pair.Length == 2 && Uri.UnescapeDataString(pair[0]) == "page") int.TryParse(pair[1], out page);
                        }
                        if (page < 1 || page > 4) throw new IOException("DATAEXPORT_PAGE_LIMIT");
                        if (page == 1) Interlocked.Increment(ref FirstPages);
                        body = Encoding.UTF8.GetBytes(page > 2 ? "{\"data\":[]}" : "{\"data\":[" + data["data"][page-1].ToJsonString() + "]}");
                    }
                } else throw new IOException("HTTP_ALLOWLIST");
                if (dataDelayMilliseconds < 0 || dataDelayMilliseconds > 3000) throw new IOException("DELAY_LIMIT");
                if (dataDelayMilliseconds > 0) await Task.Delay(dataDelayMilliseconds, deadline.Token);
                if (body.Length > 1048576 || Interlocked.Add(ref ResponseBytes, body.Length) > 16766078) throw new IOException("RESPONSE_BYTE_LIMIT");
                byte[] response = Encoding.ASCII.GetBytes("HTTP/1.1 " + status + " Synthetic\r\nContent-Type: application/json\r\nContent-Length: " + body.Length + "\r\nConnection: close\r\n\r\n");
                try { await stream.WriteAsync(response, deadline.Token); await stream.WriteAsync(body, deadline.Token); }
                catch (IOException) when (allowOwnClientDisconnect && Interlocked.Increment(ref ExpectedDisconnects) <= 8) { }
            }
        } catch (OperationCanceledException) when (stop.IsCancellationRequested) { }
          catch (Exception error) { Failure = error is IOException ? error.Message : "LOOPBACK_CONTROLLER_FAILED"; stop.Cancel(); }
    }
    public void Dispose() { stop.Cancel(); listener.Stop(); worker.GetAwaiter().GetResult(); stop.Dispose(); }
}
