# ADR 0041 — Origem lógica multiprotocolo e Usuários no SQL local

Data: 2026-09-09. Adotado para implementação B60; qualificação física pendente
do pacote e aprovação da seção4 do pedido. Complementa ADRs0016/0019/0034/0040.

## Decisão

`source_instance` continua sendo a conta lógica ESL, compartilhada pelos dois
transportes, com tenant e identidade canônica intactos. `source_catalog.source_kind`
passa a representar o primeiro protocolo registrado, preservado byte a byte.
Não se troca esse valor, não se cria origem duplicada e não se usa tipo genérico.

V024 acrescenta `ctl.source_protocol_binding` com a chave origem/protocolo.
O upgrade registra somente o protocolo original de cada origem. Um protocolo
adicional requer inserção administrativa explícita no pacote adotado; registrar
uma origem existente não concede o protocolo ausente. Runtime não recebe DML.
`ctl.execution_source_protocol` vincula cada execução ao protocolo: o upgrade
copia o catálogo anterior sem reinterpretar ocorrências; um trigger imutável
registra o protocolo das novas ocorrências na mesma transação do start.

Para novas ocorrências, Usuários exige GRAPHQL; as cinco verticais exigem
DATA_EXPORT. Outras entidades conservam o protocolo original e não recebem
autorização funcional por esta mudança. As validações de autorização consumida,
tenant, entidade, contrato e configuração continuam exigidas pelos consumidores
SQL. O registro de fonte operacional compara o protocolo com o workload da
capacidade consumida. A presença da origem ou de outro protocolo não basta.
Contrato/configuração continuam imutáveis em execution_attempt; o novo vínculo
não altera os materiais de fingerprint, crosswalk, source key ou recibos.

Usuários preserva current/history, dedupe, presença, conflito e frescor técnico
set-based em V007. V024 incorpora a recuperação revisada da preparação B59 e
cerca staging/apply com consumo de autoridade. O apply exige selo durável;
o selo exige a travessia preenchida/página20, candidate tipado e DQ completa
aprovada. Data Export mantém terminal vazia e mínimo de duas páginas.
O lock de namespace do apply Users precede os row locks, como no recovery.

## Defeitos da preparação encontrados antes da adoção

- A constraint do selo V015 exige duas páginas para qualquer entidade. V024
  admite uma na constraint comum e impõe o mínimo por protocolo no trigger;
  não libera Data Export com uma página.
- V007 grava o horário da aplicação tipada após o retorno do kernel genérico.
  Portanto `applied_at_utc >= published_at_utc`, com a publicação interna ainda
  dentro da transação externa. A preparação B59 comparava no sentido contrário.
  O reader novo confere essa ordem e o vínculo com aplicações/history, sem
  alterar os horários, algoritmos ou recibos anteriores.

## Regras e provas

| Regra | Origem e efeito | Contraprova |
| --- | --- | --- |
| SRC60-01 | STATES identidade3; protocolo adicional administrado | mesma origem sem binding recusa GraphQL |
| SRC60-02 | V2-020/042; ocorrência/tenant/contrato/configuração imutáveis | protocolo incompatível, tenant ou contrato trocado recusa |
| USR60-01 | B60B/ADR0040; selo após DQ completa e auditoria | uma página Users válida; uma página DE recusada; DQ parcial não sela |
| USR60-02 | V007/USR-01; leitura tipada temporalmente consistente | recibo ausente/NULL/adulterado não confirma publicação |
| OBS60-01 | B60E; tentativas observadas no transporte | retry/falha sem página concluída continua contado |

Owner-papel: Plataforma de Dados/Operações; autoridade do laboratório e fonte
real mantêm os papéis e limites ratificados. Prova estática/Java não equivale
a compilação SQL, transações, concorrência ou durabilidade física.

## Instalação e recuperação

V001–V023 e preparação B59 permanecem imutáveis. Baseline inclui V024. O pacote
físico deve conferir estado/identidade/permissões antes de aplicar delta aditivo,
comparar baseline por método isolado e preservar dados/históricos compartilhados.
Antes do commit: rollback transacional. Depois: correção versionada, retirada
da nova autoridade/grants conforme pacote e preservação da auditoria; nunca
reinstalar baseline, apagar dados, remapear origem ou renovar orçamento.

O aceite QUALIFICACAO_FISICA_LOCAL_USUARIOS só existe após execução autorizada
da matriz inteira. Sem essa prova, permanece implementação/teste por camada.
SHADOW_UPSERT_ONLY, GraphQL transitório; terminalidade não prova snapshot ou
ausência. Nenhum aceite de pai, Q-USR-01, desempenho produtivo ou cutover.
