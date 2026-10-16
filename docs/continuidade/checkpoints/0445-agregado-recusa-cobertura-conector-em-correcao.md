# Checkpoint0445 — pacote recusado por cobertura; conector em correção causal

Data:05/10/2026. Anterior:0444, SHA256 `360D0E938819A85D7830F8C671E2437CA5C13F880D3FC78C1DB55BB46807B6D1`.
STATES é canônico; AGENTS e ADR0060 delimitam a autoridade. CONTEXTO_GLOBAL continua ausente.

Objetivo do usuário: preparar o banco de testes e extrair todas as onze entidades, mantendo dados e a versão do SQL Server. Primeiro marco permanece Coletas parcial em ctl/stg, com commit conhecido e readback independente. Estado: IMPLEMENTADO_NAO_QUALIFICADO no agregado; sem extração SQL nesta unidade.

## Autoridade e limites

`USER_20261005_E3_TEST_RETAINED`, somente localhost:1433/ETL_SISTEMA_V2_SHADOW, sa existente por provedor externo e pin E3 exato. Não alterar versão16.0.1000.6, contas, permissões, serviço, certificados, trust global ou produção. V1 permanece parado por decisão do usuário, sem ação PM2. SAMPLE BF inalterado.

Nenhum despacho ou reserva física emitido. SQL0/HTTP0/commit0/DDL0 nesta unidade. Primeiro catálogo proposto:1conexão/até12SELECT/10s/60s; captura e confirmação posterior exigem unidades próprias. Banco único executor SQL; Root documentos/CI; Runtime pacote/controladores; Fontes adaptadores.

## Evidência e decisão

Unidade privada: `target/test-database-all-entities-20261005/`; inventário inicial, agregados01/02 e seus FAILs preservados.

| Prova | Resultado observado | Evidência |
| --- | --- | --- |
| Agregado03 | Surefire2660/0/0/5 e Failsafe7/0/0/0; formatter/checkstyle PASS; verify recusado pelo JaCoCo controle611/800linhas, abaixo80%; drift0 | runtime/aggregate03/receipt.json, SHA256 c239549949b08b4bda669f6bf08a2547204f7211b9c1bf96fab08503662b1c4c |
| Novos testes conector | 7/0/0/0 e formatter/checkstyle PASS; união JaCoCo em produção idêntica675/800; pós-processamento XML corrigido por XPath sem repetir suíte | banco/connector06/ |
| Revisão conector | Setup do escopo anterior ao finally pode escapar da sanitização/cleanup; RED e menor correção em andamento, ainda sem freeze05 | TestRetainedSaJdbcConnector e novo teste Banco |
| Users observacional | 10/0/0/0, factory JVM e formatter/checkstyle PASS;49membros conferidos sem drift | supervisor-users-observation-review01.json |
| Patch futuro de arquitetura | RED19/1; GREEN22/0;3APIs exatas e contraprovas100/101/vazio;35membros conferidos; não aplicado | supervisor-future-patch-review01.json |

Agregado04 preparado, com guarda até freeze05/testes causais. Dez paths futuros permanecem excluídos somente do snapshot Coletas; nenhum limiar/exclusão de cobertura alterado. JAR03 não admitido e nenhum preflight final/freeze físico emitido.

`TEST-OBS-PHYSICAL-001` autoriza apenas preparo offline de contrato distinto: corpo inteiro/envelope fechado,0/1registro principal,até1item associado conhecido, recusa integral de excedente/estrutura desconhecida. O normalizador atual não satisfaz isso; sourceEntities/remotePer continuam UNVERIFIED, binding nulo e completude falsa. Nenhum despacho de dados das sete fontes por essa decisão. A pergunta documental opcional continua sem resposta presumida.

Não há efeito externo incerto para reconciliar, novo aceite físico, checkbox, promoção ou cutover. Processos ativos são somente qualificações locais dos builders, com recibos próprios; nenhuma rotina de retomada foi instalada.

## Próximas ações

1. Banco concluir correção causal/novo freeze; Runtime qualificar agregado04 e pacote/controladores. Preservar qualquer FAIL novo e corrigir sua causa antes de prosseguir.
2. Root revisar bytes finais e despachar uma prova catalogal; Banco executar com PREPOST atual de versão/hash e reserva. Somente contrato físico provado permite captura própria seguida de readback independente.
3. Fontes concluir contrato observacional offline enquanto o marco Coletas prossegue; Root avaliar destinos e elegibilidade física das demais entidades sem transformar observação em identidade/completude.

Parar no primeiro desvio, não2xx/429, teto inválido ou resultado incerto. Não repetir efeito nem limpar dados retidos. Conclusão depende de evidência por entidade, não da duração da sessão ou quantidade de testes.
