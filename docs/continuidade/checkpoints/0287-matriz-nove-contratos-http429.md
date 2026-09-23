# Checkpoint 0287 — perfil ESL interrompido por HTTP 429 — 22/09/2026

## Identificação e objetivo

- Anterior: `0286-matriz-nove-contratos-tentativa.md`, SHA-256 `70102c2c79a7057e7d3a018d56a6f635d0939ab5823480de105bede3a4c77a02`.
- Objetivo do usuário: juntar mais trabalho em um bloco e avançar nos contratos reais sem esperar respostas dele.
- Estado: `TESTADO_NA_CAMADA` API read-only até a parada obrigatória; `IMPLEMENTADO_NAO_QUALIFICADO` para a sonda corrigida após o HTTP 429.
- Critérios: `AGENTS.md` e `STATES.md` V2-025d/V2-041.

## Autorização e limites

- O pedido atual renovou a exceção pontual para uma rodada read-only dos nove templates. G01, SQL, Java externo, credenciais, produção e cutover permanecem fora deste efeito.
- Ordem privada: `target/nine-contracts-20260922-02/order.json`, com janela de um dia distinta da anterior, até 18 chamadas seriais, `per=3`, uma página por template, três segundos entre chamadas, 10/30 s, 10 MiB, HTTPS, sem redirect/retry/fallback.
- Recuperação: recibo e processo foram reconciliados. Nenhum `curl` ficou ativo. Não repetir automaticamente a chamada após o 429.

## Alterações e decisões

- `Invoke-DataExportNineContractProbe.ps1` passou a emitir nomes técnicos sanitizados, tipos declarados e fingerprint do schema de `/info`, mais nomes de campos de topo e contagens de candidatos em `/data`, sem valores de negócio.
- A extração de `data` foi corrigida para preservar arrays de um item. O autoteste demonstrou o defeito local; o formato da resposta anterior de Fretes permanece desconhecido.
- HTTP não-2xx agora é classificado antes de interpretar JSON. O recibo histórico da rodada desta unidade permaneceu imutável, embora seu `stop_reason` bruto seja `INVALID_JSON`; o status 429 registrado determina a classificação operacional correta `HTTP_429`.
- `STATES.md` e trilha sincronizados; nenhum contrato/fingerprint de baseline foi promovido.

## Execução e evidência

| Passo | Camada | Observado | Evidência |
| --- | --- | --- | --- |
| Perfil 6908 e 6389 | API Data Export read-only | quatro chamadas: 200, 200, 200, 429; parada; sete templates não chamados | `target/nine-contracts-20260922-02/summary.json`, SHA-256 `37e5f4e612315b0917b4c769df484a36628364539bc188ce34b8746bf877ac6` |
| Metadata | API read-only | 6908: 31 campos/seis filtros; 6389: 110/16; nomes técnicos seguros, sem colisões; `finished_at` ausente em 6389 | mesmo recibo privado |
| Página Coletas | API read-only | três linhas, três `id` escalares distintos, `per=3` verificável | mesmo recibo privado |
| Correção e autoteste | PowerShell offline | HTTP 429, 503 e 200; metadata objeto/array, nomes inseguros/duplicados, `data` com array de um item/objeto/vazio, identidade e expansão passaram | `-SelfTest`, zero rede |

- `stderr.txt` vazio; nenhum processo `curl` ativo após a execução. Nenhuma mutação externa, banco, Java ou deploy.
- Aceites fechados: nenhum. V2-025d/V2-041/P17/P20 continuam abertos, 67/115.
- A página de perfil não prova terminalidade, timezone da fonte, completude ou paridade. O 429 não prova quota global nem invalida os contratos individuais.

## Retomada imediata

1. Não repetir a janela interrompida. Uma futura rodada dos sete templates remanescentes exige ordem, teto e janela próprios, respeitando a condição de 429.
2. Manter metadata real observada como evidência privada até uma campanha que cubra os nove contratos e permita atualizar a matriz/baseline sem inferir nomes ausentes.
3. Para V2-025d, obter os aceites originais de owner, tradução temporal, identidade, timezone e oráculo de completude; preservar o gate G01 independente.

Condição de parada desta unidade: HTTP 429. Condição de conclusão de V2-025d não alcançada.
