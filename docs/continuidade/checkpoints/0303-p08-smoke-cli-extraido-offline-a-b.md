# Checkpoint 0303 — P08 CLI extraída A/B offline, físico pendente

## Identificação e autoridade

- Checkpoint: 0303, 2026-09-28 06:11 UTC, P08/P29 e continuidade P07/P10.
- Anterior: `docs/continuidade/checkpoints/0302-p07-p08-uac-pausado-midias-e-offline.md`, SHA-256 `98774DBF13778BCC371F0E96908549AB66F5D9277BC2E6006C47D6914EDA16B5`.
- Objetivo do usuário: testar o JAR extraído de P08 offline sem carregar DLL ou conectar SQL, mantendo pacote/lock e sem promover aceite falso; depois continuar a sombra local apenas quando o operador liberar UAC.
- Autoridade: `STATES.md` canônico, `AGENTS.md`, matriz e [mapa P01–P33](../qualificacao-p07-p33/mapa-p01-p33-20260927.md). Autorização da instância local permanece, mas o usuário mandou **não repetir UAC até o operador indicar prontidão**.
- Branch `main...origin/main`, deltas preexistentes e checkpoint 0302 preservados; máquina da sombra anterior não acessada. `../CONTEXTO_GLOBAL.md` segue ausente na busca segura anterior. Nenhum serviço, SQL, DDL, fonte real, push, merge, deploy ou cutover.

## Causa, alteração e limite

- O launcher P08 anterior inseria flags shadow e `java.library.path` em todo comando. O supervisor abre `QualificationSqlEvidence.master` para casos físicos; o pacote fixa DLL 12.8.2, enquanto o perfil shadow autorizado exige driver/DLL 12.8.1. Executar esse caminho antes de resolver o lock violaria o limite vigente.
- `scripts/validation/Invoke-Qualification.ps1` ganhou somente `config-validate`/`dry-run`: após verificar manifesto, chama `Main` com configuração exemplo pinada, classpath só do JAR, sem flags shadow ou caminho nativo; limpa `V2_*` herdadas e opções JVM externas apenas no processo offline. `New-QualificationPackage.ps1` inclui `config/application.example.properties`. O README separa o bloco puro e suspende os comandos físicos desta revisão. Os comandos físicos e o lock histórico permanecem sem mudança.
- Esses comandos reproduzem o alcance do checkpoint 0281: `LOCAL_SHADOW`, Data Export/auditoria desligadas, deny-all; `RuntimeCompositionRoot.validateConfiguration()` não compõe cliente ou conexão. P08 integral requer supervisor/worker, A/B físicas, rollback, guardas completos, scanner/sucessão/selo/readback nos bytes pertinentes.

## Execução e evidência

| Parcela | Camada/limite | Observado | Recibo |
| --- | --- | --- | --- |
| Fonte/CI | 1.204 Java/POM atuais comparados por SHA ao espelho v66 | Zero diferenças; `clean verify` v66 prévio: 2.348 Surefire, seis IT offline, JaCoCo bootstrap 80,34%/68,07%, limites intactos. Alteração v71 é script/README. | `target/ci-p10-20260927-01/compare-java-pom-v66-v71.py`, `clean-verify-v66-private.log` |
| Build/package | JDK17, Maven offline, `Package -DskipTests`, sem perfil shadow/SQL | A/B exit 0; ZIP/manifesto/revisão byte-idênticos, 188 membros, nove dependências, 2.159 inputs; DLL 12.8.2 estagiada apenas como bytes passivos do lock. | `target/ci-p10-20260927-01/p08-offline-smoke-final-build-{a,b}-private.log`, `target/macrobloco-qualificacao-pacote-20260928-01/p08-offline-smoke-final-candidate-{a,b}/result.json` |
| Pins v71 | Pacote candidato, sem assinatura/aceite | Revisão `f0f326b4804fe17bca5c1faa6374d71ffd823f09513ab2d984ec56e0b3b243fb`; manifesto `545857495d77ea1708d9a0854f6f95e775068be9e6050300d3284bd6ffa0cb22`; ZIP `c49dc4b3ac1fe9c952d0fa1f57ef6e66776a7c3593e1d5e9afb61d06a81b696f`. | `p08-offline-smoke-final-package-{a,b}-private.log` |
| JAR extraído | Extração segura A/B, 190 entradas por envelope, 30s/comando, heap512MiB, JDK17, `V2_*` parentais hostis | Quatro `config-validate`/`dry-run` exit 0, stderr 0; resumo LOCAL_SHADOW, fontes/auditoria desligadas, deny-all. Manifesto passou antes/depois. | `target/ci-p10-20260927-01/p08-offline-smoke-v71-result.json`, SHA-256 `550DE92FE976B4ED4308B49CBFACC27CBCBF4A7A5F7D9DA5B3853B87DB766AA2` |
| Guardas | Somente envelope/launcher offline | Entrada extra recusada `QUAL_OFFLINE_SMOKE_INPUTS`; pin inválido `QUAL_PACKAGE_PIN`; 25 guardas sintéticos do envelope PASS. | `p08-offline-smoke-final-*-red-private.log`, `p08-offline-smoke-package-guards-v70-private.log` |
| Gates | Sem rede/SQL | Parser de três scripts, trilha 33/48/nove, scanner 4.025 candidatos zero achados, Gitleaks 4.025 arquivos zero leaks, diff check PASS; `graphify update . --no-cluster` exit0, 37.152 nós/95.072 arestas. | `target/ci-p10-20260927-01/*p08-v71-private.log`, `graphify-update-p08-v70-private.log` |

Resultado do empacotador preservado como `PACKAGED_NOT_SMOKE_QUALIFIED`; o recibo novo prova apenas CLI offline. Serviço/processos SQL inexistentes e `V2_SHADOW_JDBC_URL` ausente após a rodada. Nenhuma DLL nativa foi chamada pelo caminho `Main` offline, e nenhum comando físico do README foi executado.

## Retomada imediata — até três ações

| Ordem | Ação | Pré-condição | Prova esperada | Alternativa independente |
| --- | --- | --- | --- | --- |
| 1 | Revalidar mídia/estado e extrair/instalar SQL Server local via UAC legítimo. | Operador desta máquina informa que está pronto; não repetir antes. | Árvore/assinatura setup, exit/logs, serviço/versão/rede somente local. | Nenhum gate offline adicional necessário neste pacote. |
| 2 | Preflight read-only `master` e criar só banco ausente/vazio; aplicar V001–V104 e validadores sintéticos/rollback. | Instância `LUCAS` local, caminhos reais/quotas e ausência de alvo conferidos. | Recibos separados, schema/baseline, contagens antes/depois. | Revisar lock/runtime físico 12.8.1 sem executar 12.8.2 em shadow. |
| 3 | Executar P08 supervisor/worker físico A/B, guardas/sucessão/selo na revisão e solicitar aceites G02/P29 próprios. | P07/SQL local e pacote físico coerente com perfil 12.8.1; owners externos fornecem CI/Segurança/RC. | IT/rollback, pacote/smoke/guardas e checks/aceites nominais. | P11–P33 conforme insumo próprio. |

- Input externo imediato: indicação do **operador desta máquina** de prontidão para UAC. Depois, owner do pacote/Segurança ratificam a combinação de distribuição 12.8.1, e owners G02/P29 entregam checks/aceites externos.
- Parada: UAC cancelado novamente, host/banco diferente, serviço/banco preexistente, SQL incerto, listener remoto, DLL 12.8.2 em perfil shadow ou deriva do lock.
