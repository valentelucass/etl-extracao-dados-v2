using System;
using System.Data;
using System.IO;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Text;

namespace Jujuba.Retention {
    public sealed class ArchiveDigest : IDisposable {
        private readonly SHA256 rolling = SHA256.Create();
        private readonly SHA256 individual = SHA256.Create();
        private static readonly char[] Hex = "0123456789ABCDEF".ToCharArray();
        private readonly byte[] ascii = new byte[64];
        public long Lines { get; private set; }
        public void Append(string line) {
            byte[] digest = individual.ComputeHash(Encoding.Unicode.GetBytes(line));
            for (int i = 0; i < digest.Length; i++) {
                ascii[i*2] = (byte)Hex[digest[i] >> 4];
                ascii[i*2+1] = (byte)Hex[digest[i] & 15];
            }
            rolling.TransformBlock(ascii, 0, ascii.Length, ascii, 0);
            Lines++;
        }
        public string Finish() {
            if (Lines == 0) throw new InvalidDataException("Arquivo vazio.");
            rolling.TransformFinalBlock(new byte[0],0,0);
            return BitConverter.ToString(rolling.Hash).Replace("-","");
        }
        public void Dispose() { individual.Dispose(); rolling.Dispose(); }
    }
    public static class Archive {
        private static readonly UTF8Encoding Utf8 = new UTF8Encoding(false,true);
        public static string Write(IDataReader rows, string path) {
            using (var file = new FileStream(path, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            using (var digest = new ArchiveDigest()) {
                using (var gzip = new GZipStream(file,CompressionLevel.Fastest,true))
                using (var writer = new StreamWriter(gzip,Utf8,65536)) {
                    writer.NewLine = "\n";
                    while (rows.Read()) {
                        string line = rows.GetString(0);
                        if (line.IndexOf('\r') >= 0 || line.IndexOf('\n') >= 0)
                            throw new InvalidDataException("Linha JSON invalida.");
                        writer.WriteLine(line);
                        digest.Append(line);
                    }
                }
                file.Flush(true);
                return digest.Finish();
            }
        }
        public static string Verify(string path) {
            return ReadBatches(path, null);
        }
        public static string ReadBatches(string path, Action<string> consume) {
            using (var file = File.OpenRead(path))
            using (var gzip = new GZipStream(file,CompressionMode.Decompress))
            using (var reader = new StreamReader(gzip,Utf8,false,65536))
            using (var digest = new ArchiveDigest()) {
                var batch = consume == null ? null : new StringBuilder("[");
                string line;
                while ((line=reader.ReadLine()) != null) {
                    digest.Append(line);
                    if (batch == null) continue;
                    if (batch.Length > 1) batch.Append(',');
                    batch.Append(line);
                    if (batch.Length >= 262144) {
                        consume(batch.Append(']').ToString());
                        batch.Clear().Append('[');
                    }
                }
                if (batch != null && batch.Length > 1) consume(batch.Append(']').ToString());
                return digest.Finish();
            }
        }
    }
}
