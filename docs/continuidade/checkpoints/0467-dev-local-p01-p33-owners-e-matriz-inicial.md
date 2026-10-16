# Checkpoint0467 — DEV LOCAL P01–P33 em implementação

Anterior: [0466](0466-qualificacao-local-final-admitida-sem-extracao.md), SHA256 `d6ea877e5ffef0b515ac98fd5affd841fa6b508e14b2b9dd0f19e53fa017ccc8`.

Pedido HERMES_b77c5029cfbd: Lucas pediu concluir todo o modo DEV LOCAL e excluir apenas opcionais realmente inviáveis. Autoridade integral lida em `AppData/Local/hermes/integrations/maestri/requests/b77c5029cfbd.prompt.txt`. Estado: IMPLEMENTACAO_LOCAL_EM_CURSO; matriz é preparação, não entrega nem aceite integral.

AGENTS/STATES/RETOMADA/runbook e plano canônico lidos. CONTEXTO_GLOBAL ausente, não recriado. Maestri confirmou Codex/Supervisor ETL único; quatro builders existentes estavam ociosos/sem draft antes do despacho. Inventário e pins anteriores em `target/dev-local-b77c5029cfbd/root/`; alterações preexistentes preservadas. Nenhum agente novo, reset, Hermes callback, rotina ou efeito físico autorizado.

Decisão: comandos `local-sequence` e `local-data capture` existentes abrem `ColetaTemporalLaboratorySession`; não são adequados ao escopo sem SQL. Manter esses caminhos e defaults intactos; criar composição opt-in separada, explicitamente sintética, com adapters locais e processamento das regras existentes. Não usar fixture como prova de fornecedor, SQL ou produção.

Owners por arquivo em `target/dev-local-b77c5029cfbd/CONTRACT.md`: Fontes → `plataforma/fonte/devlocal/DevLocal*`; Banco → `plataforma/persistencia/devlocal/DevLocal*`; Regras → `bootstrap/DevLocalEngine*`; Runtime → `bootstrap/DevLocalMain*` e launcher novo. Codex exclusivo docs/CI/estado/matriz. APIs mínimas publicadas por três owners; integração/testes finais ainda pendentes.

Matriz programática inicial: 33 fases ordenadas, 48 unidades únicas, 60 ocorrências. 25 fases/38 unidades a implementar ou vincular causalmente; 8 fases/10 unidades excluídas do DEV por dependência exclusivamente externa/produtiva. Exclusões têm causa/impacto/trilha futura e não removem proteção essencial. Artefato `target/dev-local-b77c5029cfbd/root/matrix-preparation.json`; não muda plano histórico, 39/45 ou 67/115.

Limites: nenhum SQL/JDBC real, HTTP externo, captura/retencão real, provider/credencial real, download/install, produção, versão SQL16.0.1000.6, contas/identidades/permissões, serviços/listeners/certificados/trust/schema, V1/PM2/hold. STOP429/PAUSED e reservas físicas encerradas permanecem. Arquivos novos confinados; não apagar evidência/dados/ledgers/migrations.

Próximas: 1) integrar dataset/engine/ledger/CLI e corrigir causas locais; 2) executar smoke real, erros, encerramento e agregado final dos bytes novos; 3) consolidar matriz comprovada, estado/recibo e encerrar aguardando Lucas. Nada concluído só por documentação ou Maven histórico.
