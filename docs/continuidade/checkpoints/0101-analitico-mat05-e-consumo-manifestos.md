# 0101 — MAT05 e consumo de Manifestos

EM_EXECUCAO A–N, prosseguir integralmente sem perguntas/continue.
Predecessor0100 SHA256 375c6c8cdf65e91bd4d6595947f62dc69836168e259c86a94ec63d0ac3e4690b.
Request/before2704/snapshots/autorização e limites anteriores preservados.
Construção32/45 e aceites67/115 intactos; nenhuma nova unidade contabilizada.

V001–V078 instaladas/imutáveis; baseline acompanha. Próxima livreV079.
V072 MAT05; V073 SQL08/09/apresentação; V074 comparação temporal; V075 aliasCOL;
V076 componentes atuais; V077 competência temporal; V078 papéis opcionais/frota.
Qualificações/instalações com ledgers/reservas e contagens preservadas no target.
Geradores privados têm guard de existência; não repetir ou editar migrations
instaladas. Cópias DDL anteriores permanecem qualificadas em suas tentativas.

Provas observadas:

- physical-owned-fleet-01:3IT PASS (0100).
- physical-mat05-01:5IT,4falhas preservadas. Cenário não fornecia termos
  financeiros. Corrigido via nova captura existente com withFinancialBindings.
- physical-mat05-02:5IT,2falhas preservadas. Coleta alias virava vazio pelo
  wrapper {value:scalar}; expectativa de3linhas após replay deveria ser6.
- physical-mat05-time-alias-01:13IT,2erros preservados. MAT05/SQL08/09 passaram5;
  nova prova de offsets mostrou comparação bruta da competence_json na captura;
  nova prova de alias textual encontrou corretamente contrato somenteINTEGER.
- physical-mat05-time-alias-02/session7282 reconciliada exit0:13IT,0falhas/erros/
  skips; MANpreparação7, MAT05/queries5, aliasCOL1. Essa revisão vai atéV077.
- **physical-manifest-fleet-gates-01/session69503 em execução** ao checkpoint.
  Seletores AnalyticLaboratoryManifestsIT,AnalyticLaboratoryManifestGatesIT.
  Reconciliar exit/report antes de repetir. Pretende9IT sobreV078; sem PASS antecipado.
- metadata-eleven-contracts-01:11views/578colunas totais (inclui técnicas), SQL
  read-only. Onze contratos implementados:02/07/08/09/13–19. Oito restantes:
  01/03/04/05/06/10/11/12. Universo original673colunas intacto.

Implementação:

- mart.analytic_manifest_observation37campos derivados + snapshot92 imutável,
  current porrun/source_key com data/filial mutáveis, receipt e partição antiga.
  JdbcAnalyticMaterializations.manifests usa o mesmo pedido tipado/SQL8parâmetros.
- mart.analytic_manifest_freight deduplica Frete canônico; freight_path preserva
  DIRECT e MC/CF/CROSSWALK. Receita total usa união, soma direta confere exatamente
  agregado6399 e selo completo. BRL/MAJOR explícitos; missing/CONFLICT bloqueiam.
  Contagens/dinheiro por caminho e interseção expostos; nenhuma soma de produto.
- Lifecycle ativo/reativação, competência saída→criação, frota governadaV071,
  branch/tractor/trailers/driver por bindings e capacityKG. Disposições auditáveis.
  V076 restringe MC a picks na lineage da coorte atual, CF a itens da captura
  corrente da Coleta; histórico antigo não reaparece na receita/apresentação.
- Receita/capacidade/referência/colunas92 usam comparação de conjuntos para
  replay; receipt idêntico confere também paths/freight lineage. Nova observação
  com valores iguais éNOOP de negócio e avança ponteiro de proveniência.
- V073 display prepara collections/arrays/JSON tipado uma vez por carga; SQL08
  105colunas eSQL09 106 mais técnicas; ambosleemMAT05ativo. Provas conferem todos
  aliases/ordem/valores não-nulos no positivo, decimal28,8, traduções, timezone,
  receita120uma vez por caminho compartilhado e capacidade22000nos três veículos.
  SQL09 adiciona Tipo de contrato key; ambos têm Local de Descarregamento.
- V074 compara7campos TIME porsegundo+nano e projetaUTC; raws/offsets preservados.
  V077 aplica igualdade deinstante à competência na captura anterior à preparação.
  Teste aceita7offsetsequivalentes e recusa1ns emmobile_read_at na mesma coorte.
- V075 corrigeJSON_VALUE do wrapper de sequence_code. AliasINTEGER100001positivo;
  textual é recusado pelo contrato sintético atual, não por coerção silenciosa.
- V078 acrescenta FLEET_ROLE_CONFLICT para veículo emdoispapéis; binding inativo
  de reboque opcional representa retirada quando fonte também não declara placa.
  Provas de zero/nulo, swap/removal, placa excluída, referência faltante/fullgate
  estão na campanha69503, ainda não reconciliada.

Docs novos/atuais: MAT05-REGRAS.md, manifesto-colunas-consumo.json,
manifesto-referencias.json, ADR0050 ANA19–21. Status/matriz precisam sincronização
conforme última campanha. 0100corrige contagem MAN102(81+13+8) sobrebásico75.

Pendências necessárias:

1. Reconciliar69503; corrigir falhas e completar contraprovasG/C/E/F/D.
   Manter regra de salvar histórico e não estreitar gate para passar fixture.
2. Implementar oito SQL restantes e leitor JDBC limitado; SQL11éInventário34,
   SQL12Sinistros34 (mappings no inventário, base tipada151 já existente).
   Integrar11verticais/5fatos/19saídas noJAR e ausênciaK/hidratação/degradação.
3. L–N: sessões concorrentes, escalas3+repetição/planos, verify e233ITanteriores,
   processosJAR, matriz673/diff contra before/revisão/estados funcionais/sucessão.

Lacunas anteriores continuam: terminalidade Raster porfolha/extracted_atno-op,
matriz completa51campos, efeitos SQL XACT_ABORT/savepoint emfalhas tardias,
full/empty/hidratação/inc/concorrência/medição integrados. Nenhummarcofinal alegado.
