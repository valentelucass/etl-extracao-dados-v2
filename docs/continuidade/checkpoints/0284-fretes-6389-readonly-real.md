# Checkpoint 0284 — Fretes 6389 read-only real — 22/09/2026

## Identificação e objetivo

- Anterior: `0283-coletas-6908-readonly-real.md`, SHA-256 `2934648bc04649afa68d8d4cd3ba3a94a62f12a9cb4747a6b1581d12be0689d8`.
- Objetivo: completar o macrobloco de primeira observação real de Coletas/Fretes dentro da allowlist, sem escrita nem aceite antecipado.
- Estado: `TESTADO_NA_CAMADA` Data Export read-only; Fretes `TRAVERSAL_PAGE_LIMIT_REACHED` sem terminal; contrato sintético local difere da metadata real observada.
- Critérios: `AGENTS.md`, `STATES.md` V2-025d/P17/V2-041, catálogo de primeira onda.

## Autorização e limites

- O usuário autorizou expressamente prosseguir com as credenciais existentes apesar da exposição. A exceção é somente para estas sondas read-only `curl`; V2-041 não recebeu aceite de rotação.
- Rodada Fretes: uma janela fechada de um dia; `GET /info` e até quatro páginas `GET_WITH_QUERY` com `per=100`, cinco chamadas seriais, intervalo 3 s, conexão 10 s, total 30 s, 10 MiB por resposta, sem redirect/retry/fallback.
- A ordem exata ficou no diretório privado da rodada; nenhum valor de segredo, URL, payload ou ID foi versionado ou mostrado.
- Recuperação: nenhuma mutação; resultado confirmado por processo e resumo, sem repetição após teto.

## Alterações e decisões

- `STATES.md` recebeu o resultado sanitizado e a diferença de contrato. Baseline sintética, manifesto e contadores históricos permanecem intactos.
- O `/info` atual declara 110 campos e `id`, enquanto a fotografia anterior registrava 109 sem `id`. Os nomes históricos completos não foram versionados; não afirmar que `id` seja o único delta apenas pela contagem.
- `finished_at` da baseline sintética não aparece na metadata nem nas páginas atuais. Os tipos do `/info` permanecem não declarados. O guard classifica campo esperado ausente ou tipo alterado como quebra; não promover a baseline atual para runtime real sem release qualificado.
- O mapper permite ausência de `finished_at` como fallback, então não houve correção de código por mera ausência; a semântica de performance ainda exige evidência de negócio.

## Execução e evidência

| Passo | Camada | Teto | Observado | Evidência privada |
| --- | --- | --- | --- | --- |
| Travessia 6389 | Data Export read-only | cinco chamadas | cinco HTTP 200; 410 linhas físicas/400 entidades nas quatro páginas, IDs não nulos, zero sobreposição; exit 1 por teto não terminal | `target/fretes-6389-traversal-20260922-01/summary.json`, SHA-256 `1b1692b464758d9248a5692d5adcb9073c622363d05e5acb74d3558f316a2fa1` |
| Metadados atuais | `/info` read-only | mesma chamada da travessia | 110 campos, filtros obrigatórios presentes, `id` declarado, `finished_at` ausente, tipos não declarados | mesmo recibo privado |
| Higiene do recibo | filesystem local | três diretórios desta campanha | arquivos ignorados pelo Git; nenhum marcador `Bearer` ou URL encontrado | leitura local sanitizada |

- `git diff --check` passou. `Test-Gpt56ChatTrail.ps1` saiu 1 em `HANDOFF_PIN`, bloqueio histórico preservado; não reescrever manifesto/ledger para fazê-lo passar.
- Nenhum teste Java, SQL ou schema foi repetido, pois os respectivos bytes não mudaram nesta unidade.
- Nenhum HTTP não-2xx, `429`, retry, GraphQL, 4924, Java, SQL, DDL/DML, persistência, deploy ou cutover.
- Aceites fechados: nenhum P17/P20, V2-041, paridade, completude ou publicação. A prova local do código permanece histórica e separada.

## Retomada imediata

1. Qualificar um release de contrato real para Fretes com nomes/tipos disponíveis e decisão de fallback; não alterar manifesto sintético por delta de contagem.
2. Para completude de Coletas/Fretes, planejar campanha própria com janela que caiba em teto conservador ou particionamento aprovado, mantendo página terminal e oráculo independente quando exigido.
3. Só executar JAR contra ESL quando houver escopo operacional próprio e release/autoridade compatíveis; a sonda `curl` não transfere essa autorização.

Condição de parada: teto/erro/`429`, limite de entidade inválido, contrato real incompatível ou falta de autoridade do runtime. As sondas desta campanha terminaram, sem chamadas remanescentes.
