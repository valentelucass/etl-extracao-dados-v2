# Checkpoint 0285 — identidade Coletas 6908 read-only — 22/09/2026

## Identificação e objetivo

- Anterior: `0284-fretes-6389-readonly-real.md`, SHA-256 `60a309f578b3c58566d4af384e554d248a6dbda28f4ede9ec608e4e346f753c4`.
- Objetivo do usuário: avançar após verificar o que foi concluído e registrado no `STATES.md`; executar uma prova nova e limitada de entrada Data Export e saída GraphQL.
- Estado: `CONCLUÍDA_NA_CAMADA_DE_SONDA_READ_ONLY`; paridade de identidade apenas da janela observada.
- Critérios: `AGENTS.md` allowlist e limites; `STATES.md` V2-025d, V2-012a/b/c, P17/P20, V2-041; sonda de identidade versionada.

## Autorização e limites

- O usuário decidiu continuar com as credenciais existentes e autorizou avançar; a exceção ficou delimitada à sonda `curl` read-only. V2-041 continua aberto.
- Alvo: Coletas 6908 Data Export × GraphQL, janela fechada independente de um dia. Duas travessias `per=50/100`, até três páginas cada; GraphQL até duas páginas; máximo oito chamadas seriais, pausa 3 s, timeout de conexão/total 10/30 s, 10 MiB/resposta, sem redirect/retry/fallback.
- Ordem exata privada em `target/identity-6908-20260922-01/order.json`; nenhum token, URL, cursor, payload ou ID no Git. Recuperação sem mutação: reconciliar processo e recibo antes de qualquer nova ordem se resultado incerto.

## Alterações e decisões

- `STATES.md` recebeu pré-efeito, resultado e conferência explícita das caixas. `BLOCOS_ETAPA_2.md`, trilha e este checkpoint foram sincronizados.
- O conjunto observado tem uma entidade expandida em quatro linhas físicas. Duas páginas vazias terminais Data Export e uma página GraphQL terminal permitem comparar a janela, não inferir snapshot global ou estabilidade temporal.
- Nenhum release sintético foi promovido nem houve alteração de código, schema, manifesto de contrato, banco, JAR ou produção.

## Execução e evidência

| Passo | Camada | Teto | Observado | Evidência privada |
| --- | --- | --- | --- | --- |
| Identidade Coletas | API read-only `curl` | oito chamadas | saída 0, cinco chamadas, stderr vazio; Data Export `per=50/100` com uma página de quatro linhas/uma entidade e uma página vazia em cada travessia; GraphQL terminal com uma entidade; conjuntos de chave natural e ID canônico iguais | `target/identity-6908-20260922-01/summary.json`, SHA-256 `aa3a6e4d143024ccebca112757bc0fff480c2d0ebb22d6315e3ac191725ef1ed` |
| Higiene do recibo | filesystem local | três arquivos privados | ordem, resumo e stderr ignorados pelo Git; sem marcador de Bearer, URL ou valor de ID/cursor/token | inspeção local sanitizada |

- Aceites canônicos fechados: nenhum. V2-025d exige a matriz de nove requisições; V2-012a/b/c exigem caracterização, oráculo e/ou SQL; P17/P20 mantêm critérios de campanha; V2-041 exige rotação/invalidação e saúde. O estado da unidade de sonda, somente, é concluído.
- `git diff --check` e UTF-8 estrito passaram. `Test-Gpt56ChatTrail.ps1` saiu 1 em `HANDOFF_PIN`, falha histórica preservada. Sem alteração Java/SQL/schema, suas suítes não foram repetidas.

## Retomada imediata

1. Qualificar release de contrato real de Fretes com nomes/tipos e decisão de fallback antes de usar o JAR contra ESL.
2. Planejar campanha de Coletas/Fretes com janela representativa, oráculo independente e terminal sob teto próprio, sem repetir a janela/teto encerrados.
3. Executar V2-025d somente após condições de alcance/autoridade de cada template e da matriz completa; não inferir os sete templates restantes desta prova.

Condição de parada: limite, erro, `429`, identidade inválida ou falta de autoridade para ação seguinte. As cinco chamadas desta sonda terminaram; nenhum processo ou chamada remanescente.
