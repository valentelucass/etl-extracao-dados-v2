using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

// At most two owned sqlcmd processes; no shell and no process-name based termination.
public sealed class Bloco60AdminJob : IDisposable {
    private static readonly SemaphoreSlim Slots = new SemaphoreSlim(2, 2);
    private readonly Process process;
    public readonly ManualResetEventSlim Ready = new ManualResetEventSlim(false);
    public Task<Bloco60AdminResult> Completion { get; private set; }
    public int ProcessId { get; private set; }
    public Bloco60AdminJob(ProcessStartInfo start) {
        if (!Slots.Wait(0)) throw new InvalidOperationException("B60_TWO_ADMIN_PROCESS_LIMIT");
        process = new Process { StartInfo = start };
        try { if (!process.Start()) throw new InvalidOperationException("B60_SQLCMD_START"); ProcessId = process.Id; }
        catch { process.Dispose(); Slots.Release(); throw; }
        Completion = Run();
    }
    private async Task<Bloco60AdminResult> Run() {
        var result = new Bloco60AdminResult(); var output = new StringBuilder(); var gate = new object(); int bytes = 0;
        Func<StreamReader, Task> read = async reader => {
            var buffer = new char[2048]; int count;
            while ((count = await reader.ReadAsync(buffer, 0, buffer.Length)) > 0) {
                lock (gate) {
                    bytes += Encoding.UTF8.GetByteCount(buffer, 0, count);
                    if (bytes > 1048576) { result.Limited = true; Kill(); return; }
                    output.Append(buffer, 0, count);
                    if (output.ToString().Contains("B60_BARRIER_READY", StringComparison.Ordinal)) Ready.Set();
                }
            }
        };
        try {
            var stdout = read(process.StandardOutput); var stderr = read(process.StandardError);
            using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(45));
            try { await process.WaitForExitAsync(deadline.Token); }
            catch (OperationCanceledException) { result.Limited = true; Kill(); }
            await Task.WhenAll(stdout, stderr);
            result.Code = result.Limited ? 124 : process.ExitCode; result.Output = output.ToString();
            return result;
        } finally { Kill(); Slots.Release(); }
    }
    private void Kill() { try { if (!process.HasExited) process.Kill(true); } catch (InvalidOperationException) { } }
    public void Dispose() { Kill(); Completion.GetAwaiter().GetResult(); process.Dispose(); Ready.Dispose(); }
}
public sealed class Bloco60AdminResult { public int Code; public string Output = ""; public bool Limited; }
