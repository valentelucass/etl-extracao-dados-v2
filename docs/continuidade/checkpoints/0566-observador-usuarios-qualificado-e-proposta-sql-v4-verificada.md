# Checkpoint 0566 — Observador Usuários qualificado; proposta SQL v4 verificada

24/10/2026,22:32BRT. Sucessor0565. Pedido de concluir as quatro etapas ativo;
etapas3/4 ainda não aceitas. Continua pendente a resposta humana à exceçãoDDL
específica dos dois objetos Fretes. Nenhuma mensagem sobre organização/delegação
foi tratada como resposta a essa decisão.

## Entrega Usuários revisada

Chat ETL V2 — Usuários e retomada da coleta concluiu sua entrega externa em
`quatro-etapas-20261024-01/delegacao-usuarios-20261024-01`. REPORT.md final,
qualification-02/result.json e receipt foram lidos.27cenários adversos PASS e
qualificação adicional com leitor Windows:128páginas falsas,513snapshots selados.
Compile17/strict/Werror,batch0/147683ms/sem timeout,pico315953152bytes. Primeira
rodada instrumental encerrou exclusivamente seu filho no prazo120s;exit1 e
artefatos preservados,não somados comoPASS. Segunda rodada nova teve300s/8min
finitos;nenhuma reabertura do diretório consumido.

O sucessor usa nomes únicos/CREATE_NEW/selo apósforce+close,sem substituição;
separa reserva,intenção de envio,entrada confirmada no transporte,página selada
e fechamento. Falha na reserva950 deixa949envios falsos;envio incerto após queda
não é marcado como não enviado. Cadeia quebrada,selo incompleto,teto,429,JSON
inválido,ID/cursor repetidos e prazo não produzem conclusão. Mensagens/paths/
negócio/segredos não entram nos recibos. O selo é integridade técnica,sem provar
autenticidade nem durabilidade contra desligamento físico.

Nenhum launcherlive/env/credencial foi entregue ou executado. ZeroHTTP/SQL.
JARA49 e21inputs congelados permanecem idênticos. Cursor e conjunto de IDs da
ocorrência real antiga foram perdidos com a RAM:retomada comprovada sem replay
não existe.18.980observados/18.978semrecusa/2nomesrecusados/hasNext=true continuam
o resultado real. Travessia completa e snapshot aceito seguem abertos.

## Precisão temporal agora observada no SQL real

`temporal-readback-probe` executou22SELECT em uma conexão C78,mesmo socket/nativo
antesLOGIN7/TLS/versão16.0.1000.6/alvo e encerramento confirmados.19fixtures
sintéticas usaram exatamentesetTimestamp/CalendarUTC/driver12.8.2 e CAST para
datetime2(3)/(7):PASS,com NULL,limiares e carrydia/mês/ano/preEpoch.Compat160.
Truncamento divergiu13casos;arredondamento direto1ms divergiu4. Ficou observada
a projeção100ns→1ms no caminho fixado;nenhum negócio/DDL/DML/HTTP ocorreu.

`FretesBankTrialV2.java` externo corrige a comparação esperada para essas duas
etapas,sem alterar a gravação do adaptador ou o bruto. Stage3FretesPipeline-v5
passou o pedido principal ligado ao destino C78 real;19casos do oráculo SQL
foram conferidos offline no método de readback. Strictcompile+selfcheck PASS em
stage3-v5-offline-v2. Falha anterior de nome auxiliar -v2/Werror preservada;
recuperação usou classe/arquivoFretesBankTrialV2 corretos,sem relaxarWerror.
Isso não admite captura física:catálogo pós-adoção real continua inexistente,
schema continua sem autorização. Controllerlivev5 não foi preparado/executado.

O trial atual ainda compara os9JSON por JsonNode.equals,insuficiente para
provar preservação de texto/precisão de todas as representações. A revisão
entregou patch de comparação textual exata;permanece candidato a integrar e
qualificar antes do readback real. O parser produtivo DataExport já conserva
BigDecimal e escala;essa constatação não substitui a correção do oráculoSQL.

## Revisão independente e verificação real dos predicados v4

**Caminho real da entrega Banco é irmão da campanha:**
`Armazenamento-Projetos/etl-extracao-dados-v2/delegacao-revisao-banco-20261024-01`.
Não está dentro dequatro-etapas-20261024-01;corrige a previsão de0565,sem mover
ou sobrescrever a entrega. Chat de revisão não fezSQL/build/HTTP.

A revisão encontrou7contraexemplos v3 que passam nas duas camadas:parseState
superior ausente/NULL,instantes de evidência ausentes ou valores tipados
incoerentes. Não se afirma que o mapper normal produza esses erros. V4 positiva
valida a posição escolhida/instantes projetados,conserva ramo legado válido e
usa CASE de chaves literais/BIN2 para evitar $..instantUtc quando seleçãoNULL.
O fallback de serviço é somente avaliação segura;origemNULL não é admitida.
Forwardv4 final SHA256:
`D9D6781180EE0F1C2F9793E3C55C8D0A48F219542043A42BF574780D58588BC8`.
Continua apenas proposta dos mesmos2objetos,defaultTHROW,um batch e rollback;
não invocar Flyway nem inferir histórico/novos objetos/contas/permissões.

Root qualificou e executou **somente SELECT dos predicados/projeções exatos**:
`readonly-fretes-v4/live/results.json`,40SELECT/1conexãoC78,37casos sintéticosPASS,
7passagens indevidas v3 reproduzidas e8casos de seleçãoNULL avaliados sem erro
de caminhoJSON. Os casosNULL não avaliaram o caminho inválido histórico v3;
não fingir uma passagem física dele. Versão/TLS/nativo/alvo/fechamento confirmados.
ZeroDML/DDL/HTTP. Não executouALTER,proc completo,staging/readback/rollback37.
Os12PASSanteriores v3 permanecem corretos no seu conjunto,mas insuficientes.
README da proposta externa aponta para v4 sem promover versões1–3.

Antes de qualquer DDL futuro:decisão humana real,preflightnativo/TLS/alvo,
comparação de definições/constraints,assinaturas/permissões/ownership eDDLtriggers,
checagem agregada de compatibilidade das linhas existentes com novoCHECK,
locks transacionais da pegada e recuperação. WITH NOCHECK/alterar grants/criar
histórico ou relaxar o contrato para passar ficam fora do escopo.

## Demais entidades: auditoria e P1 em andamento

Entrega estática Contratos emdelegacao-contratos-20261024-01/REPORT.md distingue
identidades locais adotadas de garantia global. Manifestos/Cotações/Localização
já têm decisões conservadoras escopadas;não exigir informação global inventada
como condição de toda captura restrita. Contas raiz/Faturas título/componentes
Inventário/Sinistros/Raster continuam sem os bindings/aceites específicos.
V1 foi lido:hashes/renomeios/índices explicam o legado,sem resolver essas chaves.

Obstáculos locais confirmados:trêsB55 sem seleção observada;Local usa mapper17,
pipeline sem léxico original eSQL com envelope17;expansões exigem proveniência
sintética/occurrence que não vem daAPI. O mapper observado reinicia ordinal por
página;não ligar direto ao staging com unicidade por execução/occurrence.
Cotações pediu250/500,não50/100;ambos devolveram100/100/87/0. Provasetapa4 sem
order_by não qualificam a ordenação doenum,per100continua teto runtime.

O supervisor autorizou a fase candidataP1 externa:catálogos/recursos observados
6399/6906/8656 com hashes/tipos sustentados,sem editar produto/selectors/SQL/docs.
NULL-only sem tipo inventado,NUMBER não distingueINTEGER/DECIMAL;elementos das
listas não caracterizados permanecem bloqueados. Sem conceder acesso ou aceite.
Código/testes em candidatos-p1;**qualificação ainda pendente neste checkpoint**.
Fila liberada ao chat Contratos paraJDK/testesoffline≤1GiB pelo executor original;
Root ficará em leitura/documentação,sem segundo batch/SQL enquanto ele qualifica.
Não iniciarP2/P3 antes de revisarP1. Usuários/Banco encerrados nos seus escopos.

## Consolidação e próximo passo

8conexõesSQLencerradas:5somenteleitura,3tentativasstaging revertidas;zeroDDL e
zero commit retido de negócio.1181HTTPfonte/1182reservaslocais permanecem;nenhuma
chamada nova àESL desde a falha local. Consultas públicas às fontesMicrosoft
sobre precisão não são chamadas àESL. ProdutoJARA49/334casos por composição/7IT
eAST44514/116337 permanecem;testes externos não são nova suíte/cobertura do produto.

1. Receber/revisar/qualificar P1 e integrar somente os bytes aceitos;preservar limites.
2. Integrar comparação textual do trial e qualificar sem repetir testes sem causa.
3. Resposta humanaDDL continua necessária para efeito dos2objetos;quando concedida,
   provar alteração/testes em rollback,adoção só no escopo concedido,catálogo novo
   e37fonte→staging→readback→rollback→ausência. Sem retenção/promoção/deploy.
4. Prosseguir P2/P3/Raster e identidades nos seus aceites próprios;etapa4nãoencerrada.

Root único docs/CI/SQL;C78rollback-only;SQL16.0.1000.6/instalação/contas/credenciais/
permissões/identidades/ACL/serviços/listeners/TLSglobal/V1/WMS/produção preservados.
Gates43RAW/53exclusões eMainHTTPUNKNOWN/MISSING_NOT_RECONSTRUCTED permanecem.
