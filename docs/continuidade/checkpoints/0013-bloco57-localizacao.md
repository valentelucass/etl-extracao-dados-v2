# Checkpoint 0013 — B57 B, Localização integrada

Objetivo A–D e limites da instrução efetiva preservados. A e B estão
TESTADO_NA_CAMADA; C/Fretes e D/documentos/pacote continuam autorizados.
MapperCharacterization reutiliza o parser real, recorta o JSON original de cada
linha após validação e chama o overload String existente de Localização.
Não houve alteração produtiva no mapper LOC nem criação de relação com Frete.

27 casos sintéticos LOC cobrem 17 paths, identidade exclusiva, tri-state,
zero, léxico numérico, DECIMAL(38,9), escala/expoente/locale inválidos, status,
proveniência, campos sem fonte, gap/overlap e datas. Status desconhecido preserva
bruto e é não terminal conforme LOC-06; publicação continua fora do escopo.

Maven Java17 offline: test-B-01.log passou 20 testes, zero falhas/erros/skips,
incluindo 39 casos COT, 27 LOC, oito testes de limites e mapper LOC existente.
Limites recusam bytes/nós/paths/profundidade, páginas/linhas, lacuna, JSON inválido,
leitura falha, expectativas faltantes e quarentena inesperada. Relatórios não
persistem valores e são determinísticos. test-guards-01.log: oito testes PASS.

Fonte, SQL, runtime, instalações, orçamento e efeitos externos desconhecidos: zero.
67/115, 48 pendentes, nenhum novo aceite. Recuperação pelo inventário/cópias
iniciais e deltas B57; guardar todos os relatórios/logs. Processos Maven focados
terminaram. Nenhum efeito físico ou ledger a reconciliar.

Até três ações: (1) C Fretes/Data Export sem GraphQL remoto; (2) D pergunta
específica sobre os limites Data Export já documentados, pacote futuro e holds;
(3) verify offline final, validadores estáticos, hashes, diff/recuperação.
Anterior: 0012-bloco57-cotacoes.md; SHA-256 64da75f73a32f439d40f852829fd63ec99cac6e424bec0de18bfe2217b718718.
UTC: 2026-09-09T03:03:47.0535055Z.
