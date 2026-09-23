Set-StrictMode -Version Latest

function Enter-DataExportProbeGate {
    $mutex = $null
    try {
        $mutex = [System.Threading.Mutex]::new($false, 'Global\ETL_V2_DATAEXPORT_READONLY_PROBE')
        try {
            $acquired = $mutex.WaitOne(0)
        } catch [System.Threading.AbandonedMutexException] {
            # O processo anterior terminou sem liberar a trava; o sistema
            # operacional transferiu a posse para esta execucao.
            $acquired = $true
        }
        if (-not $acquired) {
            $mutex.Dispose()
            return $null
        }
        return $mutex
    } catch {
        if ($null -ne $mutex) { $mutex.Dispose() }
        throw 'Nao foi possivel reservar a trava local das sondas Data Export.'
    }
}

function Exit-DataExportProbeGate {
    param([AllowNull()][System.Threading.Mutex]$Gate)

    if ($null -eq $Gate) { return }
    try {
        $Gate.ReleaseMutex()
    } catch {
        # A finalizacao do processo tambem libera um mutex nomeado. Nao deixe
        # uma falha de limpeza disparar uma nova chamada externa.
    } finally {
        $Gate.Dispose()
    }
}
