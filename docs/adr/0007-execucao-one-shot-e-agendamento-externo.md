# ADR 0007 — Execução one-shot e agendamento externo

- Status: Aceito por V2-017; implementação em V2-018/V2-020/V2-022/V2-023
- Data: 2026-08-30

## Contexto

O legado combina fluxo completo, loops/daemon, scheduler interno de fatos, arquivos PID, launchers Windows e tarefas operacionais. Isso duplica estado, dificulta shutdown/recovery e permite que ausência de argumentos inicie carga.

## Decisão

O runtime V2 é CLI one-shot sob scheduler/supervisor externo.

- Ausência de argumentos mostra ajuda e sai sem efeito. Não existe default produtivo.
- Não haverá daemon/loop/PID file interno. Singleton, lease, heartbeat e stale recovery ficam no SQL Server.
- Comandos principais são tipados: executar entidade/partição/modo, `status`, `config validate`, `dry-run`, replay/backfill/sweep autorizados e fechamento mensal com mês explícito.
- `force-run` é separado, autorizado e auditado; não ignora lease, contrato, DQ ou write-fence silenciosamente.
- Shutdown é gracioso: não inicia novo lote, conclui/aborta o lote atual conforme protocolo e registra terminal explícito.
- Scripts Windows, se necessários, são launchers finos. Agendamento, retries de processo e calendário ficam no boundary operacional, sem embutir segredo.
- O pacote é JAR executável Java 17 reproduzível com checksum, SBOM e proveniência nos gates de release.

## Consequências

- Os quatro comandos daemon, loop de 30 minutos, scheduler interno de fatos, PID scan e menu monolítico são retirados/substituídos conforme o inventário V2-017.
- Sweep e materialização são jobs one-shot distintos, com modos/janelas/checkpoints diferentes.
- Status operacional vem do control plane durável, não de processo, arquivo ou texto de log.
