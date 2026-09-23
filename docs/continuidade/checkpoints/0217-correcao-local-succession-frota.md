# Checkpoint0217 — recuperação e correção local, validação em curso

CORRECAO_LOCAL_POS0216. 2026-09-21. Unidade: recuperação dos bytes históricos e
implementação da sucessão explícita; não é fechamento dos testes.
Anterior: `0216-preparacao-offline-p10-p21.md`, SHA-256
`6625624e2dcafe0b82513bbe6ea938f691c58589fae82679366711b94453d0b5`.

Ordem efetiva: encontrar a causa, corrigir, testar e concluir dificuldades locais.
Limites anteriores mantidos: sem subagentes, rede, segredos, banco ou efeito
externo; zero fonte real, produção, rotação, deploy, cutover, paridade real,
revisão humana e aceite V2. Nenhum orçamento ou autorização foi renovado.

Recuperados os18 arquivos divergentes da revisão P06 e o mapper histórico da
frota, todos com o hash esperado, sem mudar o conteúdo dos originais. Evidência:
`target/correcao-local-pos0216-20260921-01/recovered-targeted.json`.
Inventário inicial3680 e snapshots próprios em `baseline.json` e `before/`.
Novo módulo, composição P06 e adaptação do validador da frota: ver
`docs/catalogos/continuidade-pos0216/RELATORIO.md`. O único delta do mapper é
o tratamento textual de status, anterior a esta rodada; runtime não editado.

As buscas recursivas próprias foram interrompidas após fontes exatas serem
localizadas; resultado parcial preservado, sem efeito desconhecido. A busca
dirigida terminou com19 recuperações por hash. Testes pendentes neste checkpoint.
As falhas antigas continuam preservadas. Contadores39/45 e67/115 sem mudança.

Próximas ações: 1. testar sucessão, contraprovas e trilha/frota; 2. corrigir toda
regressão local e conferir preservação; 3. registrar fechamento em novo checkpoint
e atualizar RETOMADA após conferir o hash. Recuperação por diff próprio.
As entradas externas ausentes permanecem explicitadas na matriz das seis P,
com owner-papel e origem, sem inventar autoridade ou evidência.
