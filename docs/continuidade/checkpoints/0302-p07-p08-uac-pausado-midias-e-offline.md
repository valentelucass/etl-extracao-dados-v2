# Checkpoint 0302 — UAC pausado, mídia inspecionada, gates offline preservados

## Identificação e objetivo

- Checkpoint: 0302, 2026-09-28 05:48 UTC, P01–P33 / P07–P08–P10–P29.
- Anterior: `docs/continuidade/checkpoints/0301-p07-p08-p10-p29-offline-shadow-local-pendente.md`, SHA-256 `B8398C04380CBE09F7A90748A80F7EE8A961802E3BD0D56204071186DBA19499`.
- Objetivo: reconstruir e qualificar a sombra local exata para a revisão atual, fechar as parcelas offline independentes e preservar aceites externos próprios.
- Estado: `TESTADO_NA_CAMADA` para build/P08 candidato/PMD e preparação; P07/P08 físicos `AGUARDANDO_OPERADOR_UAC`; P10/G02 e P29 integrais continuam abertos. Nenhum P promovido por documentação.
- Autoridade: `AGENTS.md`, `STATES.md`, `TRILHA_CONCLUSAO_POR_MODELO.md`, `docs/continuidade/qualificacao-p07-p33/matriz.json` e [mapa P01–P33](../qualificacao-p07-p33/mapa-p01-p33-20260927.md).

## Autorização, alvo e limites

- O usuário autorizou expressamente instalar SQL Server **local nesta máquina** e criar só `localhost/ETL_SISTEMA_V2_SHADOW`, mas, após cancelamento do UAC de extração, mandou **não repetir o prompt até o operador indicar prontidão**. Essa pausa operacional é vigente; não revoga o escopo. A máquina da antiga sombra está fora do escopo e não foi acessada.
- Sem prompt UAC nesta unidade, setup, serviço, conexão SQL, `CREATE DATABASE`, DDL, Flyway, fonte real, push, merge, deploy ou cutover. `V2_SHADOW_JDBC_URL` ausente; DLL 12.8.2 não carregada no perfil shadow. A janela e os limites futuros estão no [runbook](../../runbooks/reconstrucao-shadow-local-20260928.md).
- Branch `main...origin/main`; todos os deltas preexistentes e checkpoints/REDs preservados. `../CONTEXTO_GLOBAL.md` não localizado na busca segura anterior. Recibos privados ficam em `target/ci-p10-20260927-01/`; nenhuma credencial ou dado de domínio foi consultado.

## Alterações e evidência observada

| Parcela | Camada e limite | Observado | Recibo |
| --- | --- | --- | --- |
| P07 preparação | Mídia oficial Microsoft, bytes locais; 7-Zip 26.03 standalone sem instalação | `SQLEXPR_x64_ENU.exe` 748.772.024 bytes, SHA-256 `74AA90C11202A5524E769B9BC22531BAEF22D91E9B2D2E8C3CB99E89A65C5297`, Authenticode Microsoft Valid. 201 CABs contíguos, um arquivo cada, 826.661.623 bytes descompactados, zero traversal, 15 nomes planos duplicados. Amostras extraídas; `SETUP.EXE` 59.408 bytes, Microsoft Valid, **não executado**. Árvore de setup completa não comprovada. | `tooling-microsoft/media-receipt.json`, `tooling-microsoft/cab-inspect/`, `action-log.md` |
| P08 offline | Java17/Maven, sem JDBC/SQL | Dois builds `Package` candidatos e dois ZIPs idênticos por SHA, 187 payloads/nove dependências/2.159 inputs; extração segura A 189 entradas. `PACKAGED_NOT_SMOKE_QUALIFIED`; launcher combina shadow com DLL 12.8.2 e não foi executado. | `target/macrobloco-qualificacao-pacote-20260928-01/`, `STATES.md` |
| P10 local | Espelho v66, mesmos bytes Java/POM | `clean verify` exit 0, 2.348 Surefire, cinco skips, seis ITs offline; JaCoCo bootstrap 4.384/5.457 linhas e 1.942/2.853 ramos, limites 80/60 intactos. G02 exige checks reais do SHA publicado e owner. | `clean-verify-v66-private.log`, `candidate-repo-mirror-v66/target/site/jacoco/jacoco.xml` |
| P29 local | PMD 7.17.0 offline | 36 achados brutos em 656 fontes/dez regras, 36/36 disposições com 2.348 casos/278 XML v66; sem aceite nominal de SAST/licenças/SOs/RC. | `pmd-disposition-v66/result.json` |
| Gates finais | Trilha/scanner/Gitleaks/encoding/diff sem rede | Trilha exit 0: 33 etapas, 48 IDs abertos, nove pacotes, `executionAuthorized=false`. Scanner exit 0: 4.025 candidatos, 4.024 textos, um binário, zero achados. Gitleaks 8.29.1 exit 0 em espelho de 4.025 arquivos, zero leaks; UTF-8 estrito e `git diff --check` exit 0. | `trilha-preparation-uac-final-private.log`, `offline-secret-scan-uac-final-private.log`, `gitleaks-uac-final-private.log` |

O setup/preflight/recovery foi preparado no runbook, sem comando de DDL executável antes dos caminhos reais de `master`. O resultado cancelado do UAC é conhecido e não deve ser repetido sem operador pronto; se um futuro comando perder resposta, primeiro reconciliar serviço, logs e `master` read-only. Os arquivos de amostra privados não substituem a árvore extraída oficialmente.

## Retomada imediata — até três ações

| Ordem | Ação concreta | Pré-condição | Prova esperada | Frente independente |
| --- | --- | --- | --- | --- |
| 1 | Com operador pronto, extrair mídia assinada via UAC legítimo, validar `setup.exe`/árvore e instalar instância local conforme runbook. | Indicação explícita de prontidão do operador; reconciliação read-only de serviço/processos/arquivos. | Exit code/logs, assinatura, versão/edição, configuração sem listener remoto. | Gates offline já executados. |
| 2 | Preflight `master` em `lpc:localhost`, depois criar só banco ausente/vazio e aplicar V001–V104/validadores sintéticos. | Instância local íntegra; host `LUCAS`, banco exato ausente, caminhos/quotas conferidos. | Recibos separados de `master`, DDL único, schema/baseline, rollback e contagens antes/depois. | Resolver lock/runtime P08 sem carregar 12.8.2 em shadow. |
| 3 | Concluir P08 físico e aceites externos G02/P29 na mesma revisão. | Banco/JDBC loopback e DLL12.8.1 comprovados; owners entregam CI/segurança/RC próprios. | IT, pacote/smoke/guardas e checks/aceites nominais; sem inferir de fixture. | P11–P33 somente parcelas com input próprio elegível. |

- Input externo imediato: **operador desta máquina** indica que está pronto para o prompt de elevação legítima do Windows. Não repetir antes. Posteriormente, owner do repositório e Seguranca/release owner fornecem checks/aceites externos G02/P29.
- Condição de parada: host ou banco diferente, serviço/banco preexistente, arquivos/drift, resultado incerto, listener remoto, DLL 12.8.2 em shadow ou UAC novamente cancelado.
- Condição de conclusão: critérios integrais na camada exigida e aceites próprios; downloads, hashes, preparação ou testes sintéticos isolados não fecham P07/P08/G02/P29.
