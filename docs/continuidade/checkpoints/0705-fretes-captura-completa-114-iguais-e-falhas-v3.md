# Checkpoint 0705 — Fretes: captura completa, 114 valores iguais e falhas V3

## Identificação e objetivo

- Data: 2026-11-05, fechamento após 06:03 UTC. Anterior: `0704-fretes-ampliacao-500-e-regressao.md`, SHA-256 `E4EF42BF5C9DC6FEC2DDB7CC6B798764D38B9717D3A32C6700DED838884A2095`.
- Objetivo efetivo: corrigir a extração e provar conteúdo real contra Excel em três períodos, preservando regras ESL. Último pedido: a correção deve ser exercitada sob diferentes tipos de sobrecarga e falha.
- Estado: TESTADO_NA_CAMADA e ACEITO_NO_ESCOPO para captura/igualdade do dia 19/10; três semanas completas e entidade continuam pendentes. V1 pausado; Coletas 3/3.
- Critérios: todos os valores de cada coluna, tuplas completas, multiplicidades, filtro temporal e totais; campos adicionais da API permitidos. Data Export; GraphQL somente usuários. Dashboard alvo já conhecido, consumo real ainda pendente.

## Autorização e limites

- Usuário pediu continuidade até corrigir, prova definitiva e testes de sobrecarga; autorizou apoios/revisão no ETL V2. Root é único executor pesado/API/browser.
- Preservados 47HTTP incluindo INFO/repetições, gap mínimo3s após resposta, request30s, Source210s, Original300s/1GiB. Perfil V3 reconhece422timeout e500exato46B/hashD7E6; até2retries/página6/unidade/esperas10-20s. Retry-After presente,429,UNKNOWN e corpo incompleto param. Sem ampliação para forçar sucesso.
- Sem SQL produtivo, deploy, commit, PR, mudança de serviços/processos externos ou credenciais. Apoios só metadados/código estático; execuções no Original inalterado91D96.
- Autenticação ESL está pendente no navegador interno; pergunta já apresentada. Não duplicar. Source0/local continuou enquanto isso. Nenhuma Source nova foi disparada após a captura concluída.

## Execução e evidência

Referências `work/` e `outputs/` abaixo são relativas ao supervisor privado `C:\Users\suporte\Documents\Codex\2026-10-27\etl-v2-supervisor-finalizacao`.

| Unidade | Camada e limite | Resultado observado | Evidência |
| --- | --- | --- | --- |
| Source0704-02 | ESL real/Original300s1GiB, per60 | Original34752/0,136085ms456052736B;Java17536/0/EOF;17HTTP200/946ocorrências850IDs/terminal16vazio | `outputs/fretes-day19-real0704-02-actual01/capture.complete/manifest.json` E58C042D;closure03 ACA59EBD/6948B |
| Retenção/EOF/assinatura | Original14072/0,Node20428/0 | Todos17corpos retidos;assinaturaEd25519existente verificada;EOF por subscriber qualificado | assinaturaRoot779814/2764B;inspeçãoF46C4946 |
| Conservação corporal | Original10372/0,13630ms108986368B,Python6884/0,Node36328/0/EOF | RAW→JSONL→locators por valor,946ocorrências/850IDs/114INFO/terminal/conservação de multiplicidade | `work/fretes-v3-body0705-02-run02/diagnosis.json` 30DE65BE/28862B;worker1AB2304B/3024B |
| Preparação do par | Original4392/0,12298ms105193472B,Python28608/0/EOF | INFO114igual ao modelo;XML114perfis/datas/946linhas e conservação verificados | `work/fretes-pair-v3-0704-v2-run01/pair-candidates/final-result.json` eparent0 |
| Comparação completa | Original42376/0,34660ms270450688B,Python34704/0/EOF | 114/114conteúdos iguais,850tuplas completas iguais/946ocorrências,50/50totais,datas iguais | `work/fretes-daily114-v3-0705-run02/audit/final-result.json` EB993E15/61084B;aceiteFABABE16/3890B |
| Transporte V3 adicional | Original46716/0,35238ms309321728B,javac72888/0,Java56664/0/EOF | 5casos reais de socket local passaram:headerslentos/quedaantesheaders/500prefixocortado/500prefixotravado/EOFinicial. TodosSTOP/0retry/semcompletefalso/2cliente=2servidor | `outputs/root-fretes-v3-transport-acceptance0705-01.json` BA986279/2480B;fonte7BDE6FAC/config0F51FF99 |

As 31 suítes Java, 34 invocações PS e runtime25+Node+3vínculos, fingerprint38classes/16famílias e Native atual do checkpoint anterior permanecem válidos, sem reconstrução do produto08831/host5AD3. Os cinco casos adicionais usam esse produto inalterado, servidor/gate lógico sintéticos e transporte real local; não qualificam TLS ou Native real. 422→500→200, persistência,429 eRetry-After já haviam passado em loopback V3. Não há prova de recuperação500naESL nesta Source: todas17respostas foram200.

## Correções locais e revisão

- O auditor corporal inicial exigia `probe` de todos os recibos Java; cinco recibos legados têm esquemas próprios. Falha Original53540/1 preservada. Correção isolada0111B755 mantém validação estrita para cinco formatos legados e26novos;três testes específicos e auditoria real passaram no mesmoOriginal10372. ProdutoSource não mudou.
- Revisão independente encontrou falta de vínculo entre auditor/Original/parent. V2 index7772F3FF/helperDB5D95ED/preparerEE372564/reconciler7BCAC3A3 acrescenta worker/config/module/wrapper/approval e revalida antesCAS/Recover. Root e revisão da thread01a1a869 revision38 aceitaram o vínculo da cadeia real. Worker histórico não contém report/config pin próprio: fechamento declara isso explicitamente, sem inventar campos. Assinatura reutilizada foi realmente verificada.
- Comparação inicial Original57424/1/Python69076/1 parou antes do conteúdo: recibo de captura tinha apenas200:17, enquanto adapter exige também422:0/500:0. Falha preservada,estado restaurado. Novo recibo de comparaçãoA9ACD2BE usa contadores explícitos da candidata real838BD5; recibo canônicoC91DBCB6 fica imutável. Novo Original42376/0 passou; não altera fonte/dados/regras/encoding129.
- Modelos114 reutilizados somente após INFOidêntico e novos perfisXML/datas conferidos. Encoding129 semEPS/tolerância; nenhuma escolha de regra guiada por igualdade. Excel535397B/6E554E00,04nov21:58,antecedeSource. Aceite somente dia19out; não é semana nem par simultâneo.

## Estado canônico final

- Atual: `3704BDED6B9927BCDEEEBED7283A4FF3E6680F924891A0E471ABDC6AE665A6A4`,723580B,ACL B4E214EB…,Root0658,slot vazio,19complete+12partial,zeroHTTP conhecido pendente. **Não restaurar8614 ou anteriores.**
- Reconciliação acrescentou somente frontier/counter17 preservando lexicalmente todos os valores anteriores. PrepareAndCommit eRecover0; mesmos hashes,nenhum17duplicado. Não foi executada injeção de crash.
- Controle `work/fretes-complete0704-transaction-v1/current-control.json` 17692DF0/32664B;typed2D5067AB/1877B;frontier8CE26CEC/4522B. Fronteira direta nova,sem baselineReconciliation antigo apontando apósnovoestado;cadeiaF9B90050/48reservas permanece como histórico.
- Intent81F967FD/3230B,commit6B415338/968B,aceiteAC966C01/1983B. SignedPolicyProof {closureACA59,signature779814},semretryComplete/semv3RetryBodyProof pois não houve retry.
- Todos os jobs próprios encerraram,nenhuma execução pesada ativa. V1 continua pausado. Nenhuma escrita produtiva ou consumo real do dashboard declarado.

## Retomada imediata — até três ações

1. Recuperar sessão autenticada ESL no navegador interno,Fretes6389; pergunta de login já pendente. Não usarColetas6908 por engano nem extrair cookies/tokens por fora da UI. Seabaerro,abriroutraefecharerro conformeusuário.
2. Com sessão e orçamento vigentes, preparar captura delimitada e Excel recente do mesmo período para fechar as três semanas completas; auditar e reconciliar cada Source antes da seguinte. Dia19out provado não conta como semana. Não reutilizar grants/READY/janelas consumidas nem ampliar limites. Capture API pode conter colunas extras.
3. Comparar imediatamente os valores de todas114colunas/tuplas/multiplicidades/totais e datas; corrigir divergências com evidência. Só então avançar aprovação de entidade e contrato de consumo do dashboard.

Bloqueio externo concreto: sessão webESLexpirada para novosExcel. Prova legível: `docs/validacao/fretes/PROVA-CAPTURA-E-CONTEUDO-2026-11-05.md`. Falhas de servidor persistentes ou limites continuam gerando parada auditável; não prometer conclusão sob qualquer sobrecarga. Medição individual de gap/backoff Source permaneceUNKNOWN;ausênciaTLSindependente preservada.
