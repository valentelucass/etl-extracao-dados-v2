# Checkpoint 0577 — Usuários/Raster qualificados; causas medidas; pacote em teste

25/10/2026, 2026-10-25T07:08:31.566746+00:00. Anterior:
[0576-contexto-do-banco-isolado-e-usuarios-v3-em-qualificacao.md](0576-contexto-do-banco-isolado-e-usuarios-v3-em-qualificacao.md), SHA256
07BF467F47C065BBBEF20715DC63C56F3CBCA5FF9ECEBC8E0FF905FD095B3705.
Objetivo permanece concluir as quatro etapas da extração V2. Etapas3/4 abertas;
não encerrar por delegação, preparação ou teste offline.

## Resultados efetivos e evidência

- Usuários V3: 37 casos efetivamente aprovados, compilação estrita JDK17,
  factory/parser reais com transporte fake, percurso de5.000 páginas/100.000nós/
  20.001metadados selados, leitura integral e corrupção perto do final recusada.
  Recibo original1GiB,957.466ms,pico411.136.000B,exit0/sem timeout. Sessão46905
  fechada. RootUsersV3QualificationReview.json, SHA
  066B788F3800BAEBCD1D72626F3B3CEB874EC1D7B887C11CD5542C2C3E06081C.
  Fonte/SQL0; nenhuma nova travessia real liberada. A ocorrência real anterior
  continua40mil/39.997válidos/3quarentenas/hasNexttrue, sem cursor retido.
- Catálogo V5:67 grupos offline aprovados, recibo1GiB/10.777ms/pico404.385.792B/
  exit0/sem timeout. Novo live10250004 passou contexto e conferiu os13corpos
  reais contra referências congeladas. XACT_STATE observado foi classificado
  COMMITTABLE, com autocommitTRUE/depth0 e demais guardas satisfeitas.
  Query4 parou na primeira dependência não resolvida de
  ctl.trg_execution_source_protocol_immutable:targetNULL/typeUNKNOWN,sem fronteira
  externa. Nome original REFERENCED_ENTITY não retido. Não inventar seu valor.
  4SELECT tentadas/3concluídas pelo verificador,55.633B recebidos; conexão fechada
  conhecida, peer antesLOGIN7, native SQL inalterado. Recibo1GiB/13.916ms/exit1,
  sem timeout. RootCatalogV5ClosureReview.json SHA
  8A52DC413C57C0049C2B6704EABCA9C4B2681C1CB65553165F267769F12D0D70.
  Ainda não existe catálogo positivo35 ou admissão física.
- Raster diagnóstico V2:45JUnit reais aprovados (20diagnóstico+25entrada/protocolo),
  strict17,recibo1GiB/13.987ms/pico358.862.848B/exit0/sem timeout. Manifesto
  400FCA42DA50CD88DCBE61848C251A8935452C02CEBBF8E5EF6F62D5F8B0261B.
- Raster novo live10250002:1POST/dia22Oct,reciboHTTPsucesso108viagens/355paradas,
  encerramento selado/readback,recibo1GiB/19.866ms/pico284.766.208B/exit0/sem timeout.
  A rejeição real de Latitude227/355 e Longitude229/355 coincide exatamente com
  escala>8 e dígitos nãozero além da8ª; esses valores não cabem exatamente em
  DECIMAL28,8. Preditor versus parser real:0divergências. Evidência é rendering
  exato do DecimalNode/disposição observada, não tokenJSON original nem trace da
  exceção capturada. Nenhum arredondamento ou mudança de contrato foi feito.
  Há20pares deOrdemrepetidos, todos com algum dos20atributos mapeados diferente;
  14mesmoTipo/6Tipo diferente. Pares não são as16linhas excedentes da medição
  anterior. Não deduplicar ou promover Ordem a PK. Identidade/completude/
  admissão continuam falsas; nenhum payload/ID/coordenada/cursor foi retido.
  RootRasterV2LiveClosureReview.json SHA
  FBDD7C21956DF0864609E316ADB09A54E13171A1DA30A73115AF3BA4FB9947F8.

## Novos defeitos encontrados e decisões

Revisão completa dos100SQL/267pins pela frente Contratos não encontrou mutação,
UDF ou tabela de negócio. Encontrou projeção versus expected incompatíveis em
Local20–23: o plano esperava scope inexistente e omitia três colunas. O original
case-manifest já fornece os resultados independentes de caller/dispatch/status.
Core V3 separado corrigiu somente esses4expected; outros93/100SQL intactos.
São30métodos/33invocações preparados,0executados; manifesto
CB5FDF97B8E0729E8FD97D1EA2FC30C53B69EDE8FAD835B599992B6AE232C701.
Composição física com12casos próprios preparada, manifesto
4F441F400ACC68B45D96771F28C439887D45EBA2BAF309260F6AD979EDBF5874;
tabelas Root vazias, sem unidade ativa ou qualificação herdada. A guarda de
autocommit/depth/estado0ou1 é sucessora explícita, preservando os18/29anteriores.

JDK17 local: contagem de adapter não garante teto de transmissão se houver
retry interno deGET. Leitura do src.zip SHA
2E28A2C1C143E2DAD534CEF71C977AAB49AFDF1ADC25F921E161356D396354E9
mostra que redirects.retrylimit=1 limita todos os ciclos MultiExchange antes do
envio, junto de disableRetryConnect=true e enableAllMethodRetry=false. UsuáriosV3
e RasterV2 JÁ contêm as três flags; nenhuma nova versão ou repetição de37 foi
feita por esse motivo. Banco e lançador P3 precisam integrar o ajuste em sucessor.
Sonda causal loopback preparada em chatcwd/work/loopback-get-retry-probe-v1,
manifesto47990F36EEBE891A358B93110F6F69D4FC3D9E7EFCAA193887D7A6B63E16E512,
0executada. Nenhuma chamadaESL por essa sonda; futuros guarded/default separados.
O histórico3194 conta invocações/respostas confirmadas pelo adapter, não medição
de cada transmissãoTCP. Retransmissões internas históricas não foram medidas.

Banco físico E05B:revisão31arquivos/177pins encontrou dois defeitos: flags acima
ausentes e checagens de prazo antes depreflight/hash ou depois deprovider/open.
Sucessor deve checar imediatamente antes de cada efeito, sem renovar tempo;
getterdelta ainda precisa integrar. Nenhum novo defeito de commit/rollback/
readback/temporal/ausência8/close encontrado.22modelos preparados/0executados.

V6 catálogo está em preparo. O corpo real exato do primeiro trigger recusado
consulta deleted. A hipótese de pseudotabela intrínseca não é valor observado
da linha descartada. V6 publicará diagnósticos técnicos antes de recusar e pode
classificar inserted/deleted somente no contexto estrito dos6triggers conhecidos,
corpo exato,classes1,referencing_minor_id0 e campos resolução/fronteiraNULL,
com metadata/eventos frescos na finalização. Outros unresolved continuamUNKNOWN;
não fabricar ID/target resolvido. Os11SQL/35SELECT ficam intactos. Colunas ausentes
da query, como referenced_minor_id, não podem ser declaradas observadas.
Referências primárias: Microsoft Learn sobre
[inserted/deleted](https://learn.microsoft.com/en-us/sql/relational-databases/triggers/use-the-inserted-and-deleted-tables?view=sql-server-ver17)
e [sys.sql_expression_dependencies](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-sql-expression-dependencies-transact-sql?view=sql-server-ver17).

## Executor atual, autorização e preservação

Único Job: Root P3q05/sessão73472,original2GiB/15min,coorte56/42classes,
p3-root-cohort-v6-r04-isolated.json SHA
EEEEA8EE6EFB39BC18CB45F75C946968D05DFCD4414DEAB631E95C1CFCA65CC9.
44fontesv5 exatas+11Java+recursoq04 original355.245B. Qualificador sucessor
QualifyP3Composition-Isolated.ps1 SHA
8EA45086A9F55A33F85941D3E173F6E0B694E5F5BEDBDCAA826492A20B59E465,
ambiente mínimo antes dePython-I-S,executor original intacto. Resultado pendente;
não transferir419JUnit+7IT deq04 às novas22invocações. Fase positiva Windows/Unit
fica separada, consumindo resultados/recibos reais já fechados, semJSONqualificado
artificial e sem conceder fonte por teste offline.

Não iniciar outro Java/Maven/SQL/fonte/índice até fechamento,recibo e liberaçãoRoot.
Delegados sóestáticos. Produto continuaE05/150JUnit+7IT;P3q04/419+7 aprovado no
snapshot,sem adoção/deploy. AST ainda precisa atualização após integração final.

Total11conexõesSQL fechadas,0DDL/0commit retido. Fonte3194/reservas3195,
DataExport95/GraphQL3096/Raster3. PreservarV1/WMS/produção,SQL16.0.1000.6,
instalação,contas,credenciais,permissões,identidades,serviços,listeners,certificado
e trust. A pergunta específicaDDLdos2objetosFretes ainda aguarda resposta humana;
não cobreLocal/P3/Raster. Não repetir pergunta nem inferir aprovação da continuidade.

## Retomada imediata — até três ações

1. Fecharq05 por resultado/recibo efetivos; qualificar extra e fase positiva
   Windows/Unit em unidades próprias. Depois conferir integração/adotar sóbytes
   aprovados; não declarar produção ou captura SQL por JUnit.
2. Qualificar coregrid33/physical12,sondaJDK e catálogoV6; executar unidades reais
   finitas separadas de banco somente com pré-condições verificadas.
3. Concluir provas físicas Manifestos/Cotações/P3 e preparar tratamento específico
   de coordenadas Raster preservando precisão; depois nova travessiaUsuáriosV3
   com liberação finita própria. Não reutilizar cursores ou limites encerrados.

Bloqueio externo parcial:DDLFretes sem resposta humana. Persistem alternativas
independentes elegíveis; não finalizar como se as quatro etapas estivessem feitas.
