# Checkpoint 0294 — P10/G02, Supervisor/CLI offline; JaCoCo aberto — 25/09/2026

## Autoridade, alvo e limites

- Anterior: [0293](0293-p10-g02-ranking-controle-puro-e-clean-verify-aberto.md), SHA-256 `f662a1842a896afc30cc90b7839243530861876048995f49d540e41f183769ef`.
- Instrução: harness offline coeso para admissão, falha, retomada e conclusão observáveis; comparar XML por classe, isolar SQL apenas com fluxo/pin/check shadow, manter 80/60, G02 e IT físico abertos.
- Base local/remota: `b9dac416737ac69c98d0e63715411b7c62fa03f7`. Efeito desta unidade: somente teste offline e continuidade; nenhum código produtivo/POM/manifesto mudou. Espelho/logs em `target/ci-p10-20260925-01/`, índice real e backups preservados. Recuperação: revisar/reverter o teste novo; nenhum banco, credencial, rede CI, push, merge ou deploy foi tocado.

## Evidência observada

| Critério | Camada | Resultado e limite |
| --- | --- | --- |
| Harness de transição | JAR sintético, CLI externa e journal local | `status` inicial exit 2; reserva sem processo faz `resume` parar sem novo worker ou evento; fixture selada de falha gera `ROLLBACK`/`TERMINAL` e `campaign-result.json` `FAILED`/exit 2; `status`/`resume` repetidos são estáveis; log alterado reprova `QUAL_PROCESS_EVIDENCE_CHAIN`. Processo/recibo/reconciliação são **sintéticos**: não provam rollback SQL. |
| Falha focal corrigida | Teste/controle | Primeiro alvo temporário violou `QUAL_CONTROL_ROOT`; reposicionado como irmão do payload sob `target`, mantendo a guarda. Repetição focal passou, duas ITs offline sem falha/erro. |
| Delta por classe | XML limpo v22→v23 | `QualificationSupervisor`: 123→175/431 linhas e 44→70/240 ramos; ganho 52/26. `QualificationLaboratoryMain` ficou 30/126 linhas e 15/62 ramos porque o processo JAR externo não tem JaCoCo, embora sua saída/exit sejam testados. |
| Build integral v23 | Espelho novo, JDK 17, heap 512 MiB | `clean verify` exit 1 só JaCoCo `bootstrap`: 2.313 Surefire + duas ITs Failsafe offline, zero falhas/erros, cinco skips históricos. Pós-exclusão 3.639/6.094 linhas (0,597), 1.590/3.016 ramos (0,527), abaixo de 80/60. |
| Segurança | Espelho Git indexado só nele | 4.002 arquivos; scanner PASS 4.002 candidatos/4.001 textos/um binário, zero achados/não inspecionados; Gitleaks 8.29.1 zero achados. |

Shadow IT físico **não executado**: `V2_SHADOW_JDBC_URL` ausente. O check shadow mantém os limiares e as travas localhost/`ETL_SISTEMA_V2_SHADOW`, Windows integrado, sintéticos e rollback. G02 continua **aberto** sem `verify` verde, SHA/checks remotos verdes ou owner nominal. Sem publicação.

## Decisão e retomada

O harness produziu comportamento observável e 52/26 de ganho, mas ainda faltam 1.237 linhas e 220 ramos sob o denominador atual. Expandir apenas variantes de jornal para cobertura seria artificial. O próximo desenho separa execução física concreta de `runChild`/`reconcile` e dos runtimes/oráculos mistos, mantendo Supervisor e CLI no Ubuntu; cada classe SQL precisa de fluxo, pin fail-closed e check shadow. A cobertura de processo CLI externo só pode ser alegada se instrumentada explicitamente.

1. Inspecionar `QualificationSupervisor` v23 por método: preservar `status`/recibo/verificações de arquivo no Ubuntu e separar somente a cadeia de sessão/worker físico; testar decisões puras com saídas/estado observáveis.
2. Classificar os runtimes/oráculos seguintes por método e consumidor, mover apenas corpo JDBC para shadow com pin e mutante; repetir `clean verify` fresco sem reduzir 80/60 ou 512 MiB.
3. Repetir scanner/Gitleaks no espelho dos bytes finais; sincronizar `STATES.md` antes do próximo checkpoint/trilha.
