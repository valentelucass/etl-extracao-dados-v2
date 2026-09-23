# P11 — auditoria real das dependências com cache local

P11_CACHED_SCAN_COMPLETED_FINDINGS_OPEN. O encerramento anterior deixou uma ação offline elegível sem executar:
havia cache NVD local. Esta rodada auditou as dependências reais e corrigiu essa
lacuna. P11 integral continua aberta por achados e feed sem atualização/aceite.

Dependency-Check 12.2.0 examinou 13 dependências, gerou JSON/HTML e encerrou com
exit 1 pelo threshold 0.0: quatro achados em dois artefatos, zero erro de scanner.
Nenhuma supressão ou exceção foi acrescentada. Cache: última consulta 26/08/2026;
lastModified 22/07/2026. A data de modificação do arquivo não foi usada como prova
de feed atual. Relatórios completos e hashes estão em [resultado.json](resultado.json).

| Artefato atual | Achado do cache | Severidade | Correção indicada no cache |
| --- | --- | --- | --- |
| jackson-databind-2.17.2.jar | CVE-2026-54512 | HIGH | 2.18.8 |
| jackson-databind-2.17.2.jar | CVE-2026-54514 | MEDIUM | 2.18.8 |
| jackson-databind-2.17.2.jar | CVE-2026-54515 | MEDIUM | 2.18.9 |
| mssql-jdbc-12.8.1.jre11.jar | CVE-2025-59250 | HIGH | 12.8.2 |

Os três caminhos Jackson descritos no cache envolvem tipagem polimórfica,
InetSocketAddress ou a combinação de ignore/case-insensitive. A busca delimitada
em src/main não encontrou esses usos, mas isso não elimina o achado nem cria
exceção na política 0.0. As versões Jackson corrigidas não estão no cache Maven;
2.20.1, a alternativa mais nova disponível de databind, permanece na faixa afetada.

## Candidato JDBC disponível localmente

Driver 13.4.0.jre11 e pin nativo 13.4.0.x64 foram avaliados somente em cópia.
Compilação, Enforcer, Spotless, Checkstyle e 122 testes/16 classes
JDBC/fronteiras/arquitetura passaram, sem erro/falha/skip. Novo scan do candidato
removeu o alerta JDBC e conservou os três Jackson. O POM público não foi alterado:
autenticação nativa e integração SQL não foram executadas, e a autorização local
anterior fixa a combinação 12.8.1. O candidato está preparado para essa qualificação.
Os recursos foram copiados byte a byte (109 arquivos); somente a cópia automática
dos recursos foi desativada no candidato, para evitar um subprocesso do plugin.
Não houve dispensa de compilação/testes ou substituição do resultado de scan.

## Isolamento e falhas preservadas

Cache original e POM preservados por hash. Cópia própria do banco NVD/H2, sem
SQL Server, rede, segredos ou settings do usuário. Ambiente filho contém apenas
variáveis não secretas necessárias; Maven home/cache locais conhecidos. Uma
SecurityManager temporária do JDK 17 recusa sockets/DNS/listen, processos filhos
e arquivos .env/certificados; seis contraprovas passaram sem realizar essas ações.
Auto-update e analisadores remotos ficaram desativados. Nenhuma tentativa de
rede foi registrada pelo guard durante os scans. Esse guard é exclusivo da
rodada privada, não é recurso de runtime nem proposta para versões futuras Java.

Primeira tentativa: o plugin exige Maven online e recusou --offline antes do
scan. A segunda executou sem essa flag, mantendo a rede tecnicamente proibida
na JVM. Os testes do candidato preservam falhas por POM ainda não pronto e por
subprocessos de cópia de recursos; a execução corrigida é identificada em 04.
O literal PowerShell false sem cifrão foi corrigido na preparação antes do scan.
Nenhuma falha/manifesto/selo/ledger histórico foi apagado ou convertido em PASS.

## Outras P e próximo passo concreto

P10/P12/P14/P15/P21 conservam sua preparação offline. Há provas anteriores de
fonte real: B56 executou 11 consultas sanitizadas; shape ARRAY de STRING já foi
observado em 10633/4924. SQL/principals/cargas do laboratório também tiveram prova
física. Esses resultados são reutilizados e não exigidos novamente. O restante
de identidade/crosswalk/semântica continua explícito no pacote B56.

P10: destino remoto/branch ainda ausente. P12: ambiente operacional, retenção e
restore/RTO/RPO pendentes. P14: identidades/crosswalk e decisão fiscal. P15:
conteúdo oficial/versão/vigência das referências. P21: fontes/identidades/paridade
e lifecycle dimensional. Owners-papel e 17 requisitos: catálogo da preparação.

Próxima ação P11: obter, por canal público autorizado ou artefatos locais, a
família Jackson corrigida e feed com atualidade comprovada; qualificar o conjunto
JDBC/nativo candidato sob autorização própria. Sem download autorizado, não há
como construir a atualização Jackson ausente. Segurança fornece a baseline e
aceite nominal; nomes não foram inventados. Contadores 39/45 e67/115 preservados.
Sem produção, fonte real nova, rotação, deploy, paridade real, cutover, revisão humana
ou aceite V2. O relatório não afirma que a P11 inteira foi concluída.

## Matriz vigente dos requisitos externos

A [matriz atual](matriz-atual.json) conserva os 17 requisitos, sua origem e o
owner-papel, separando prova já recebida do restante. A matriz anterior é uma
fotografia preservada. Nenhum owner nominal foi recebido ou inventado.

| P | Gate | Owner-papel | Requisito externo restante |
| --- | --- | --- | --- |
| P10 | G02 | Owner do repositório | Destino/branch, conjunto aprovado, publicação autorizada e CI real do mesmo SHA. |
| P11 | FEED | Responsável de segurança | Dependências corrigidas, feed atual, qualificação do candidato e aceite nominal da baseline. |
| P12 | G05 | DBA, Operações, Segurança e Compliance | Ambiente dedicado, políticas de retenção e restore/RTO/RPO materiais autorizados. |
| P14 | G03/G04 | Fornecedor, owner de dados e negócio | Identidades/crosswalk não cobertos pela B56, contrato Raster e decisão fiscal. |
| P15 | G04 | Negócio e owners de referências/consumidores | Conteúdo oficial, versão, vigência, cobertura e ratificação segregada. |
| P21 | G04 | Negócio e owners de referências/consumidores | Fontes e identidades qualificadas, paridade e lifecycle dimensional ratificados. |

Origens detalhadas por requisito estão na matriz; o pacote B56 está em
[bloco56-continuacao/README.md](../bloco56-continuacao/README.md). O relatório
anterior de preparação permanece em
[preparacao-offline-p10-p21](../preparacao-offline-p10-p21/).

## Validação do fechamento

Sucessão P11: 10 contraprovas; composição anterior: 14 + 11; matriz offline:
13 contraprovas e 48 testes anteriores com hashes conferidos; preparação da
trilha: 24 contraprovas. Trilha ampla e frota PASS. AST de dois módulos e
UTF-8/diff PASS. Scans delimitados: catálogo (5 textos naquele instante),
snapshots (5) e validadores (295), sem achados. Recibos/hash em
[validacoes.json](validacoes.json). Preservados 3.704 dos 3.709 arquivos da base;
os cinco deltas são explícitos, com quatro documentos mantendo o histórico
como sufixo byte a byte. POM, cache NVD original e checkpoint 0218 intactos.

A primeira chamada dos validadores pelo executor privado falhou ao decodificar
o caminho `p07-replay-correção`: processo filho sem perfil usava outra codificação.
UTF-8 explícito corrigiu a execução; recibos RED e PASS são distintos e preservados.
O diff inicial também recusou avisos de conversão CRLF do Git; a conferência
corrigida desativou apenas esse aviso por comando, sem mudar configuração ou
bytes históricos, e manteve `diff --check` integral. Nenhuma falha do scanner
de dependências foi transformada em sucesso: os quatro achados continuam abertos.
