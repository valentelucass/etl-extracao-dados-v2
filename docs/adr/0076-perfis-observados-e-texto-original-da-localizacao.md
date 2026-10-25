# ADR 0076 — Perfis observados e texto original da Localização

25/10/2026. Responsável técnico Root;responsável de negócio ainda não confirmado.
Origem:provas ESL documentadas nos checkpoints0557–0570 e requisito do usuário de
extração DataExport completa. Complementa ADR0075 sem aceitar snapshot ou retenção.

RT-DE-OBS-001: pedido observado deve transportar o release exato e a semântica
do request vinculada ao guard. Três perfis imutáveis/fechados selecionam6399,
6906,8656 e as versões2026-10-24.p1b-*-observed.v1 já fixadas. Somente BODY,
retry1,preferred-only,per1..100,sem order_by e sem updatedAt. Datas/per/template/
release/modo raw/configuração compõem o runtime derivado; invocationId isolado
não cria outra identidade. O parser não recebe flags de permissão nem pins livres.
Exemplo:retry2 mesmo com fingerprint coerente recusa antes de INFO,zeroenvios.
Bundle guarda callback privado dos dois adapters HTTP finais/materializados;
wrapper conserva essa prova,gate compara sourceSelection antes de fornecer INFO.
Testes:DataExportObservedInfoPolicyTest,DataExportObservedFlowTest,
DataExportObservedRequestProfileTest,RuntimeObservedEntityRequestTest.

RT-DE-OBS-002: Localização observada26 recebe texto original por offsets validados
da String completa ROOT_ARRAY. Duplicatas/trailing/índices/limites inconsistentes
recusam. Nunca reconstruir raw por JsonNode.toString. A página conserva documento
único e slices lazily;withObservation preserva associação. Pipeline nomeado mantém
batch100/ordinais locais/numeração;mapper26 explícito é obrigatório,rawausente recusa.
Exemplo:volume numérico-0 conserva seu lexema e continua recusado pelo domínio;
0 é positivo. Testes:DataExportRawRecordSlicesTest e DataExportObservedFlowTest.
Não promete conservar todos escapes/espaços no payload canônico de persistência.

LEGACY não carrega recursos observados;construtor público6/default/order/igualdade/
hash e JSON genérico6 preservados. Reflexão vê7recordcomponents,documentado/testado.
Fretes id asc/globalruntimefingerprint e mapperLocal17 default permanecem.

Qualificação:q08 todos150JUnit distintos/7IT/strictcompile/style/package PASS.
Root conferiu23fontes e árvore nãooverlay,classes/recursos e8libs byte a byte,
adotando o mesmo JARE05BEBDD94894ED7728880C26CFD8910CF198FC999D6DDA4B4923EBA8509E045.
Backup/receipt/evidências em campanha flow-root-adoption e candidato-fluxo-p1b-p2.
Semdeploy/SQL;proceduraLocal17 continua pendente. Não é fullverify/Mainadmission.
