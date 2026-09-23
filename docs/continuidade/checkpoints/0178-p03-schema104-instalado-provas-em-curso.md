# 0178 — P03: schema104 instalado; campanha funcional em execução

19/09/2026. Continuação de0177, mesmo pedido explícito de correção local.
Instalação p03-schema-install-01 CONFIRMED: somente V103/V104 novas, transação
DDL confirmada,244tabelas anteriores com contagens preservadas;246após DDL.
1816objetos/definições efetivos iguais ao catálogo qualificado. Readback inicial
recusou diferença de formatação sqlcmd; normalização de espaços confirmou nomes
e hashes idênticos. Não houve reaplicação. Schema não tem ledger SQL de migrations;
recibo técnico é o ledger externo do mecanismo existente.

Evidências em target/macrobloco-campanhas-integrais-20260915-01/:
p03-schema-{upgrade,baseline}-02/, p03-schema-install-01/ e
p03-corrections-20260919/{preinstall,installed-readback,preservation}.json.
V001–V102 e índice Git intactos; oito exclusões preexistentes preservadas.

p03-directed-01:27unit/zero falhas, erros ou skips; compilação e gates Maven PASS.
Schema validator e manifesto do pacote preparados para104; isso não executa P08.
Contraprovas físicas adicionais de MC, referência, replay e stale implementadas.

p03-campaign-sql-07 em execução pelo Invoke-Build.ps1: IntegralCampaignIT,
SequenceReferenceIT, SequenceRecompositionIT; unitários de sequência/medição e
SchemaFoundationSqlContractTest. Inventário, reserva e processo próprios no
diretório da tentativa. Não presumir PASS nem repetir sem ler result.json.
Teto3600s/1800seq/240etapa,heap512MiB,SQL≤60s, fonte sintética e rollback-only.

Próximas ações:
1. Observar07 até recibo, comparar XMLs/agregados e corrigir apenas causa nova.
2. Executar regressão relacional/Cotações/replay/isolamento e SequenceFailureIT
   atingidas, em nova admissão serial, sem saldo reutilizado.
3. Fechar apenas o recorte com provas; sincronizar estado/trilha/verificações
   e novo checkpoint. P03 inteiro e P04–P08 permanecem fora do aceite.

Recuperação: dados sintéticos revertidos pela sessão; schema persistente somente
por migration compensatória revisada se necessário. Sem fonte, credencial,
produção, commit/push ou alteração de índice.39/45 e67/115 preservados.
