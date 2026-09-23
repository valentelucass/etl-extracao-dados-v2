# Checkpoint0222 — regressão local e matriz pós-P11 conferidas

P11_LOCAL_REGRESSION_RECORDED. 2026-09-21T23:07:34.921Z.
Anterior: docs/continuidade/checkpoints/0221-regressao-unitaria-pos-p11.md; SHA-256 6c987c93e1200f13ce2bb0b133084b4f49b52fc0cf918ffc15895d7d56fe8cc5.
Objetivo/autoridade: pedido anexado A/B/C; concluir pendências das seis P sem
repetir preparação. Estado TESTADO_NA_CAMADA_UNITARIA_E_DOCUMENTAL; conclusão
integral bloqueada pelos inputs exatos, sem dificuldade técnica local conhecida
no recorte executado. A/B físicos continuam sem autorização específica.

## Limites e alteração

Somente cópia local, fixtures sintéticas,512MiB/900s por execução; zero fonte
real/rede externa, SQL/DDL/COMMIT, DLL nativa, segredo real, publicação, produção,
deploy/cutover. POM público/Java/SQL intactos. Quatro deltas com snapshots exatos,
3735 dos3739 arquivos iniciais preservados; novos catálogo, matriz, sucessor,
validador e checkpoints. Três documentos retêm todos os bytes anteriores como
sufixo. Recuperação somente do delta próprio, preservando edições posteriores.
Inventário/intent/ledger/logs: target/p11-regressao-20260921-01/.

## Execução e evidência

| Camada | Resultado | Evidência |
| --- | --- | --- |
| Unidade integral |2162 casos/247classes;0falhas/erros;4skips históricos idênticos; casos/multiplicidades exatos|unit-04.json,unit-summary.json|
| Build |Compilação/Enforcer/Spotless/Checkstyle PASS|unit-04.stdout.log|
| Pacote parcial |JAR/oito libs e classpath estático PASS; aplicação não executada|package-01.json,jar-static.json|
| Matriz/sucessão |10+10contraprovas PASS; predecessores14+11 PASS|validation-regression-01,validation-succession-01|
| Trilha |PASS; contadores/rotas preservados|validation-trail-01|
| Scans delimitados |6+299+222textos;0achados|validacoes.json|
| Integridade |UTF-8/diff/snapshots e novo manifesto; readback final após ponteiro|diff-review.json,final-integrity.json|

Catálogo: docs/catalogos/p11-regressao-local/. Resultado SHA-256
5adecc8693481759e94f6ac9d90d8cafc36573e2967ee656982e418672bbcd10; validações SHA-256
140b9b1b9f3c135d2a8046113c518006b647795e2a096cc6cb0dc29dc4bfc584. As últimas conferências de integridade ficam
em target/p11-regressao-20260921-01/final-integrity.json, sem alterar este checkpoint.
Falhas unit01/02, parada03 e correções do harness preservadas; unit04 integral
passou. Scan vazio em target descartado; check no-index corrigido sem alterar
produto. Nenhum processo Maven próprio permanece; sem efeito físico desconhecido.

## Saldo e próximas ações

1. Usuário autorizante fornecer ordem própria A para JDBC/nativo12.8.2 no alvo
   localhost/ETL_SISTEMA_V2_SHADOW, sintéticos/rollback, contagens antes/depois,
   vigência/limites/ledger. DLL nova ausente no cache, nenhuma leitura SQL agora.
2. Ordens físicas P07/P08 próprias: VerifyPhysical/cobertura, lock e seis pins
   atualizados com DLL/licenças reais, pacote extraído/A-B/recusas/reprodução/selo.
   Critérios/delta em DELTA-P07-P08.md. Provas0207/0213 continuam históricas.
3. Receber os16 inputs externos com owners/origens da matriz: G02/owner do repo;
   FEED-BASELINE/Segurança; G05/DBA-Ops-Segurança-Compliance/data owner;
   G03/fornecedor/dados e G04/Negócio/referências/consumidores. Sem nomes inventados.

C corrigido: networkCalls agregado removido da matriz sucessora; preparação
offline, rede pública0220 (sete GETs com recibo; total não instrumentado) e
rodada local separados. Matriz/selos/ledgers históricos intactos.
FEED-ACHADOS atendido no scan0220; P09 não reavaliado sem G01 novo.
Contadores39/45 e67/115; zero novos aceites V2 ou humanos. Nenhuma prova
unitária/construção local foi convertida em cobertura/SQL/native/P08 físico.
