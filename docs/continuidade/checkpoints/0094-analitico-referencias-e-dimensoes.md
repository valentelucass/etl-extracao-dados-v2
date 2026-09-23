# 0094 — Referências seladas e vínculos dimensionais físicos

EM_EXECUCAO. Prosseguir integralmente no request A–N adotado, sem perguntas.
Predecessor0093; inventário2704, snapshots e autorização em
target/macrobloco-analitico-20260912-01/. Não alterar V001–V056 instaladas.

V054/SQL-13 foi consumida: physical-raster-transit-01 passou10IT (4Raster+6Transit),
zero falhas/erros/skips; limites0/43200, fallback, rota, conflito e metadata51.
HTTP directed-raster-http-01 passou20unit, mas depois recebeu BodyHandler limitado;
reexecutar os testes HTTP sobre essa alteração na próxima rodada dirigida.

V055 instalada: referências ANALYTIC_RULES estendem registry/import_receipt atuais,
75 entradas sintéticas (54labels,13registros dimensionais,8exclusões); revisão,
scope/vigência/políticas explícitas. Physical-references-01 passou3IT físicos,
incluindo replay, homônimos, escopo e imutabilidade. Contagens preservadas.

V056 instalada, baseline atualizado: source_group exp/rel declarado; attachment de
execuções Usuários/Cotações; bindings tipados por papel/chave/versão/vigência;
view de fontes correntes; lookup SQL com disposições; SQL14–19 internas.
SQL14–18 derivam somente de registros com binding e captura vigente, em fatias
diárias; rekey afeta só sua vigência. SQL19 lê core.v_usuario_dimension_current_v1.
Physical-dimensions-02 passou5IT, zero falhas/erros/skips, contagens preservadas:
12vínculos sobre capturas reais Fretes/CAP; cinco dimensões, homônimos, capacidade
22000KG, fallback de classificação, metadata, replay, rekey, lacuna/recusas.
Negative tests conferem códigos SQL específicos. Rodada01 falhou por GUID no TVP;
corrigido para transporte VARCHAR convertido em UNIQUEIDENTIFIER no SQL.
Tentativas DDL com conflito de collation/palavra reservada foram preservadas.

Sem numerador novo: construção32/45. Ainda não há integração final nem aceite.
Usuários tem projeção SQL19, mas seu pipeline ainda não atravessou este cenário.
Teste de troca de atributos/placa/filial e política genérica ainda necessário.

Próximas ações:
1. Reutilizar LocalUsuariosRuntime, parser/gate GraphQL e promotion+DQuality reais
   em sessão rollback-only; origem sintética limitada, SQL19 e vínculos COL.
2. Construir MAT01/02/05 e demais contratos: loader001 legado foi lido integralmente;
   grão PE/CB já decidido. A maioria dos atributos SQL02 vem do GraphQL legado,
   não do6389 conhecido; modelar suplemento sintético tipado e explicitamente
   vinculado à captura, com proveniência/recusa externa e consumidor real.
3. Compor J–N (11entradas,5fatos,19queries, ausênciaK, escala/concorrência/JAR/verify,
   diff e sucessão). Nenhuma dessas pendências é motivo para encerrar a execução.

Arquivos novos: JdbcAnalyticReferences/Dimensions, AnalyticDimensionBinding,
references.synthetic.json, V055/V056 e IT correspondentes. Leitura dirigida para
Usuários: RuntimeUsersSessionTest, RuntimeUsersRequest, RuntimeDispatcher,
GraphQlContractGate/ResponseParser (package-private), validação027 e V006/V018.
Não enfraquecer gates nem fabricar permit: usar parser, guard e DQ com política
sintética registrada por hash real. CheckGuidance permanece para fechar sucessão.
