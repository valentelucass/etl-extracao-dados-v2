# Checkpoint 0547 — auditoria NVD real e adoção do scanner 12.2.2

23/10/2026. Continuação de [0546](0546-falha-scanner1220-correcao-oficial-em-qualificacao.md), no mesmo pedido de avanço V2. O usuário informou WMS parado e autorizou continuar enquanto ausente. Esta unidade cobre a ferramenta de auditoria, o relatório real e a qualificação local do pacote; não executa ESL HTTP, SQL Server/TDS, implantação ou extração.

## Resultado real do candidato 03

O Dependency-Check 12.2.2 concluiu a atualização e a análise por arquivos oficiais NVD 2.0. Scanner exit 0; nenhum erro de ingestão e nenhuma linha de erro no log. O problema de URLs longas observado na versão 12.2.0 não reapareceu. O lote controlador terminou com exit 1 porque a validação posterior recusou o caminho da primeira entrega; ver abaixo. Receipt protegido: duração total 450017 ms, pico 1099624448 B, sem timeout. A entrega posterior aprovada não altera esse recibo original.

O JSON nativo contém seis grupos e duas dependências relacionadas: oito arquivos de bibliotecas. Seus SHA-256 correspondem exatamente aos oito arquivos do pacote qualificado de 0543. Vulnerabilidades reportadas: zero; vulnerabilidades suprimidas: zero. O relatório usa schema 1.1 e engine 12.2.2. Última modificação NVD informada: 23/10/2026 20:00:09 UTC; última consulta do cache: 21:29:47 UTC.

O scanner desabilitou automaticamente o OSS Index por ausência de credenciais. Nenhum analisador foi desabilitado manualmente. O escopo padrão exclui dependências de teste e plugins Maven. Portanto, este resultado prova a análise NVD das oito bibliotecas cobertas, sem declarar auditoria de todo o projeto, risco zero ou aceite nominal G05.

## Entrega do relatório e política

A primeira chamada do validador recusou o relatório fora do caminho canônico: `MACHINE_REPORT_PATH_NOT_AUTHORIZED`. A falha e o resultado original do candidato permanecem preservados. A correção foi entregar os dois arquivos reais, sem alterar seus bytes, em `target/dependency-check/`; a política e seu validador não foram relaxados. O scanner rodou no projeto candidato isolado, não na raiz canônica.

Após a entrega, `VerifyImplementation` passou nos mesmos 33 casos sintéticos, sem soma entre rodadas. O modo `ReportPath` passou sobre o JSON real: seis dependências principais, zero vulnerabilidades e zero exceções. JSON e HTML têm os seguintes hashes:

```text
JSON: 8DAF9CF11DC44728D2B4C304974ADCD012D1BB20CB9FEC7FC9B5BBACD4B4D1C3
HTML: 06B8A4D55A00EFCF0F356B1B15A9D60A4D7400F32EBBACAB0ECA73CD57B41361
```

## Configuração adotada e qualificação do pacote

O POM canônico adota o patch 12.2.2 e separa o cache por versão: `${user.home}/.m2/dependency-check-data-${dependency.check.version}`. A comparação semântica limita o delta a esses dois campos, além do comentário explicativo. Mantidos threshold 0.0, `failOnError`, analisadores configurados, política, exceções e bibliotecas do ETL. SHA-256 do POM atual: `DCEC6D5DCB3A8878BD1B246FDE9419AEFA298187080273E3D5D12C71F9E14221`.

O cache completo do candidato está no diretório privado da unidade 03. Não foi copiado para o novo caminho canônico; a próxima execução desse perfil poderá precisar atualizá-lo. O cache parcial 12.2.0 não é baseline aceita.

A unidade de qualificação pós-adoção foi admitida às 18:57 BRT pelo executor original: árvore de até 2048 MiB, prazo de 12 minutos, uma vaga pesada por vez. O auxiliar de espera libera a trava de observação antes da admissão original; não contorna os controles. As recusas anteriores por concorrência não iniciaram filhos. Nenhum processo de outro projeto foi encerrado. O lote encerrou às 19:05:51 BRT com exit 0, sem timeout, 510219 ms e pico de 1305194496 B; admissão com 7458 MiB disponíveis e 74% de comprometimento.

**Qualificação local pós-adoção concluída.** Conferidos 1540 arquivos de entrada Java/recursos sem drift e o POM acima. Reutilizadas somente as classes previamente compiladas e qualificadas de 0542/0543, sem recompilação. Empacotamento exit 0; sete IT offline aprovados, zero falhas/erros/skips. A comparação byte a byte confirmou 1319 classes e 45 recursos main, POM embarcado igual ao canônico e as mesmas oito bibliotecas runtime. Novo executável em `g05-1222-20261023/snapshot/target/etl-dataexport-v2.jar`, sob o diretório-base externo abaixo:

```text
JAR SHA-256: 92714FBFAA4B5836D4B55F59DF7ED062934C72681A5FA701CCBCF6B8597D5C42
```

Atualização estrutural AST concluída sem agrupamento/LLM: 44306 nós, 115932 arestas, exit 0. Não houve atualização semântica de documentos, HTML ou validação de negócio por essa operação. A conferência final após o encerramento verificou novamente 1540 entradas, POM, JAR, oito bibliotecas e os dois relatórios: nenhuma divergência. `final-integrity.json` registra os SHA-256 do resultado e do receipt. A unidade terminou com `qualified=true`, `fullVerify=false` e `G05Accepted=false`; não é suíte integral, cobertura nova, bundle operacional ou implantação. Os 70 testes de 0542 permanecem qualificação anterior dos mesmos bytes Java, sem soma de novas rodadas aos totais históricos.

## Evidências

Diretório-base externo: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2`.

- `auditoria-dependencias-20261023-03-dc1222`: plano, POM candidato, log real, JSON/HTML, `scan-result.json`, receipt e cache completo da ferramenta.
- `g05-1222-20261023`: `publication-result.json`, logs de implementação/política, `result.json`, `memory-job-receipt.json`, `final-integrity.json`, logs de pacote/IT/AST e executável atual. A publicação registra explicitamente `scannerRanInCanonicalDirectory=false` e `G05Accepted=false`. SHA-256 do resultado final: `64875BDC7DA8B77E8DCD6745AE465725FBE12CCE42AB2ECA58B67ACFA6E34A0A`; do receipt: `32B8261C966B277BA11653C0C1951051FCF293EAD57960E7A63529EB7AAF9665`.
- `qualificacao-intervalo-esl-20261023-01`: 70 testes selecionados aprovados em 0542, sete IT em 0543 e comparação independente com caminhos Windows estendidos. Repetir os sete IT após o POM novo não acrescenta sete testes distintos ao histórico.

## Pendências preservadas

O piso ESL de 2000 ms está qualificado na configuração efetiva Data Export/GraphQL; não comprova franquia disponível nem coordenação de todos os consumidores. Ainda faltam saldo, período e escopo da franquia ESL e esclarecimento do 422 de Fretes 6389. Permanecem os gates de cobertura bootstrap (79,6644%, déficit histórico de 34 linhas), oráculos, paridade, retenção e aceite nominal/material G05. Nenhum aceite de extração completa foi emitido.

MainHTTP histórico continua UNKNOWN / MISSING_NOT_RECONSTRUCTED; C78 continua rollback-only, sem herdar retenção E3. V1/WMS não reiniciados. SQL Server 16.0.1000.6, instalação, contas, credenciais, permissões, identidades, ACL, serviços, listeners, TLS global, schema/DDL/Flyway e produção preservados. O H2 do scanner armazena dados públicos de vulnerabilidades e não é o SQL Server do ETL.
