# Checkpoint 0024 — regressão local e início do verify final
Data: 2026-09-09. Anterior 0023-bloco58-usuarios-local.md,
SHA-256 148eef27b9957fd1a5df222288482cd96f2b7faa8f2ff16a2023558d5af9d40c.
Objetivo adotado A–D preservado. A/B/C: TESTADO_NA_CAMADA; D: EM_EXECUCAO.

Autorização do usuário: implementar/testar/fechar Bloco58 exclusivamente local.
Sem API real, SQL, instalação, runtime físico, credencial, agenda, cutover,
commit/push ou orçamento/ledger novo. Mesmos tetos do checkpoint0023.
Inventário/recuperação: target/bloco58-local/initial-inventory.json e initial/.
Nenhum efeito desconhecido ou processo ainda ativo antes do próximo verify.

C alterou somente o destino configurável de relatórios de dois testes B57,
mantendo defaults, assertions e fixtures. Não alterou contrato/parser/mapper LOC,
COT, FRE ou MAN. String conserva léxico; JsonNode recusa números com
UNVERIFIED_NUMERIC_WIRE_LEXEME; contrato LOC pode recusar antes de staging.
Produção mudou exclusivamente nos três formatters de Coletas já reproduzidos.

focused-c-01: exit0,124 testes,zero falhas/erros/skips. Relatórios isolados provam
39 COT +27 LOC +43 FRE =109 casos B57, além de quatro comparações de caminhos LOC.
Inclui mappers MAN/COT/FRE/LOC, pipeline DataExport e nova caracterização Coletas.
Log SHA-256 65669233b49ec87e15fe803c027c171023064f76d1e7c2d827c365782ed978c6.
Result SHA-256 7e12a08c8bc0ae67caa16fe1b9507c9e727d1d6ec78b362a4e6ecb41b0ec0700.
A98/B94 e oito gates estáticos são execuções anteriores deste bloco, não B57.

STATES atualizado antes da trilha; ambos preservam integralmente a fotografia
inicial e nenhum checkbox mudou. Sucessão preparada no Test-Bloco58Local.ps1
e ponte exata no Test-Bloco57Complemento.ps1; manifest/guards ainda pendentes.
Nenhum PASS desses validadores novos alegado. Não editar manifests antigos.

Próximas ações:
1. Executar Invoke-Maven.ps1 -Label verify-final-01 -Goal verify, Java17,
   offline/heap512, sem clean/perfil, e conferir logs/XML/skips/cobertura.
2. Criar snapshots/manifest B58, validar sucessão B57/B55 privada e guards
   (falso aceite, histórico e fonte habilitada), scanner offline/autoteste.
3. Sincronizar resultado final STATES → trilha → manifest, escrever diff próprio,
   reverse --check, recuperação e receipt com hashes do último delta.

67/115,48 pendentes,191 rotas abertas,zeroAGORA e zero novo aceite.
Q-COL/Q-USR/V2-012a/b/c aguardam oráculos/garantias/bindings próprios; os papéis
e artefatos estão na matriz do catálogo B58. SQL, relações, current/history e
paridade não são comprovados por staging em memória. Não há frente externa nova.
