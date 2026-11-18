using System;
using System.ComponentModel;
using System.Diagnostics;
using System.Runtime.InteropServices;

// Adapted from the existing ProjetosMemory.JobRunner native layout. No process is created here.
// Curl has no URL/config until stdin; assignment and readback happen before any private input.
namespace EslIndependentExport {
    // DETACHED_PROCESS avoids the additional console host observed with CREATE_NO_WINDOW.
    // Start suspended, inherit only three pipe handles, retain the enclosing Original job,
    // and resume only after the unchanged single-process/128MiB child job is attached.
    public sealed class DetachedOwnedCurl : IDisposable {
        [StructLayout(LayoutKind.Sequential)] struct Security { public int Length; public IntPtr Descriptor; public int Inherit; }
        [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)] struct Startup {
            public int Size; public string Reserved, Desktop, Title;
            public uint X,Y,XSize,YSize,XChars,YChars,Fill,Flags;
            public ushort Show, ReservedSize; public IntPtr ReservedBytes, Input, Output, Error;
        }
        [StructLayout(LayoutKind.Sequential)] struct StartupEx { public Startup Startup; public IntPtr Attributes; }
        [StructLayout(LayoutKind.Sequential)] struct Created { public IntPtr Process,Thread; public uint Pid,Tid; }
        [DllImport("kernel32.dll",SetLastError=true)] static extern bool CreatePipe(out IntPtr read,out IntPtr write,ref Security security,uint bytes);
        [DllImport("kernel32.dll",SetLastError=true)] static extern bool SetHandleInformation(IntPtr handle,uint mask,uint flags);
        [DllImport("kernel32.dll",SetLastError=true)] static extern bool InitializeProcThreadAttributeList(IntPtr list,int count,int flags,ref IntPtr size);
        [DllImport("kernel32.dll",SetLastError=true)] static extern bool UpdateProcThreadAttribute(IntPtr list,uint flags,IntPtr attribute,IntPtr value,IntPtr size,IntPtr previous,IntPtr returned);
        [DllImport("kernel32.dll")] static extern void DeleteProcThreadAttributeList(IntPtr list);
        [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern bool CreateProcessW(string application,System.Text.StringBuilder command,IntPtr processSecurity,IntPtr threadSecurity,bool inherit,uint flags,IntPtr environment,string directory,ref StartupEx startup,out Created created);
        [DllImport("kernel32.dll",SetLastError=true)] static extern uint ResumeThread(IntPtr thread);
        [DllImport("kernel32.dll",SetLastError=true)] static extern bool TerminateProcess(IntPtr process,uint code);
        [DllImport("kernel32.dll",SetLastError=true)] static extern uint WaitForSingleObject(IntPtr handle,uint milliseconds);
        [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
        private IntPtr thread;
        public Process Process {get;private set;}
        public System.IO.StreamWriter StandardInput {get;private set;}
        public System.IO.StreamReader StandardOutput {get;private set;}
        public System.IO.StreamReader StandardError {get;private set;}
        public int Id {get{return Process.Id;}}
        public DateTime StartTime {get{return Process.StartTime;}}
        public bool HasExited {get{return Process.HasExited;}}
        public int ExitCode {get{return Process.ExitCode;}}
        public bool WaitForExit(int milliseconds) {return Process.WaitForExit(milliseconds);}
        public void Kill(bool tree) {Process.Kill(tree);}
        private DetachedOwnedCurl() {}
        static void Need(bool ok) {if(!ok) throw new Win32Exception(Marshal.GetLastWin32Error());}
        static void Close(ref IntPtr handle) {if(handle!=IntPtr.Zero){CloseHandle(handle);handle=IntPtr.Zero;}}
        static string Quote(string value) {
            if(value==null || value.IndexOf('\0')>=0 || value.Length>4096) throw new InvalidOperationException("OWNED_CURL_ARGUMENT_BOUND");
            var result=new System.Text.StringBuilder("\"");int slashes=0;
            foreach(char c in value) {
                if(c=='\\'){slashes++;continue;}
                if(c=='\"'){result.Append('\\',slashes*2+1);result.Append(c);}
                else {result.Append('\\',slashes);result.Append(c);}
                slashes=0;
            }
            result.Append('\\',slashes*2);return result.Append('"').ToString();
        }
        public static DetachedOwnedCurl StartSuspended(ProcessStartInfo start) {
            if(start==null || !String.Equals(start.FileName,@"C:\Windows\System32\curl.exe",StringComparison.OrdinalIgnoreCase)
                || !System.IO.Path.IsPathFullyQualified(start.WorkingDirectory) || start.UseShellExecute
                || !start.RedirectStandardInput || !start.RedirectStandardOutput || !start.RedirectStandardError
                || start.ArgumentList.Count<3 || start.ArgumentList.Count>64 || start.Environment.Count!=2
                || start.Environment["SystemRoot"]!=@"C:\Windows" || start.Environment["WINDIR"]!=@"C:\Windows")
                throw new InvalidOperationException("OWNED_CURL_EXPLICIT_START");
            var command=new System.Text.StringBuilder(Quote(start.FileName));
            foreach(string arg in start.ArgumentList) command.Append(' ').Append(Quote(arg));
            if(command.Length>32766) throw new InvalidOperationException("OWNED_CURL_COMMAND_BOUND");
            IntPtr inputRead=IntPtr.Zero,inputWrite=IntPtr.Zero,outputRead=IntPtr.Zero,outputWrite=IntPtr.Zero;
            IntPtr errorRead=IntPtr.Zero,errorWrite=IntPtr.Zero,attributes=IntPtr.Zero,handles=IntPtr.Zero,environment=IntPtr.Zero;
            bool attributesReady=false,created=false,transferred=false;Created child=new Created();DetachedOwnedCurl owned=null;
            try {
                Security security=new Security{Length=Marshal.SizeOf(typeof(Security)),Inherit=1};
                Need(CreatePipe(out inputRead,out inputWrite,ref security,0));Need(SetHandleInformation(inputWrite,1,0));
                Need(CreatePipe(out outputRead,out outputWrite,ref security,0));Need(SetHandleInformation(outputRead,1,0));
                Need(CreatePipe(out errorRead,out errorWrite,ref security,0));Need(SetHandleInformation(errorRead,1,0));
                IntPtr size=IntPtr.Zero;
                bool unexpected=InitializeProcThreadAttributeList(IntPtr.Zero,1,0,ref size);
                if(unexpected || Marshal.GetLastWin32Error()!=122 || size.ToInt64()<=0 || size.ToInt64()>65536)
                    throw new InvalidOperationException("OWNED_CURL_ATTRIBUTE_SIZE");
                attributes=Marshal.AllocHGlobal(size);Need(InitializeProcThreadAttributeList(attributes,1,0,ref size));attributesReady=true;
                handles=Marshal.AllocHGlobal(3*IntPtr.Size);
                Marshal.WriteIntPtr(handles,0,inputRead);Marshal.WriteIntPtr(handles,IntPtr.Size,outputWrite);Marshal.WriteIntPtr(handles,2*IntPtr.Size,errorWrite);
                Need(UpdateProcThreadAttribute(attributes,0,new IntPtr(0x20002),handles,new IntPtr(3*IntPtr.Size),IntPtr.Zero,IntPtr.Zero));
                environment=Marshal.StringToHGlobalUni("SystemRoot=C:\\Windows\0WINDIR=C:\\Windows\0\0");
                StartupEx startup=new StartupEx();startup.Startup.Size=Marshal.SizeOf(typeof(StartupEx));
                startup.Startup.Flags=0x100;startup.Startup.Input=inputRead;startup.Startup.Output=outputWrite;startup.Startup.Error=errorWrite;startup.Attributes=attributes;
                Need(CreateProcessW(start.FileName,command,IntPtr.Zero,IntPtr.Zero,true,0x0008040C,environment,start.WorkingDirectory,ref startup,out child));created=true;
                Close(ref inputRead);Close(ref outputWrite);Close(ref errorWrite);
                owned=new DetachedOwnedCurl();owned.Process=Process.GetProcessById(checked((int)child.Pid));
                if(!OwnedCurlJob.CurrentOriginalContainsProcess(owned.Id)) throw new InvalidOperationException("OWNED_CURL_ENCLOSING_ORIGINAL");
                owned.StandardInput=new System.IO.StreamWriter(new System.IO.FileStream(new Microsoft.Win32.SafeHandles.SafeFileHandle(inputWrite,true),System.IO.FileAccess.Write,4096,false),new System.Text.UTF8Encoding(false),4096){AutoFlush=true};inputWrite=IntPtr.Zero;
                owned.StandardOutput=new System.IO.StreamReader(new System.IO.FileStream(new Microsoft.Win32.SafeHandles.SafeFileHandle(outputRead,true),System.IO.FileAccess.Read,4096,false),new System.Text.UTF8Encoding(false));outputRead=IntPtr.Zero;
                owned.StandardError=new System.IO.StreamReader(new System.IO.FileStream(new Microsoft.Win32.SafeHandles.SafeFileHandle(errorRead,true),System.IO.FileAccess.Read,4096,false),new System.Text.UTF8Encoding(false));errorRead=IntPtr.Zero;
                owned.thread=child.Thread;child.Thread=IntPtr.Zero;transferred=true;
                return owned;
            } catch {
                if(created){TerminateProcess(child.Process,125);WaitForSingleObject(child.Process,5000);}
                if(owned!=null) owned.Dispose();throw;
            } finally {
                Close(ref inputRead);Close(ref inputWrite);Close(ref outputRead);Close(ref outputWrite);Close(ref errorRead);Close(ref errorWrite);
                Close(ref child.Process);if(!transferred) Close(ref child.Thread);
                if(attributesReady) DeleteProcThreadAttributeList(attributes);
                if(attributes!=IntPtr.Zero) Marshal.FreeHGlobal(attributes);if(handles!=IntPtr.Zero) Marshal.FreeHGlobal(handles);if(environment!=IntPtr.Zero) Marshal.FreeHGlobal(environment);
            }
        }
        public void ResumeAfterAttachment(OwnedCurlJob job) {
            if(thread==IntPtr.Zero || job==null) throw new InvalidOperationException("OWNED_CURL_RESUME_ONCE");
            ulong[] facts=job.Observe();
            if(facts[0]!=(ulong)Id || facts[1]!=134217728 || facts[2]!=134217728 || facts[5]!=1 || facts[6]!=1)
                throw new InvalidOperationException("OWNED_CURL_PRE_RESUME_JOB");
            uint previous=ResumeThread(thread);if(previous!=1) throw new InvalidOperationException("OWNED_CURL_SUSPENDED_THREAD");
            Close(ref thread);
        }
        public void Dispose() {
            if(thread!=IntPtr.Zero && Process!=null){try{if(!Process.HasExited){Process.Kill(true);Process.WaitForExit(5000);}}catch{}}
            Close(ref thread);
            if(StandardInput!=null) StandardInput.Dispose();if(StandardOutput!=null) StandardOutput.Dispose();if(StandardError!=null) StandardError.Dispose();
            if(Process!=null){Process.Dispose();Process=null;}
        }
    }
    public sealed class OwnedCurlJob : IDisposable {
        [StructLayout(LayoutKind.Sequential)] struct BasicLimit {
            public long ProcessUserTime, JobUserTime; public uint Flags;
            public UIntPtr MinWorkingSet, MaxWorkingSet; public uint ActiveProcessLimit;
            public UIntPtr Affinity; public uint PriorityClass, SchedulingClass;
        }
        [StructLayout(LayoutKind.Sequential)] struct IoCounters {
            public ulong ReadOps, WriteOps, OtherOps, ReadBytes, WriteBytes, OtherBytes;
        }
        [StructLayout(LayoutKind.Sequential)] struct ExtendedLimit {
            public BasicLimit Basic; public IoCounters Io;
            public UIntPtr ProcessMemory, JobMemory, PeakProcessMemory, PeakJobMemory;
        }
        [StructLayout(LayoutKind.Sequential)] struct Accounting {
            public long UserTime, KernelTime, PeriodUser, PeriodKernel;
            public uint PageFaults, TotalProcesses, ActiveProcesses, TerminatedProcesses;
        }
        [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
        static extern IntPtr CreateJobObject(IntPtr attributes, string name);
        [DllImport("kernel32.dll", SetLastError=true)]
        static extern bool SetInformationJobObject(IntPtr job, int kind, ref ExtendedLimit limits, uint length);
        [DllImport("kernel32.dll", SetLastError=true)]
        static extern bool QueryInformationJobObject(IntPtr job, int kind, ref ExtendedLimit limits, uint length, IntPtr returned);
        [DllImport("kernel32.dll", SetLastError=true)]
        static extern bool QueryInformationJobObject(IntPtr job, int kind, ref Accounting accounting, uint length, IntPtr returned);
        [DllImport("kernel32.dll", SetLastError=true)]
        static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
        [DllImport("kernel32.dll", SetLastError=true)]
        static extern bool TerminateJobObject(IntPtr job, uint code);
        [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
        [DllImport("kernel32.dll", SetLastError=true)]
        static extern bool QueryInformationJobObject(IntPtr job, int kind, IntPtr buffer, uint length, IntPtr returned);
        [StructLayout(LayoutKind.Sequential)] struct ProcessBasicInformation {
            public IntPtr Reserved1, PebBase, Reserved2a, Reserved2b, ProcessId, ParentProcessId;
        }
        [DllImport("ntdll.dll")] static extern int NtQueryInformationProcess(
            IntPtr process, int kind, ref ProcessBasicInformation info, int length, out int returned);
        public static int ParentProcessId(Process process) {
            ProcessBasicInformation info=new ProcessBasicInformation();int returned;
            if(NtQueryInformationProcess(process.Handle,0,ref info,Marshal.SizeOf(typeof(ProcessBasicInformation)),out returned)!=0)
                throw new InvalidOperationException("OWNED_PROCESS_PARENT_QUERY");
            return checked((int)info.ParentProcessId.ToInt64());
        }
        public static bool CurrentOriginalContainsProcess(int processId) {
            const int maximum=128;IntPtr buffer=Marshal.AllocHGlobal(8+maximum*IntPtr.Size);
            try {
                Need(QueryInformationJobObject(IntPtr.Zero,3,buffer,(uint)(8+maximum*IntPtr.Size),IntPtr.Zero));
                int assigned=Marshal.ReadInt32(buffer,0),listed=Marshal.ReadInt32(buffer,4);
                if(assigned<1 || assigned>maximum || listed!=assigned)
                    throw new InvalidOperationException("OWNED_ORIGINAL_COMPLETE_PROCESS_LIST");
                bool found=false;
                for(int i=0;i<listed;i++) if(Marshal.ReadIntPtr(buffer,8+i*IntPtr.Size).ToInt64()==processId) found=true;
                return found;
            } finally { Marshal.FreeHGlobal(buffer); }
        }
        private IntPtr handle;
        private readonly int pid;
        private OwnedCurlJob(IntPtr handle, int pid) { this.handle=handle;this.pid=pid; }
        static void Need(bool ok) { if(!ok) throw new Win32Exception(Marshal.GetLastWin32Error()); }
        public static OwnedCurlJob AttachBeforeInput(Process process) {
            if(process==null || process.HasExited) throw new InvalidOperationException("OWNED_CURL_NOT_LIVE");
            IntPtr job=CreateJobObject(IntPtr.Zero,null);
            if(job==IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error());
            try {
                ExtendedLimit limits=new ExtendedLimit();
                limits.Basic.Flags=0x100|0x200|0x2000|0x8;
                limits.Basic.ActiveProcessLimit=1;
                limits.JobMemory=new UIntPtr(134217728);
                limits.ProcessMemory=new UIntPtr(134217728);
                Need(SetInformationJobObject(job,9,ref limits,(uint)Marshal.SizeOf(typeof(ExtendedLimit))));
                Need(AssignProcessToJobObject(job,process.Handle));
                ExtendedLimit actual=new ExtendedLimit();
                Need(QueryInformationJobObject(job,9,ref actual,(uint)Marshal.SizeOf(typeof(ExtendedLimit)),IntPtr.Zero));
                if(actual.ProcessMemory.ToUInt64()!=134217728 || actual.JobMemory.ToUInt64()!=134217728
                    || (actual.Basic.Flags & 0x2308)!=0x2308 || actual.Basic.ActiveProcessLimit!=1)
                    throw new InvalidOperationException("OWNED_CURL_JOB_LIMIT_READBACK");
                return new OwnedCurlJob(job,process.Id);
            } catch { TerminateJobObject(job,125);CloseHandle(job);throw; }
        }
        public ulong[] Observe() {
            ExtendedLimit limits=new ExtendedLimit();Accounting counts=new Accounting();
            Need(QueryInformationJobObject(handle,9,ref limits,(uint)Marshal.SizeOf(typeof(ExtendedLimit)),IntPtr.Zero));
            Need(QueryInformationJobObject(handle,1,ref counts,(uint)Marshal.SizeOf(typeof(Accounting)),IntPtr.Zero));
            return new ulong[]{(ulong)pid,limits.JobMemory.ToUInt64(),limits.ProcessMemory.ToUInt64(),
                limits.PeakJobMemory.ToUInt64(),limits.PeakProcessMemory.ToUInt64(),counts.ActiveProcesses,counts.TotalProcesses};
        }
        public void Stop() { if(handle!=IntPtr.Zero) Need(TerminateJobObject(handle,124)); }
        public void Dispose() { if(handle!=IntPtr.Zero){ CloseHandle(handle);handle=IntPtr.Zero; } }
    }
}
