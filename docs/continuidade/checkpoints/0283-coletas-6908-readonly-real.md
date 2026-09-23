# Checkpoint 0283 — Coletas 6908 read-only real — 22/09/2026

## Identificação e objetivo

- Anterior: `0282-primeira-leitura-antes-dos-aceites-reais.md`, SHA-256 `6e35f10a5cf6400a48af0dd99085167f2e9aeba7381874a52f4ef5ace7d9930f`.
- Objetivo: iniciar prova real do contrato Data Export de Coletas sob a decisão explícita do usuário de prosseguir com a credencial atual.
- Estado: `TESTADO_NA_CAMADA` API read-only; terminalidade/completude `NÃO_COMPROVADA` pelo teto da travessia.
- Critérios: `AGENTS.md` allowlist 6908 e parada por `per`/teto; `STATES.md` V2-041 e P17.

## Autorização e limites

- O usuário dispensou expressamente a rotação prévia para este teste. Exceção delimitada a sonda read-only `curl`, sem concluir V2-041 ou autorizar Java/produção.
- Primeira ordem: janela fechada de um dia, `per=2/5`, até duas páginas cada, cinco chamadas totais, 3 s entre chamadas, timeout 10/30 s, 10 MiB, sem redirect/retry/fallback.
- Segunda ordem causal: mesma janela, `per=100`, até três páginas mais `/info`, quatro chamadas, mesmos limites. Respostas e segredos só em memória; recibos sanitizados em `target/`.
- Recuperação: nenhuma mutação; não repetir automaticamente a janela após o teto.

## Alterações e decisões

- `STATES.md` registra a exceção de escopo, a pré-condição e os resultados. O working tree preexistente não foi limpo ou descartado.
- A segunda ordem nasceu da falta de terminalidade nas duas páginas iniciais, não de erro HTTP ou retry.
- `per` limita entidades distintas; expansão física de 137/100 e 120/100 foi aceita com ID escalar não nulo e sem mais de 100 entidades.
- A terceira página da travessia ainda tinha dados. Não inferir terminal nem promover P17, paridade, watermark ou publicação.

## Execução e evidência

| Passo | Camada | Teto | Resultado | Evidência privada |
| --- | --- | --- | --- | --- |
| Perfil inicial | Data Export 6908 | 5 chamadas | exit 0; cinco HTTP 200; `/info` 31 campos; páginas 2/2/5/5, IDs válidos e sem sobreposição por tamanho | `target/coletas-6908-first-read-20260922-01/` |
| Travessia | Data Export 6908 | 4 chamadas | quatro HTTP 200; 326 linhas físicas/252 entidades em três páginas; zero sobreposição entre páginas; exit 1 por `TRAVERSAL_PAGE_LIMIT_REACHED` | `target/coletas-6908-traversal-20260922-01/` |

- Nenhum stderr, HTTP não-2xx, GraphQL, 4924, Java, SQL, DDL/DML, persistência, deploy ou cutover.
- Aceites fechados: nenhum. V2-041 permanece aberto apesar da exceção read-only.

## Retomada imediata

1. Executar a ordem independente de Fretes 6389 já delimitada no cabeçalho de `STATES.md`.
2. Classificar o resultado de Fretes sem ampliar teto ou repetir manualmente após erro.
3. Para Coletas, obter prova de terminalidade em campanha própria futura e oráculo independente antes de avaliar P17/P20; não converter a página curta em fim.
