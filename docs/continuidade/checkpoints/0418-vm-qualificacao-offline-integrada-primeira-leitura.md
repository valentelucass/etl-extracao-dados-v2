# 0418 — VM: qualificação offline integrada e primeira leitura pendente

## Identificação e autorização

- Data: 2026-10-02. Anterior: [0417](0417-vm-sql-windows-auth-recusada.md), SHA `63A1C9AC57887D1657294C5D3569886EC8044E5D02447E2E950C79726A0E1639`.
- Pedidos efetivos: continuar qualificações possíveis, chegar à primeira extração, usar sa em lugar de usuario_etl e delegar aos agentes conectados. Escopo/local seguro da credencial sa e origem/tenant/dia/rotação foram perguntados; respostas ainda ausentes. Nenhum efeito dependente foi inferido dessas perguntas.
- Estado: `TESTADO_NA_CAMADA` offline; `BLOQUEADO_POR_INPUT` para nova autenticação sa e leitura externa. Piloto SQL não habilitado. Critérios: AGENTS §1/6/8/9, STATES e ADR0055; não houve aceite P08, produção, fonte real ou cutover.
- Codex único editor de STATES/trilha/RETOMADA; Runtime único editor do teste delegado, Banco responsável futuro SQL/ledger. Fontes e Regras fizeram revisão offline. Não houve novo SQL após a troca solicitada para sa, nem habilitação/login/grant/serviço.

## Alterações e evidência

Inventário inicial preservado: AGENTS, STATES e RETOMADA já modificados; .ai-memory.toml e checkpoint0417 não rastreados. Base congelou 4198 entradas fora de target/segredos/grafo; CONTEXTO_GLOBAL ausente. A primeira cópia privada falhou por caminho longo antes de Maven e foi preservada; segunda cópia em caminho curto teve FAIL offline por POM ausente no cache Maven migrado. Download das versões Maven já fixadas corrigiu a preparação sem alterar o POM ou versões.

| Critério | Camada / observado | Evidência |
| --- | --- | --- |
| Gate base | JDK17/Maven: 2433 unitários, zero falhas/erros, cinco skips; seis ITs de pacote, zero falhas/erros/skips; Enforcer, Spotless, Checkstyle e JaCoCo PASS; exit 0; 4198 entradas sem drift; 541,5 s no controlador | `target/qualification-vm-20261002/base-03/receipt.json` e logs |
| Comparador final | Quatro métodos novos / 20 recusas: JSON/presença corruptos, limites/página sem vínculo, ausência/duplicidade de evidência e binding inválido; classe 9/0/0/0; fake JDBC, zero SQL real; apenas dois ajustes de formato pelo Runtime | `target/runtime-vm-20261002/comparator-focused-01/receipt.json`, SHA `E7AF074B952121AFB603497ED61DD39C300CCD94844A5CB4B381347D686B326A` |
| Preservação de cobertura | Todo Java principal/POM/migrations byte-idêntico à base; relatório do pacote Coletas: 762 linhas cobertas/155 não, 271 branches cobertos/123 não; 80/60 PASS; base antecede o teste novo, não houve suíte integral nova de 2437 testes | `target/qualification-vm-20261002/integrated-receipt.json`, SHA `BADB8E4AD2C4EC28A5915AAEF0DDBFE609DBE36937BF63D3F4AB5B91C550B302` |
| Scanner Windows | Git stderr esperado capturado por processo; hash SHA-256 compatível com .NET Framework sem alterar pins; 20 autotestes PASS, 13 remoções históricas verificadas e manifesto alterado recusado | `scanner-selftest-03.log`, `secret-scan-03.log` sob diretório da rodada; varredura 4199 candidatos/4198 textos/um binário/zero achados |
| Schema versionado offline | Guardas/mutantes de V105, inventário, baseline e validator PASS; não aplicação SQL | `target/qualification-vm-20261002/epoch-offline.log` |
| Grafo | AST-only exit 0: 38810 nós/92063 arestas; sem semântica remota | `target/qualification-vm-20261002/graphify-update.log` |
| Banco, revisão offline | Hashes V001–V105/baseline locais conferem; banco recuperado não prova epoch; ausência de Flyway e collation impedem V105 direta | `target/banco-vm-20261002/reconciliacao-offline-shadow.md`, SHA `A4D6673B8592A6C5615AE88009032B3D9DCC1FA91017B07E65F286DA94866A27` |
| Primeira leitura | Sonda HTTP 6908 separada de trial SQL; comando futuro não executado, /info + página 1/per=1; CALL_BUDGET_REACHED/exit 1 esperado após duas chamadas bem sucedidas, só amostra parcial | `target/fontes-vm-20261002/receipt.json`, SHA `95CD169CA1A7BF4469BFF22F02C3D33BDF2139B4EFB34EFD7DDED516B78B5C36` |
| Domínio | Revisão estática sem defeito demonstrado; paridade observada não prova valores/filhos/completude; prazo cooperativo pode exceder 60 s enquanto chamada síncrona termina, sem reprodução física | `target/regras-vm-20261002/recibo-revisao-offline.txt`, SHA `904460169030EF5CA8C10AED0F107B1AD682BDFFABEA2C29FC349FB1ED5B9AAE` |

O readback SQL anterior à troca de login confirmou apenas o alvo local: 246 tabelas, 1816 objetos, zero histórico Flyway, zero CHECK/FK não confiável, quatro colunas V105 em Latin1_General_CI_AS e auditoria 0 execuções/453 páginas. Listeners observados em 0.0.0.0/::, sem efeito de rede/serviço. A recuperação contra o pacote e contagens não equivalem à prova contra V001–V105. Não foi executado 063/064, Flyway ou JDBC físico nesta unidade.

## Limites, falhas e recuperação

- Nenhum segredo exposto, alteração de credencial, SQL escrito, fonte HTTP, V1, produção, commit/push ou campanha física. Principal sa não foi verificado nem usado; os guards integratedSecurity permanecem intactos.
- FAILs de cópia/cache/scanner foram preservados. Diagnósticos privados retiveram somente tipo/linha; uma cópia diagnóstica perdeu o contexto do módulo e foi corrigida sem converter essa falha em prova do scanner. Leitura UTF-8 de log UTF-16 também falhou; o checker seguinte leu o encoding real, preservando o log anterior.
- Runtime preservou filtro Spotless que selecionava zero arquivos (não validação), formato inicialmente recusado e erro do validador privado que proibia BOM já existente; teste manteve esse BOM. Nenhum main/POM/pin/limiar alterado.
- Arquivos alterados: teste do comparador, dois scripts do scanner e documentos de estado/trilha/continuidade. Recuperação local: revisar/reverter somente os hunks desta unidade se necessário, preservando as mudanças anteriores; nenhum rollback de banco/serviço aplicável. Não modificar manifests históricos para ocultar drift.
- Validadores PS7 de trilha/continuidade não executados porque pwsh não foi localizado nos caminhos conferidos. Não afirmar ausência no disco inteiro nem PASS desses gates. UTF-8 dos arquivos alterados e diff conferidos; ainda não existe autorização/segredo para sa ou inputs suficientes da fonte.

## Retomada — até três ações

1. Usuário/owner fornece origem, tenant, dia/corte fechado e condição G01/autoridade da rodada; confirmar teto e PowerShell >=7.5 verificado, então Fontes executa somente a primeira sonda read-only, sem exigir todos P17–P29 ou P08 físico antes dela. Parar no primeiro erro/teto; não repetir automaticamente.
2. Usuário delimita “tudo” para sa e informa local seguro da credencial. Banco pode então preparar novo preflight autorizado; pedido não autoriza por inferência alterar outros sistemas, habilitar login, senha, grants ou serviço. Separar Windows integrado do acesso SQL auth.
3. Banco/Supervisor define unidade própria de comparação/convergência do schema/histórico com prova recuperável e limites novos antes de qualquer Flyway/IT. Não falsificar histórico, afrouxar guard ou reaplicar importação. Risco de prazo cooperativo pertence à futura qualificação do trial, não bloqueia a sonda isolada.

Nenhuma nova caixa/contador/aceite produtivo. Os bloqueios só serão reavaliados quando esses inputs mudarem; testes aprovados não precisam ser repetidos sem alteração pertinente.
