package br.com.esl.etl.v2.plataforma.persistencia.controle;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Read only native command/start proof for the one actual parent; never logs command contents. */
final class C78DevRootActor {
    private static final String PWSH = "C:/Users/suporte/.codex/tools/powershell-7.6.6-v2/pwsh.exe";
    private static final String PROGRAM =
            """
$ErrorActionPreference='Stop'
try {
$request=[Console]::In.ReadLine()
if ($null -eq $request -or $request.Length -gt 8192) { throw 'LIMIT' }
$p=$request | ConvertFrom-Json
$source=@'
using System;
using System.IO;
using System.Runtime.InteropServices;
public static class GateParent {
 [StructLayout(LayoutKind.Sequential)] struct US { public ushort Length,MaximumLength; public IntPtr Buffer; }
 [DllImport("kernel32",SetLastError=true)] static extern IntPtr OpenProcess(uint access,bool inherit,uint pid);
 [DllImport("kernel32")] static extern bool GetProcessTimes(IntPtr h,out long c,out long e,out long k,out long u);
 [DllImport("kernel32")] static extern bool CloseHandle(IntPtr h);
 [DllImport("ntdll")] static extern int NtQueryInformationProcess(IntPtr h,int cls,IntPtr b,int size,out int need);
 [DllImport("shell32",CharSet=CharSet.Unicode)] static extern IntPtr CommandLineToArgvW(string line,out int argc);
 [DllImport("kernel32")] static extern IntPtr LocalFree(IntPtr h);
 public static bool Match(long pid,long startMillis,string[] expected) {
  if(pid<1 || pid>UInt32.MaxValue || expected.Length!=4) return false;
  IntPtr h=IntPtr.Zero,b=IntPtr.Zero,argv=IntPtr.Zero;int size=0;
  try {
   h=OpenProcess(0x1000,false,(uint)pid);if(h==IntPtr.Zero)return false;
   long c,e,k,u,after;if(!GetProcessTimes(h,out c,out e,out k,out u) || c/10000-11644473600000L!=startMillis)return false;
   int need;NtQueryInformationProcess(h,60,IntPtr.Zero,0,out need);
   if(need<Marshal.SizeOf(typeof(US)) || need>32768)return false;
   size=need;b=Marshal.AllocHGlobal(size);
   for(int i=0;i<size;i++)Marshal.WriteByte(b,i,0);
   if(NtQueryInformationProcess(h,60,b,size,out need)!=0 || need>size)return false;
   US s=(US)Marshal.PtrToStructure(b,typeof(US));
   long first=b.ToInt64(),position=s.Buffer.ToInt64();
   if(s.Length==0 || s.Length%2!=0 || s.Length>s.MaximumLength || s.MaximumLength>size
      || position<first || position>checked(first+size-s.Length))return false;
   string line=Marshal.PtrToStringUni(s.Buffer,s.Length/2);int count;
   argv=CommandLineToArgvW(line,out count);if(argv==IntPtr.Zero || count!=5)return false;
   for(int i=0;i<4;i++) {
    string actual=Marshal.PtrToStringUni(Marshal.ReadIntPtr(argv,(i+1)*IntPtr.Size));
    string left=i==2?actual:Path.GetFullPath(actual);
    string right=i==2?expected[i]:Path.GetFullPath(expected[i]);
    if(!String.Equals(left,right,i==2?StringComparison.Ordinal:StringComparison.OrdinalIgnoreCase))return false;
   }
   return GetProcessTimes(h,out after,out e,out k,out u) && after==c;
  } finally {
   if(argv!=IntPtr.Zero)LocalFree(argv);
   if(b!=IntPtr.Zero){for(int i=0;i<size;i++)Marshal.WriteByte(b,i,0);Marshal.FreeHGlobal(b);}
   if(h!=IntPtr.Zero)CloseHandle(h);
  }
 }
}
'@
Add-Type -TypeDefinition $source -ErrorAction Stop
if ([GateParent]::Match([long]$p.pid,[long]$p.startMillis,[string[]]$p.arguments)) {
 [Console]::Out.WriteLine('MATCH')
} else { [Console]::Out.WriteLine('REFUSE') }
} catch { [Console]::Out.WriteLine('REFUSE');exit 1 }
""";

    private C78DevRootActor() {}

    static void verify(
            final ProcessHandle parent,
            final Instant start,
            final List<String> arguments,
            final long milliseconds)
            throws IOException {
        if (!parent.isAlive() || arguments.size() != 4 || milliseconds < 1) {
            throw new IOException("ROOT_GATE_ACTOR_UNAVAILABLE");
        }
        final var request = new ObjectMapper().createObjectNode();
        request.put("pid", parent.pid()).put("startMillis", start.toEpochMilli());
        request.set("arguments", new ObjectMapper().valueToTree(arguments));
        final byte[] input = (request + "\n").getBytes(StandardCharsets.UTF_8);
        if (input.length > 8192) {
            throw new IOException("ROOT_GATE_ACTOR_LIMIT");
        }
        final String program =
                Base64.getEncoder().encodeToString(PROGRAM.getBytes(StandardCharsets.UTF_16LE));
        final Process helper =
                new ProcessBuilder(
                                PWSH, "-NoProfile", "-NonInteractive", "-EncodedCommand", program)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
        IOException failure = null;
        try {
            helper.getOutputStream().write(input);
            helper.getOutputStream().close();
            if (!helper.waitFor(Math.min(milliseconds, 10_000), TimeUnit.MILLISECONDS)) {
                throw new IOException("ROOT_GATE_ACTOR_DEADLINE");
            }
            final byte[] output = helper.getInputStream().readNBytes(16);
            if (helper.exitValue() != 0
                    || !new String(output, StandardCharsets.US_ASCII).strip().equals("MATCH")) {
                throw new IOException("ROOT_GATE_ACTOR_NOT_QUALIFIED_CONTROLLER");
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failure = new IOException("ROOT_GATE_ACTOR_INTERRUPTED", interrupted);
        } catch (final IOException caught) {
            failure = caught;
        } catch (final RuntimeException caught) {
            failure = new IOException("ROOT_GATE_ACTOR_BOUNDARY", caught);
        } finally {
            failure = closeHelper(helper, failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    static IOException closeHelper(final Process helper, final IOException primary) {
        IOException failure = primary;
        try {
            if (helper.isAlive()) {
                helper.destroyForcibly();
                if (!helper.waitFor(1, TimeUnit.SECONDS)) {
                    failure = retain(failure, new IOException("ROOT_GATE_ACTOR_CLOSE_UNCERTAIN"));
                }
            }
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failure =
                    retain(
                            failure,
                            new IOException("ROOT_GATE_ACTOR_CLOSE_INTERRUPTED", interrupted));
        } catch (final RuntimeException cleanup) {
            failure = retain(failure, new IOException("ROOT_GATE_ACTOR_TERMINATE_FAILED", cleanup));
        }
        try {
            helper.getInputStream().close();
        } catch (final IOException | RuntimeException cleanup) {
            failure = retain(failure, new IOException("ROOT_GATE_ACTOR_STDOUT_CLOSE", cleanup));
        }
        try {
            helper.getOutputStream().close();
        } catch (final IOException | RuntimeException cleanup) {
            failure = retain(failure, new IOException("ROOT_GATE_ACTOR_STDIN_CLOSE", cleanup));
        }
        try {
            helper.getErrorStream().close();
        } catch (final IOException | RuntimeException cleanup) {
            failure = retain(failure, new IOException("ROOT_GATE_ACTOR_STDERR_CLOSE", cleanup));
        }
        return failure;
    }

    private static IOException retain(final IOException primary, final IOException cleanup) {
        if (primary == null) {
            return cleanup;
        }
        primary.addSuppressed(cleanup);
        return primary;
    }
}
