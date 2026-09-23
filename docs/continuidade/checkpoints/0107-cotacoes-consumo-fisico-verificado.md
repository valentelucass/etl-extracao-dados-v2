# 0107 — Cotações com consumo físico verificado

EM_EXECUCAO A–N; continuar integralmente sem perguntas/continue. Predecessor
0106 SHA2567e4006739314855689d4db51dcf82cbadb81876df8a6b7f90f5c75cdfc1a6501.
Request/before2704/limites permanecem. Construção32/45 e aceites67/115 intactos.
V001–087 instaladas, baseline acompanha; próxima candidataV088, conferir alvo.
Nenhum processo próprio ativo conhecido neste checkpoint.

physical-quotes04/session53869 reconciliada: exit0,5IT,0falhas/erros/skips,
15,78s testes,1min43build. Contagens antes/depois preservadas. SQL05 tem54
colunas positivas com metadata SQL real; dezessete contratos físicos ao todo.
Export metadata-seventeen-contracts01 realizado nesta unidade; consultar JSON
para contagem exata. Não há ainda leitor JDBC genérico nem cenário JAR composto.

Falhas preservadas:

- physical-quotes01/session66022: compilação do teste, retorno void ausente.
- physical-quotes02/session87251: chamada bind inexistente, correta bindBatch.
- physical-quotes03/session52150:3IT/1falha/2erros, selo do recovery exigia
  referência tarifária com escopo próprio. Corrigido na revisão04, sem bypass.

V087 acrescenta ctl.analytic_quote_runtime_reference e procedure de binding
imutável por execução/run/revisão/release/fingerprint. ctl.fn_runtime_tariff_valid
preserva o ramo antigo e as verificações de privilégio/consumo; reconhece ramo
adicional somente para esse vínculo LOCAL_SHADOW/LOCAL_V2/LOCAL_V2/cotacoes,
famíliaQUOTE_TARIFF, seleção selada/ratificada/vigente e escopo sintético exatos.
Sem grants/usuários/permits fabricados. Qualify/install-quote-runtime-binding01
passaram; Java agora passa o mesmo release ao recovery e à promoção. Antes da
extração, staging.begin e promotion.bindReference fazem o vínculo explícito.

Cinco IT de AnalyticLaboratoryQuotesIT comprovaram:

- 3linhas físicas entre páginas2 →1snapshot/raiz,54colunas positivas, tarifa
  SP→RJ1,21, total120, precisão28,8/TIME7 e raw9nanos, replay semnovo snapshot.
- Correção mais nova: ABSENT preserva cliente, NULL limpa observação, zero é
  mantido; captura antiga viraSTALE_NO_OP; empate divergente53715 é recusado
  sem apagar os dois snapshots anteriores.
- RotaAC→RJ sem cobertura53716, isolamento de run/contexto corrente53714 e
  preservação do ramo já preparado após rollback da captura recusada.
- Falta de filial bloqueia SQL05 comdisposição; binding libera. Convertida
  precede motivo de perda; documentosNULL dão Reprovada/Pendente, labels de
  emissão e Refino corretos; usuário trim/NFC conserva acentos.
- Importtarifa25/retryexato; divergenteref53705 não muda conteúdo; PR→RJ0,88
  direcionais; vigência expirada53716 não publica nem regride valor anterior.

Arquivos/políticas centrais estão em0106 e ADR0050/ANA26; catálogo de36campos
atualizado TESTED_PHYSICAL_LOCAL. Regressão antiga completa continua obrigatória,
especialmente fn_runtime_tariff_valid e runtime COT apósV087.

Próximas ações concretas:

1. Coletas SQL03/04+K. DataExportColetasContractCatalog declara31campos reais
   do perfilB62; faltam no6908 alguns atributos legadosGraphQL. Não inventar
   paths por cópia de nomes; usar suplemento sintético tipado/binding lateral
   ligado à observação da captura. RelationalSyntheticSource já oferece perfil
   MAN completo; acrescentar perfilCOL opt-in conservando assinaturas antigas.
   LocalRelationalRuntime capturaCOL pelo pipeline existente e serve de entrada.
2. Ligar snapshots de ausência completos/independentes ao kernel existente.
   Kernel exige4proofs deordinais1..4 comoccurrence/run fingerprints distintos,
   masmesmoscope/policy/snapshot/binding; montar evidência a partir de recibos
   físicos, semconstantesfalsas. Primeira ausência candidata/segunda confirma,
   reappearance limpa; fullmaterialização não fazsweep; operacional intacto.
3. Leitor JDBC tipado/limitado, cenário JAR11entradas/5fatos/19consultas, restante
   C–I/L–N: modos/hidratação/delta, concorrência real, escalas,verify233IT/JAR,
   diffbefore/sucessãoexata/relatório. Nenhum resultado parcial encerra o pedido.

Investigação Coletas já lida (sem mudanças V1):

- RelationalLaboratoryFixtures (nome exato, não RelationalFixtures) geraCOL
  id200000+índice,alias100000+índice,status pending,request_date e relógio
  status_updated_at9nanos,synthetic_item_key; bindingBatch fornece MC/CF explícitos.
- DataExportColetasContractCatalog31: id, pck_crn_psn_nickname, sequence_code,
  created_at, request_date/service_date/finish_date, pck_cor_nickname,
  invoices_volumes/weight/taxed_weight/value, status, pck_pds_cty_name/state,
  postal_code/neighborhood, pck_prn_name/driver, updated_at, ocorrências,
  pck_uer_name/pck_ctr_name, cancellation_reason, attempt/manifesto/placa/tipo.
- GraphQL ColetaNodeDTO+ColetaMapper legados: requestHour, vehicleTypeId,
  customer.name/cnpj, pickAddress.line1/line2/number/city/state/neighborhood/CEP,
  corporation.id/person.nickname, user.name, cancellationUserId, destroyReason/
  destroyUserId. Caminhos GraphQL camelCase são distintos dos aliases DataExport.
  Não são garantia de wire/currenttenant. Arquivo DTO id éString; ID6908 continua
  sourcekey integer do contrato próprio, sem equiparar implicitamente GraphQL.
- ColetaStatus existente já implementa COL11: finished/done/canceled/cancelled
  terminais, draft não terminal; ação finished/done primeiro, depois motivo,
  depois cancelada, senãoPendente. Labels/raw/code preservados separadamente.
- SweepScope hashes canônicos são methods package-private; integração no mesmo
  pacote ou factory estreita, semreproduzir indiscriminadamente strings de hash.
  Kernel épreviewsemcapability; novoapplySQLsomente no laboratório autorizado.
