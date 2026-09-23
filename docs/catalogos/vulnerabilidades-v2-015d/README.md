# Política local fail-closed de vulnerabilidades de dependências

Este catálogo congela a decisão técnica local da rota `G05A`, Bloco 42, para a tarefa `V2-015d/DECISAO_POLITICA_LOCAL_FAIL_CLOSED`. Ele não executa o OWASP Dependency-Check, não consulta NVD ou outro feed e não contém achados, dependências ou credenciais reais.

## Decisão congelada

- O limiar do `failBuildOnCVSS` é o threshold `0.0`; qualquer vulnerabilidade reportada bloqueia, independentemente do score.
- O limiar não admite override por linha de comando, workflow ou valor ad hoc. Qualquer mudança exige nova versão desta política e atualização coordenada do fingerprint.
- Falha do scanner, resultado parcial, JSON ausente ou malformado, schema inesperado, score ausente e severidade ausente ou desconhecida bloqueiam.
- JSON é a evidência de máquina obrigatória; HTML é artefato humano obrigatório, mas não é fonte decisória.
- `failOnError` deve permanecer verdadeiro, `continue-on-error` é proibido e ausência do artefato deve ser tratada como erro.
- A chave NVD só pode vir da variável de ambiente `NVD_API_KEY`. Valor literal, conteúdo sensível em relatório ou log e leitura de `.env` são proibidos.

## Exceções

O catálogo efetivo começa vazio. Uma futura exceção exige correspondência ordinal exata entre identificador da vulnerabilidade e PURL Maven versionado, justificativa não sensível, owner por papel, escopo fixo, expiração UTC futura de no máximo 90 dias e vínculo ao SHA-256 desta política. Wildcards, regex, supressão ad hoc, exceções permanentes, expiradas, órfãs ou fora do escopo bloqueiam.

Os fixtures são exclusivamente sintéticos. Uma exceção sintética válida existe apenas para provar o avaliador e não é baseline aceita. Nenhuma exceção efetiva, finding real ou aceitação nominal é produzida por este catálogo.

## Modos de validação

```powershell
pwsh -NoProfile -File ./scripts/validation/Test-DependencyVulnerabilityPolicy.ps1 -PolicyOnly
pwsh -NoProfile -File ./scripts/validation/Test-DependencyVulnerabilityPolicy.ps1 -VerifyImplementation
```

`-PolicyOnly` valida encoding, schemas fechados, semântica congelada, casos sintéticos e hashes. `-VerifyImplementation` acrescenta verificações estáticas do `pom.xml` e do workflow de segurança. `-ReportPath` é reservado ao relatório JSON produzido por uma execução autorizada futura; o Bloco 42 não o usa.

## Limites

Esta decisão é contenção técnica local. Ela não autoriza rede, NVD/feed, perfil `security-audit`, CI remoto, release, deploy, banco, produção, findings reais nem leitura de segredos. A aceitação nominal e a baseline real continuam pendentes na rota `G05`.
