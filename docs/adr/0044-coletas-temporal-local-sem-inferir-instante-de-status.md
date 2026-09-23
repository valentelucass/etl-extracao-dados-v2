# ADR 0044 — Coletas: validade temporal local sem inferir instante de status

10/09/2026. Decisão técnica do B63 local adotado pelo usuário. Complementa
ADR0023/COL-02 e ADR0043; não modifica releases, migrations ou aceites de fonte.
Responsável técnico: manutenção B63. Aceitante nominal de negócio: pendente.

## Fundamento e decisão

COL-TIME-01 é uma lacuna de fonte: o release corrente 6908 não contém
`status_updated_at`. As sete diferenças B62 não autorizam equivalência com
`updated_at`, `finish_date` ou tempo de captura. Permanecem abertas.
O [mapa](../catalogos/bloco63-temporal-local/MAPA-TEMPORAL.md) identifica
seleções, consumidores, precedência e limites das provas.

**COL-TIME-02 — horário local precisa determinar um único instante.** ADR0023
exige preservar valor inválido sem reinterpretá-lo e usar somente fallback
tipado válido. `LocalDateTime.atZone` corrigia silenciosamente o gap e escolhia
um dos dois offsets no overlap. Exemplo discriminante: `2018-11-04T00:30:00`
nunca ocorreu em America/Sao_Paulo; `2019-02-16T23:30:00` ocorreu duas vezes.
Nenhum dos dois fornece um instante único. Recusar sua interpretação temporal,
preservar bruto/presença/payload e percorrer o fallback aprovado. Havendo offset
explícito ou Z, respeitar o instante declarado, inclusive na transição histórica.
Essa validação não coloca a linha inteira em quarentena nem cria novo campo.

**COL-TIME-03 — data civil permanece data civil.** O início do dia no fuso
America/Sao_Paulo é um representante local para frescor, não hora do evento.
`atStartOfDay` permanece intencional: 2018-11-04 começa às 01:00 locais,
03:00Z. A decisão é distinta de corrigir um horário explicitamente inexistente.
Não copiar o `+00:00` do fallback SQL V1: em 2026-09-09 ele produz 00:00Z,
enquanto V2 produz 03:00Z. A divergência precisa constar da futura prova.

**COL-TIME-04 — precisão e conflitos não autorizam desempate artificial.**
Manter nanos no `Instant` Java e calendário UTC no JDBC. V010 armazena
DATETIME2(3); V1 usa DATETIMEOFFSET(0). O B63 não arredonda/trunca no mapper
para simular paridade física. Casos que distinguem instantes abaixo de 1ms
entram no pacote de qualificação SQL. Empate divergente continua conflito;
hash e ordinal não se tornam ordem de negócio. Terminalidade de linha é
preservada; a promoção depende dos gates conservadores descritos no mapa.

## Alternativas e limites

- Corrigir o gap ou escolher offset no overlap: rejeitado por inventar instante.
- Exigir offset em todo formato: desnecessário; horários locais unívocos têm
  zona explícita já aceita. Não utilizar o fuso do host.
- Rejeitar datas civis de início de horário de verão: desnecessário; o dia existe.
- Trocar fallback por UTC, updated_at ou observed_at: sem fundamento aprovado.
- Habilitar GraphQL complementar: apenas proposta no pacote C, dependente de
  identidade, autorização, proveniência e plano de remoção próprios.
- Alterar V010 ou o reducer de conflito: fora da correção Java. Preservar os
  contratos históricos; necessidades físicas são propostas revisáveis, sem SQL.

Regressões: `ColetaDataExportRecordMapperTest` (contraprovas de gap/overlap,
formatos, presença, fallback, precisão), `ColetasCurrentTemporalTest` (ROOT_ARRAY
→ gate → streamer → mapper → staging), B58 e Q-FND históricos preservados.
Teste sintético não prova a semântica da fonte nem promoção SQL física.
Rollback local: restaurar somente os deltas do B63 pelo inventário/before;
preservar relatórios e falhas. Nenhuma recuperação operacional é executada.
