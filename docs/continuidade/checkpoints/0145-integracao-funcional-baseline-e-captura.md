# Checkpoint 0145 — integração funcional: baseline e captura

EM_EXECUCAO A–N, 2026-09-14T14:49:02.111Z. Objetivo integral permanece o pedido adotado em target/macrobloco-integracao-funcional-20260914-01/PROMPT-MACROBLOCO-INTEGRACAO-FUNCIONAL-E-FECHAMENTO-LOCAL.md. Não encerrar parcial, não pedir continue/permissão, não usar subagentes.

Anterior: docs/continuidade/checkpoints/0144-fechamento-de-bytes-e-verificacao-final.md; SHA256 ac52e2dcec5e5115ad1b228b33123eb7ce396ca11fd7cf7e2e1ef03d5fd58bec.

Baseline:3190 arquivos canônicos e9672pins conferidos, zero divergência; baseline-result.json e inventory-before.json/snapshots em target/macrobloco-integracao-funcional-20260914-01. Preservados todos os deltas preexistentes; HEAD não é baseline. Construção39/45 eaceites67/115 sem mudança.

Autorização: instrução explícita14/09/2026 adota A–N. Só alterações V2 reversíveis, Java17/Maven offline; SQL quando reservado somente localhost/ETL_SISTEMA_V2_SHADOW Windows integrado e duas travas, rollback obrigatório. SemDDL/COMMIT de domínio/fontes reais/env/segredos/rede de negócio/produção/V1/dashboard/serviço/agendamento/git remoto/commit/push. Tetos por caso preservados, heap512MiB/log16MiB, verify60min. Cada tentativa nova deve reservar antes; desconhecido exige reconciliação própria. NenhumSQL executado até aqui.

Lista fechada inicial: docs/catalogos/macrobloco-integracao-funcional/obrigacoes-locais.json. As45unidades/2437campos anteriores são baseline, não nova contagem. V2-034a=MANTER confirmado no STATES original, apesar da conclusão genérica incorreta da auditoria0144. Default Raster fora da primeira onda preservado.

B: RED b-red-01 executado offline em snapshot;1teste/1falha esperada/0erro/0skip: LocalExpansionRuntime exigia ExpansionSyntheticSource concretamente. Resultado observado e preservado. Implementação ainda NÃO QUALIFICADA: ExpansionCaptureSource, ExpansionLocalContract e ExpansionPageSource separam contrato e transporte do adapter de fixture; ExpansionArtifact lê manifesto local/páginas limitadas por bytes/hash/escopo; ExpansionCharacterizer usa mapeadores efetivos; LocalDataLaboratoryMain caracteriza/captura somente modo local rollback. AnalyticExpansionSources é injetado em AnalyticExpansionCapture e AnalyticScenarioRuntime; consumidor empacotado de cenário completo ainda pendente. Sem alegação de fornecedor/paridade nominal.

Tentativa b-green-01: Directed, QualificationArtifactInputTest,900s,512MiB,16MiB; runner próprio em target/macrobloco-integracao-funcional-20260914-01/Invoke-Build.ps1 cria cópia isolada e reserva/inputs/process/result. Consultar result/process/logs antes de repetir. Runner copia formatação apenas dos arquivos alterados da rodada que ainda correspondam ao snapshot: não editar esses Java durante a execução.

Próximas ações:
1. Reconciliar b-green-01, corrigir compilação/testes se necessário; implementar provasJDBC duas entradas porCAP/FAT/INV/SIN e contraprovas na mesma cadeia.
2. Integrar artefatos de cenário/oráculos ao pacote, caracterização19contratos/cinco fatos, Raster explícito e sweep/preview33responsabilidades, sem fixtures no núcleo novo.
3. Concluir H–N: rastreabilidade595links/2437campos, robustez/duas escalas,45unidades, verify integral423identidades antigas/quatro skips, pacote extraído/reprodução, revisão separada, sucessão/diffs aplicados/selos/readback. Não reexecutar autores one-shot.

G01–G08 externos só bloqueiam parcelas demonstradas; nenhuma obrigação local deste macrobloco está encerrada só por este checkpoint. Relatório final/selo ainda inexistentes. Continuação autônoma obrigatória até cumprir todo trabalho local executável.
