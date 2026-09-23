# Análise estática local

O gate usa Java 17, Maven 3.9.14 e os artefatos já presentes no cache local:
PMD 7.17.0 e maven-pmd-plugin 3.28.0. Não instala ferramentas nem baixa dependências.
O POM temporário e os settings de usuário/global vazios ficam no diretório da
tentativa. O POM e os workflows do projeto não são alterados.

```powershell
pwsh -NoProfile -File scripts/security/Invoke-LocalStaticAnalysis.ps1 -OutputDirectory target/local-static-analysis/main-01
pwsh -NoProfile -File scripts/security/Test-LocalStaticAnalysis.ps1 -OutputDirectory target/local-static-analysis/tests-01
```

Cada saída precisa ser nova e permanecer em `target`. O tempo máximo é 180 segundos
por processo Maven, sem retry. `SourceDirectory` aceita `src/main/java` ou fixtures
dentro de `target`; o inventário e seus hashes são conferidos após a análise.
`JavaHome`/`MavenHome` opcionais selecionam instalações locais dessas versões.

Exit 0 exige as dez regras executadas em todas as unidades fonte inventariadas,
nenhum finding, supressão, erro, drift ou relatório ausente. Exit 1 conserva os
findings ou a razão da falha em `result.json`, junto do XML, benchmark e logs.
Os testes executam duas análises reais: fixture válida e fixture recusada por
chave/IV constantes e exceção em finally; as demais contraprovas são offline.

As regras cobrem criptografia, fechamento de recursos, preservação de falhas e
erros específicos de concorrência. `DoNotUseThreads` é excluída por exigir o
contexto J2EE, que não corresponde ao CLI. Alertas de ownership ou sanitização
exigem triagem do código; o gate não os suprime ou cria uma baseline automática.
Esse recorte técnico não substitui SAST abrangente, aceite nominal de Segurança
ou qualificação do release candidate.

## Disposição técnica específica

`Invoke-LocalStaticDisposition.ps1` verifica um catálogo autorado por alerta,
conforme a [ADR 0054](../../docs/adr/0054-disposicao-tecnica-especifica-de-alertas-pmd.md).
Ele recebe `RepositoryRoot`, `Catalog`, `PmdDirectory`, `TestSourceRoot`,
`TestReportDirectories` e um `OutputDirectory` novo dentro de `target`.
Os XMLs devem estar em `TestSourceRoot/target/surefire-reports` ou
`TestSourceRoot/target/failsafe-reports`, produzidos pelo mesmo build informado.

O verificador confere o relatório bruto, as dez regras, os conjuntos e hashes
de fontes atuais/analisados/executados, e cada teste vinculado ao alerta. Todas
as invocações devem ter passado; nomes repetidos mantêm sua multiplicidade.
Achado adicional, disposição ausente, drift, erro, supressão ou prova incompatível
impedem a aprovação. Não existe geração automática de baseline ou exclusão ampla.

`PASS_LOCAL_REVIEWED_FINDINGS` aprova somente essa conferência técnica. O PMD
continua com seus findings e exit 1; não há aceite humano, SAST integral ou release
aprovado. `Test-LocalStaticDisposition.ps1 -OutputDirectory target/disposition-tests/rodada-nova`
executa as contraprovas locais do verificador, sem chamar Maven, PMD, SQL ou rede.
