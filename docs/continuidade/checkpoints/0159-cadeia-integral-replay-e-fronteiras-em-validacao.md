# 0159 — Replay e fronteiras integrais em validação

## Objetivo e autorização

**EM_EXECUCAO**, sem entrega parcial. O pedido adotado continua A–N integral em
`target/preparacao-macrobloco-cadeia-integral-20260914-01/`, com P01–P24 e as mesmas
45 unidades. Uma entrada do usuário e uma entrega final, sem perguntas/subagentes.
Predecessor entregue: 0154. Checkpoint de progresso anterior: 0158,
SHA256 `fd814051f695d0ba4ceba169b48d8f1b6096025762d48b45ebc8fd799cd6e1c4`.

Preservar 39/45, 67/115, V099 e macrobloco anterior. V2-041: sem segredos/API/V1
executada/produção/serviços/deploy/cutover. SQL apenas localhost/ETL_SISTEMA_V2_SHADOW,
Windows integrado, duas travas, migrations versionadas e provas com rollback.
Índice Git real intocado. Nenhuma aprovação pendente. Baseline privada: 3.349 arquivos.

## Estado observado

Rodada `target/macrobloco-cadeia-integral-20260914-01`; recibos individuais, inventários,
reservas, processos e logs permanecem preservados. V100–V102 já instaladas e conferidas;
não reaplicar. Novas migrations somente se houver divergência demonstrada.

| Tentativa | Evidência |
| --- | --- |
| physical-10 | A2/B24 completos: 11 fontes, cinco fatos, 19 SQL, 33 previews; cinco bordas sweep PASS. |
| physical-11 | Quatro falhas intermediárias PASS: cancelamento, deadline, lease e drift de página. |
| physical-12/14 | Cinco contraprovas independentes de oráculo PASS (valor, chave, cardinalidade, precisão e fato). |
| physical-13 | Unidade 2067, duas falhas de catálogos de verificação; corrigidas, sem execução IT. |
| physical-14 | Unidade 2067 sem falha/erro, quatro skips históricos. Inicial e replay da mesma revisão exatos; revisão posterior recusada pelo contrato de Fretes. |
| physical-15 | Nove IT, quatro falhas e dois erros, rollback confirmado. Mesmas chaves/datas em dois scopes PASS; Raster incompleto e retry físico MAT01 divergente preservando fatos/19 SQL PASS. |

Correções em validação na **physical-16** (PackagePhysical, 1800 s, heap512):

- Replay Raster seleciona revisão da fonte, distinta da revisão da operação. SQL10 compara
  os vinte eventos de cada ciclo; SCENARIO inclui as cardinalidades dos ciclos anteriores.
- Autoria avançada usa cte_created_at14Z, posterior ao13Z original, conforme FRE-02;
  updated_at não foi promovido a frescor. Valor independente143.25→166.375 e criado_em10Z→13Z.
- Scope trocado foi recusado tardiamente por DQ na15; requireIntegralContext agora confere
  run/grupo/origem/tenant/janela antes de criar ciclo ou capturar. Guarda ainda em prova.
- Referências estrangeiras/revisão incompatível recusam no binding53721, antes do preflight;
  TVP duplicada aborta toda a transação, exigindo conferência de zero run após a falha.
- Financial fault legado não altera fontes integrais; teste usa falha de snapshot de Coletas
  suportada. Não atribuir resultado sintético de fixture interna ao input explícito.
- Limites5/33/32 registrados no catálogo arquitetural e validados antes de cópias defensivas.
  Removidos overload monitoring(Cycle) e parâmetro manifestExecution sem consumidores.
- Trio RasterLoopbackTransport/Configuration/HttpBodyHandler movido byte-idêntico para
  src/test após busca de consumidores; scanner/contraprova acompanham o caminho exato.
  Primeira tentativa sem diretório destino falhou sem mover; estado reconciliado antes de repetir.
- Distribuição Java/config/schema exige102; leitor PowerShell conserva históricos98/99 e102.
  Invoke-IntegralJarProof.ps1 preparado, **não executado**; autor de exemplos A/B e pequeno
  artifact-case preparado. Pacote/JAR extraído ainda precisam de prova real.

## Retomada — até três ações

1. Reconciliar physical-16 pelo result/process/counts; corrigir replay/referências/escopos e
   concluir contraprovas/propagação restantes. Não repetir efeitos desconhecidos.
2. Construir pacote e exemplos pinados pelo hash do JAR; executar o JAR extraído em A2/B24,
   sucesso e recusa, supervisor existente quando aplicável; concluir revisão V1/11 famílias,
   classes, regressão16casos V099 e suíte final completa com quatro skips históricos.
3. Concluir sucessão M, matrizes A–N/45/P01–P24, revisão e G01–G08, scanner integral em
   índice privado, diff/overlay aplicados em cópia, pacote/código/provas, selo/readback e continuidade.

G01–G08 conservam apenas dependências externas comprovadas; não bloqueiam implementação
ou prova local independente. Encerrar somente depois de todo o escopo local autorizado.
