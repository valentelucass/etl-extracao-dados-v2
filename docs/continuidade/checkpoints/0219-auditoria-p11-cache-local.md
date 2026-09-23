# Checkpoint 0219 — auditoria P11 com cache e candidato JDBC

P11_CACHED_SCAN_COMPLETED_FINDINGS_OPEN. 2026-09-21T21:55:09.309Z.
Anterior: docs/continuidade/checkpoints/0218-correcao-local-validada.md; SHA-256 cbd570e575a1970f0cc7d3feb78e1504e49b7483e4c46e7ad215f1cbb10206d6.
Objetivo: concluir toda ação local elegível de P10/P11/P12/P14/P15/P21, corrigindo
dificuldades técnicas e verificando provas anteriores, sem inventar aceite.
Estado: auditoria local executada com achados; preparação das demais P validada.

## Autorização e limites

Ordem efetiva: “pronto, modo astra, finalize llogo isso”, mantendo o macrobloco
offline original e sua proibição expressa de rede. Sem subagentes, segredos,
.env/certificados, fonte real nova, SQL Server/DDL/DML, rotação/revogação,
secret store, deploy, serviço, job, release ou cutover. Nenhuma autorização de
um gate foi herdada por outro. Sem efeito externo ou reserva/renovação de saldo.
P09 não reavaliado: nenhum novo G01 sanitizado. Contadores 39/45 e 67/115.

## Alterações, decisões e preservação

A conclusão anterior de esgotamento local era prematura: cache NVD existia.
Foram auditadas as dependências reais com cópia desse cache, sem rede; quatro
achados permanecem no POM atual. JDBC 13.4.0 passou em cópia, mas não foi
promovido: autenticação nativa/SQL não qualificadas. As versões Jackson
corrigidas não estão no cache. A família correta e um feed atual exigem entrada
local nova ou acesso público autorizado; não existe exceção de segurança criada.

Novo catálogo: docs/catalogos/p11-cache-offline/. Novo módulo de sucessão
confere a base anterior e recibos; o módulo OfflinePreparation compõe a sequência.
Cinco deltas explícitos sobre 3.709 arquivos, 3.704 intactos; snapshots dos cinco
em docs/continuidade/historico/p11-cache-offline/. Quatro documentos conservam
o conteúdo anterior como sufixo byte a byte. Manifesto anterior imutável:
feccbc554f7c539e47c23acdcfd59a901b203132b3dffc58f5c12f2a2b24e6f3.
POM público, cache NVD original, manifests, selos, snapshots e ledgers anteriores
foram preservados. Recuperação: diff próprio e snapshots exatos, sem restaurar
todo o worktree nem descartar trabalho preexistente. Nenhum Java/SQL foi alterado.

## Execução e evidência

| Critério/camada | Execução observada | Prova |
| --- | --- | --- |
| Dependências reais, offline | Dependency-Check 12.2.0: 13 dependências, 4 achados, 0 erros de scanner; exit 1 do threshold 0.0 | resultado.json; JSON/HTML privados fixados por hash |
| JDBC candidato em cópia | 122 testes/16 classes, 0 falhas/erros/skips; Enforcer/Spotless/Checkstyle/compilação PASS | XMLs e recibo candidate-test-04.json |
| Scan do candidato | Sem alerta JDBC; 3 alertas Jackson; exit 1 preservado | candidate-scan-01.json e relatórios |
| Isolamento | 6 contraprovas do guard; rede/DNS/processos e arquivos sensíveis recusados | guard-test-02.log |
| Sucessão e cadeia | P11 10 contraprovas; anterior 14 + 11; composição PASS | validacoes.json |
| Preparação | 6 P, 17 requisitos, 13 contraprovas; 48 testes anteriores íntegros | validation-preparation |
| Trilha e frota | Validadores amplos PASS; preparação 24 contraprovas | validation-trail-02 / fleet-02 / trail-preparation |
| AST, diff, UTF-8 e scans | PASS; escopo e contagens no relatório | preservation.json e logs delimitados |

Resultado público SHA-256: 5a800809f235355b129cb8090c5fe1e5e43c06e7aaa17880d30db04145f21226.
Validações SHA-256: 276f160c00dd5672ccef74b2516686e40eadba1cbb7140ad4173904987b970ff.
Rodada privada: target/conferencia-final-p10-p21-20260921-01/. Relatório e matriz atual detalham requisitos/owners
e provas parciais. B56 já executou 11 consultas reais sanitizadas: reutilizadas,
sem nova chamada; ARRAY de STRING de 10633/4924 não é novamente exigido.
Provas físicas anteriores de SQL/principals locais também não foram descartadas.

Falhas preservadas: P06/frota/P07/P08/P09 anteriores; plugin recusou Maven
--offline; candidato inicial sem POM pronto e recursos com subprocesso recusado;
executor de validação com decodificação incorreta de ç; avisos CRLF classificados
indevidamente pelo primeiro check. Cada causa técnica foi corrigida e retestada.
Os quatro achados de dependências permanecem RED. Nenhuma exceção foi inventada.
Sem processo próprio de teste ativo ou efeito de resultado desconhecido. Depois
de salvar este checkpoint: conferir hash, atualizar RETOMADA, fixar a nova revisão
e repetir a leitura final de integridade, sem repetir efeito externo.

## Retomada — até três ações

1. Obter família Jackson corrigida (cache indica mínimo 2.18.9 nessa linha) e
   feed atual por canal público autorizado ou artefatos locais; Segurança fornece
   baseline/aceite nominal. A rede continua proibida pela ordem original.
2. Qualificar JDBC 13.4.0/nativo sob autorização própria de laboratório antes de
   promover pins; repetir auditoria e testes do conjunto final.
3. Admitir primeiro pacote sanitizado G02/G05/G03/G04 novo para a P correspondente,
   validando origem/owner/escopo, sem repetir as provas B56 existentes.

Requisitos externos: P10/G02 — owner do repositório, destino e CI; P11/FEED —
Segurança, correções/feed/baseline; P12/G05 — DBA/Ops/Segurança/Compliance,
ambiente/retenção/restore; P14/G03/G04 — fornecedor/owner de dados/negócio,
identidade e semântica; P15/P21/G04 — negócio e owners de referências/consumidores,
conteúdo/ratificação e fontes/dimensões. Origem de cada requisito na matriz atual;
nenhum nome nominal foi fornecido ou inventado. Estado integral continua
BLOQUEADO_POR_INPUT onde o artefato, decisão ou autorização efetivamente falta.
Conclusão integral exige as provas de cada gate; testes locais não as substituem.

Não houve produção, fonte real nova, rotação efetiva, deploy, paridade real,
cutover, revisão humana ou aceite V2. Nenhum desses resultados foi alegado sem prova.
