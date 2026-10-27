# 0646 — Passagem para novo supervisor do ETL V2

## Identificação, pedido e estado

27/10/2026. O usuário pediu: “entao faça, so limpar os outros e o novo
supervisor e ao final fecha esse”. Esta unidade transfere a supervisão para um
chat novo, preserva os arquivos e arquiva os chats antigos do ETL. Não apaga
conversas, repositório, capturas, worktrees ou processos. A criação e o
arquivamento serão feitos depois da publicação deste documento; esta fotografia
não afirma antecipadamente que essas ações já ocorreram.

Anterior: `0645-manifestos-pelo-main-e-valores-conferidos.md`, SHA256
`2F91CCDA71E5B34ACC56F260874CDAABA3CF1772F5B1C74CC2DD8F4BA992E7EC`. Base desta passagem: commit
`a263c010272ad278d9d72e925f7a7b3cc0431324`, já enviado ao origin/main e limpo.
Objetivo final permanece extração completa das entidades pelo V2, gravação e
validação no banco, seguida de comparação por registro/campo com Excel e ESL
no mesmo recorte. Esse objetivo ainda NÃO está concluído.

## Entrada de leitura e locais

- Repositório: `C:\Users\suporte\Documents\projetos\etl-extracao-dados-v2`.
- V1, apenas referência: `C:\Users\suporte\Documents\projetos\script-etl-automacao`.
- A = `C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2`.
- B = `A\quatro-etapas-20261024-01`.
- Banco = `A\delegacao-revisao-banco-20261024-01`.
- W = `C:\Users\suporte\Documents\Codex\2026-10-16\c-users-suporte-documents-projetos-etl`.

Ler este documento, checkpoint0645, AGENTS.md, Tarefas Pendentes de STATES.md,
docs/continuidade/RETOMADA.md e docs/runbooks/continuidade-agentes.md. Usar os
checkpoints históricos por necessidade concreta; não reiniciar centenas de
testes nem repetir consultas apenas para recuperar contexto. AGENTS contém
fotografias antigas: a mais recente prevalece para fatos, sem criar autoridade.
STATES mantém os critérios de aceite. Memória é evidência histórica: briefing
consultado nesta passagem tinha última observação em 19/10 e seis handoffs
antigos pendentes; não sobrepor isso às provas atuais de 27/10.

## Resultados reais por entidade

| Relatório / entidade | Extração comprovada no recorte | Banco / comparação |
| --- | --- | --- |
| 6908 Coletas | 17/10, 1 linha, fim de paginação | Gravação temporária, readback e rollback; 25 células e 22 bindings sem diferenças |
| 6389 Fretes DATA EXPORT | 17/10, 37 linhas, 114 campos, fim de paginação | Banco ainda pendente: CHECK/procedure/52123. Não confundir com Fretes GraphQL |
| 6399 Manifestos | 17/10, 315 linhas físicas, 17 chaves, quatro batches | Main real0645, Source=stage=readback=privado315; 6.300 células selecionadas sem diferenças; rollback |
| 6906 Cotações | 22/10, 287 linhas físicas, 285 chaves, fim de paginação | Gravação/readback/rollback; 6.888 células sem diferenças em prova anterior, não Main0645 |
| 8656 Localização | 17/10, 37 linhas, fim de paginação | Banco pendente; não é Raster |
| 8636 CAP | 17/10, 13 linhas históricas DATA_V2 | Chave/banco pendentes; Q06 posterior provou término sem reter contagem |
| 4924 Faturas por Cliente | 17/10, 37 linhas históricas DATA_V2 | Banco pendente; Q06 posterior sem contagem retida |
| 10633 contrato local INVENTARIO | 17/10, 207 linhas / 11 páginas históricas DATA_V2 | Nome de negócio upstream ambíguo; não inventar equivalência com faturas |
| 6392 Sinistros | 01–23/10, 60 linhas / 39 chaves | Mapper aceitou53/recusou7; comentários longos preservados raw, ainda sem SQL |
| Usuários GraphQL | Prefixo antigo: 2.727 páginas, 54.540 observados, 54.534 válidos, seis quarentenas | hasNext=true; SEM EOF/cursor/fechamento. Não retomar página2728 por inferência |
| Raster | 22/10, 108 viagens / 355 paradas, captura14+5campos | Comparação externa parcial; não valida Localização nem os nove Data Export |

Fim de paginação nesses nove recortes não prova histórico completo ou carga
permanente. As três provas de banco usaram rollback: não afirmar retenção final.

## Última unidade real e provas que o novo supervisor deve conferir

Última Source: `root-main-manifestos-0645-01`, counterId064501. Main verdadeiro,
JAR A98F/Core44; três HTTP reservados/concluídos, fim de paginação observado.
Original0/child0/Native0, 43.489ms, pico757.309.440bytes, uma conexão SQL,
STRICT74/14tabelas/11locks/rollback2/post10/absence8 e SQL16 preservado.

| Referência relativa a B | SHA256 |
| --- | --- |
| RootMainMftSourceBankQualification-root-main-manifestos-0645-01.json | 36DF1D507A04B3A9E1B2FDEDD70D0467266245DE287A67027270432241F476A9 |
| main-mft-private-comparison-preparation0642-v2/root-execution/main-mft-actual0645-01/qualification.json | 7460B8F69CE720EFABA1A76F93E69268DDF404A775BCF1906D9956BC9B173D9A |
| main-mft-actual-technical-audit-files-only-0645-v1/review.json | 2AFE82717CF060893FAEA74639EA869DAAC36DAF9A7B8E59899CE461A47096FB |
| RootGitCommitPush0645.json | 7532D92C795CBBB26D206EE6EB125B12EB3E1A7634B7F7943C80B415655FCDEB |

Comparador real: 315linhas/6.300células, 29.956nós rawJSON e 61.458derivados,
zero diferenças. Limites: 945célulasSQL e 16.380campos tipados UNVERIFIED;
bindings completos NÃO capturados, bytesHTTP brutos NÃO comparados,
allFieldsVerified=false e externalEslOrExcelParityProven=false.

`B\supervision-current.json` tinha rootActiveExecSessions=[] nesta passagem.
Reconciliador B852/caller1DDE real acrescentou SOMENTE as duas folhas064501.
Contadores globais5931/5932 continuam não finais. Não rebasear históricos.
Specs antigos LatestRaster agora devem recusar antes de efeito; uma próxima
Source exige sucessor LatestMainTyped e escopo próprio de entidade. Não reusar
unidade, CODE, janela, spec ou capturas concluídas.

Captura privada Main fica em
`C:\Users\suporte\AppData\Local\Rodogarcia\EtLV2Validation\main-mft-0642-01\data`
e está COMPLETE315. Não sobrescrever/reusar. Produtos privados e payloads não
entram no Git. O JAR Main real é
`B\me38-06380003\main-successor-unqualified.jar`, SHA
`A98F4B980751E5AE174677A7FFAF70E1308503EE368C23D48F2B0A233E1F61E6`.
O nome histórico não invalida a prova0645, nem qualifica todo o código canônico
pós68: a projeção antes/depois de quatro HTTP é explicitamente delimitada.

## Pendência interna imediata: diagnóstico de Usuários/P3

O progressive sobre view1000/13scripts falhou em `STATIC_CHILD_CLOSED0_REQUIRED`:
Original1, 49.695ms/133.517.312bytes, sem timeout, API ou SQL. O worker descartou
os buffers, por isso a causa interna e o script exato são UNKNOWN. Não inventar
OOM ou atribuir a LoggerFactory, nem declarar13PASS.

- Trabalho falho preservado: `B\pg42-06420001` e respectivo root-control.
- Diagnóstico preparado: `B\users-p3-progressive-diagnostic-preparation0644-v1\file-manifest.json`, SHA `3E76C57A65FE07D1E519F1DF990482BF607A92C430853070E3672756D6FCBE22`.
- Revisão: `B\users-p3-progressive-diagnostic-review-files-only-0644-v1\review.json`, SHA `350BB37E2F4A55286B683C9F5F713B643F2DBDDA72871DC9F13133BEC1A0E936`.
- Caller WIP: `B\users-p3-progressive-diagnostic-root-executor0644-v1\Invoke-ProgressiveDiagnosticRoot.pending.ps1`. AINDA sem manifesto/receita/revisão/execução; NÃO executar esse WIP diretamente.

Fechar caller e revisão, depois rodar diagnóstico limitado sob Original, sem
Source/SQL. Preservar limites/stdout16KiB/stderr32KiB/truncation/exit/progresso
até32; sucesso desse diagnóstico significa DIAGNOSTIC_CAPTURED_ONLY. Corrigir a
causa demonstrada e validar a menor unidade aplicável. Adapter operacional,
catálogo, guard/proveniência e engine de Usuários/P3 ainda estão abertos;
preparo estático local não prova travessia, EOF ou banco.

## Banco e comparação externa: o que continua aberto

As propostas estruturais fechadas estão em
`Banco\structural-closure-after-adoption68-0636-01\FECHAMENTO.json`, SQL-PINS.json
e RESUMO.txt: 18SQL/quatrogrupos (Fretes, Localização, Usuários, P3). Raster é
outro grupo, com sete DDL/cinco alterações. Nada foi aplicado; os pedidos de
exceção específica não receberam resposta. Continuar código/local independente;
não transformar “continue” em permissão para DDL/grant/Flyway.

Excel de referência: Downloads/Relatório de Exceções - 2ª Semana.xlsx e
Relatório de Exceções - 3ª Semana.xlsx, 636linhas/290+346 códigos disjuntos.
Confronto parcial Raster já comprovou 108códigos+placas e previsões de paradas
até minuto, admitindo múltiplas ocorrências. Os outros528 NÃO são faltantes
comprovados; papel da parada, recorte, nomes, fusos/segundos e paridade integral
seguem abertos. O Excel é referência verdadeira para seu recorte, sem inferir
que cada linha corresponda automaticamente à mesma entidade/campo da API.

Site ESL: somente consulta/filtro/download, nunca escrever/enviar/salvar,
alterar/logout/fechar a aba original. Última situação: sessão expirada/2FA,
sem autenticação fornecida. Não tocar repetidamente em login inalterado.

## Limites operacionais e entrega

SQL16.0.1000.6, contas, credenciais, identidades, permissões, serviços, listeners,
certificados/TLSglobal, PM2, WMS, V1 e produção preservados. Usuário confirmou V1
pausado. Nenhum processo foi encerrado para esta troca. Antes de efeito novo,
conferir estado atual; seisJVM/70076 de0645 são fotografia, não passe permanente.
Um executor pesado Root por vez; memória livre>=4096MiB e commit<=85%, Original
finito1GiB. API serial, intervalo mínimo2s e limites do contrato; não presumir
quota contratada infinita; STOP429/sem retry ou fallback manual nos contratos.

Original: `C:\Users\suporte\Documents\projetos\.runtime\memory-control\Executar-Batch.ps1`,
SHA `91D96F8DE3116004A38B3CA470769CA8D4BB82791C0ED485F793CC4E4C2DBCE9`.
PS7: `C:\Users\suporte\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe`.
JSON em PS7: ConvertFrom-Json -AsHashtable -DateKind String. ExitCode ao vivo do
Original pode ser UInt32: correção dos dois guards já validada e usada em0645.
Não eliminar hashes, leases, controles ou limites para acelerar. File.Replace
exige backup concreto. Sempre commit E push das alterações revisadas.

Runtime canônico1779 e scannerF27B permaneceram inalterados na publicação0645;
scan dos quatro textos foi real, zero achados. Esta passagem altera somente
quatro documentos e exige novo scan/publicação. Não repetir a suíte completa
ou reconstruir AST em uma troca exclusivamente documental.

## Chats e sequência de transferência

- Antigo supervisor: `01a1455c-57df-7983-b86f-3b4e9dc3e19c`, ETL V2 — Supervisor.
- `01a17093-42c9-7641-aa5c-519bcffe3b5e`, ETL V2 — Revisão do banco.
- `01a17092-71ca-7d91-a166-7f1410137efe`, ETL V2 — Contratos e demais entidades.
- `01a17091-5e7a-7932-80b4-7b769589846c`, ETL V2 — Fluxo e usuários.

Os três auxiliares estavam notLoaded, com últimas tentativas antigas falhas
por capacidade de modelo; suas entregas foram superadas/absorvidas pelas provas
atuais. Agentes filhos desta sessão estão concluídos. Arquivar somente ETL,
preservando conversas, arquivos e trabalho WMS/AVALIACAO. Criar supervisor novo,
fixar, receber confirmação de leitura e transferir execução antes de arquivar
o supervisor antigo por último. Não abrir novos auxiliares por hábito.

## Retomada imediata — três ações

1. Receber a supervisão: ler a entrada de continuidade, conferir Git/pins e o
   ledger sem disparar efeitos; confirmar os limites e o próximo trabalho.
2. Após a transferência, concluir o caller diagnóstico de Usuários/P3, revisar,
   executar com limites e localizar a causa real; corrigir e validar no escopo
   permitido. Não aplicar DDL nem fingir13PASS/EOF por um diagnóstico.
3. Manter a matriz entidade/origem/banco/comparação e avançar os próximos
   trabalhos elegíveis, com LatestMainTyped/escopos próprios antes de nova
   Source e exceção concreta antes de estruturas proibidas. Retenção definitiva
   e fidelidade ESL/Excel precisam de provas próprias antes de declarar entrega.

Condição final: todas as entidades exigidas extraídas integralmente, dados
gravados/validados no banco correto e confrontados no mesmo recorte com a
referência adequada, com divergências resolvidas/documentadas e commit/push.
Esta passagem não recebe aceite de extração ou implantação.
