# Checkpoint 0010 — Raster: aceites locais e fechamento offline

08/09/2026, sucessor de 0009. Decisão delegada MANTER registrada no ADR 0039;
necessidade, owner da decisão, consumidores declarados/impactos e autorização
local estão em `docs/catalogos/raster-contrato-local/decisao.json`.

V2-034a e V2-025c satisfazem seus critérios originais no escopo local. V2-025c
formaliza contrato TRANSITIONAL com lacunas explícitas, sem alegar identidade ou
implementação. O validator passou 18 aspectos, 51 campos e 16 casos offline;
12 contraprovas com hashes atualizados foram corretamente recusadas. Logs:
`target/bloco56-raster/contract-01.log` e `contract-guards-01.log`.

STATES/trilha registram somente esses dois aceites: 67/115 (58,3%), 48 pendentes,
191 rotas abertas, zero AGORA. Fase 1: 39/52 (75,0%). Não houve checkbox novo.
V2-009c continua bloqueada: faltam wire type/escopo/estabilidade de raiz e parada;
o contraexemplo posicional é sintético. V2-034b não foi iniciada. P08–P11/V2-029–032
continuam bloqueadas pelos requisitos já registrados, sem sonda repetida.

Inventário inicial próprio com 1.396 arquivos e oito snapshots anteriores;
manifests históricos, Java, migrations e ledgers preservados. A nova sucessão
confere cada delta e seus bytes anteriores, mantendo validadores históricos
contra suas próprias fotografias. Não existe exceção genérica para arquivos.
Zero fonte, SQL, runtime, grant, instalação, orçamento, rotação ou resultado
desconhecido. Os 1.138 testes Java permanecem históricos B55.

Próximas ações:

1. Conferir `target/bloco56-raster/final-verification.json` e
   `completion-receipt.json`. Se ausentes, concluir somente a rodada offline de
   sucessão/contraprovas, continuidade/B55, trilha, scanner, UTF-8 e diff; este
   checkpoint não antecipa seus exits. Se presentes, não repetir essa rodada.
2. Receber garantia/oráculo pertinente de uma identidade ESL/Raster, conforme os
   pacotes concretos. Somente com identidade integralmente provada implementar
   sua vertical local e preparar efeitos físicos separados quando necessários.
3. Receber provas de retenção e políticas/consumidores operacionais da Fase 1.
   Decisão Raster já foi tomada; não perguntar manter/retirar novamente.

Recuperar somente deltas próprios após conferir hashes/edições posteriores.
Preservar todos os recibos, testes falhos e snapshots. Nenhuma restauração de
banco se aplica ao trabalho local aqui realizado.
