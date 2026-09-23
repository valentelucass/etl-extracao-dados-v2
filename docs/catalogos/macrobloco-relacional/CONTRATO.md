# Contrato do laboratório relacional — ADR0048

Construção local sintética de Manifestos6399 → Coletas6908 → Fretes6389.
Consumidor autorizado: `RelationalLaboratoryMain`, execução única, sessão
`ColetaTemporalLaboratorySession` e destino `localhost/ETL_SISTEMA_V2_SHADOW`.
Environment `LOCAL_SHADOW`, source `SYNTHETIC_RELATIONAL_LAB`, tenant
`SYNTHETIC_RELATIONAL_TENANT`, versão `synthetic-relational-v1` e run isolado.
Esses valores são parte do contrato, não parâmetros para conectar fontes reais.

| Relação | Captura e origem | Alvo demonstrado | Evidência e cardinalidade | Consumidor |
| --- | --- | --- | --- | --- |
| MC | Raiz Manifesto `/sequence_code`; componente `/mft_pfs_pck_sequence_code`, ambos INTEGER | Coleta `/id` INTEGER e componente ROOT, na data declarada pelo binding | Evidência `synthetic-*`, revisão1..1000; ONE_TO_ONE, ONE_TO_MANY ou MANY_TO_MANY explicitamente declarada | Resolver SQL; link ativo e backlog MC |
| CF | Coleta `/id` INTEGER; `synthetic_item_key` INTEGER ou STRING, extensão exclusiva da fixture | Frete `/id` INTEGER; `synthetic_pick_item` INTEGER ou STRING, extensão exclusiva da fixture, na data declarada | Mesma estrutura de evidência/revisão/cardinalidade; depende de MC resolvida para a Coleta de origem | Resolver SQL; link ativo CF e reconciliação tipada |

`RelationalBinding`, `RelationalCaptureContracts`, constraints de V029/V036 e
procedures de V030–V036 tornam essa matriz executável. O run fixa os três
fingerprints esperados. `ContractExecutionBinding` vincula a captura ao release
e à configuração, cujo fingerprint inclui todos os budgets. Hash descreve o
conteúdo e não concede permissão operacional.

| Entidade | Alias preservado | Frescor e presença | Filho independente |
| --- | --- | --- | --- |
| Manifestos | Pick é candidato; igualdade com sequence de Coleta não cria identidade | Coorte mais recente; reducers de status/métricas compatíveis com V022; raiz única | Picks de todas as coortes; MDF-e reduzido por raiz/chave/frescor, com número do mesmo par físico |
| Coletas | `/sequence_code`, separado de `/id` | Epoch second + nano de `stg.coleta_exact_time`; ABSENT/NULL/VALUE, bruto, parse e fallback permanecem no staging existente | Item de fixture conserva seu tipo; INTEGER:0 é válido |
| Fretes | `/corporation_sequence_number`, separado de `/id` | Frescor, campos de performance e presença atravessam mapper/gateway existentes; precisão do contrato de Fretes é preservada | Candidato de fixture separado da raiz; sidecar GraphQL permanece observacional |

Raiz, alias e componente não são intercambiáveis. STRING:0 difere de INTEGER:0.
Valores ausentes, nulos e inválidos ficam no staging; não se inventa binding.
Chaves string com padding/controle são inválidas, preservando o bruto: SQL
Server ignora espaço final até em comparações BIN2. A validação não remove
espaços para fabricar uma identidade. Zero de pick de Manifestos continua
submetido ao mapper desse domínio; o zero válido de item sintético não muda isso.

Repetições equivalentes agrupam a tupla inteira. `MIN(binding_id)` escolhe
somente a proveniência entre evidências equivalentes. Revisão nova explícita
substitui a anterior com links históricos; conflito na mesma revisão bloqueia.
A data-alvo precisa estar comprovada por captura selada no mesmo run/entidade.
Ausência incremental não apaga raízes, candidatos ou vínculos.

O erro51428 antecede precedência terminal quando o mesmo instante exato contém
conteúdo de raiz divergente. Coleta terminal pode prevalecer sobre observação
aberta mais nova; offsets equivalentes não criam atualização e1ns não colapsa
em milissegundos. V004/V010 e migrations aplicadas permanecem intactas.

O pipeline exige página terminal vazia comprovada. Página curta não encerra a
travessia; linha física repetida não vira raiz adicional. Erro, cancelamento ou
teto excedido invalidam guard/captura e revertem o savepoint; transação inválida
é revertida integralmente e propagada. A sessão sempre fecha com rollback.

Fila SQL: PENDING → CLAIMED → RESOLVED, DEFERRED ou QUARANTINED. Claim incrementa
a tentativa e cria lease; término exige dono e lease vigentes. Expiração fica
no histórico e reentra apenas até o teto. Falha temporária e abandono adiam;
contrato e conflito ficam em quarentena. Hidratação vazia não é sucesso. A
tentativa resolvida aponta para captura que contém o alvo/data/entidade exigidos.

Os budgets são explícitos: janela até366 dias de diferença, expansão0..31,
até100.000 linhas físicas por run, claim1..100, tentativas1..10, lease1..300s,
retry1..3.600s, página1..100 entidades e teto2..10.000 páginas. O planner envia
até64 partições por lote. Esses limites técnicos não ratificam SLA do fornecedor.

O executor usa `RuntimeTemporalPlanner` e os namespaces/modos do control plane.
Recibos de captura ficam em DEGRADED com motivo `REL_LAB_CAPTURE_ONLY`, liberando
o lease sem simular publicação. O progresso contíguo termina na primeira lacuna;
BOOTSTRAP/BACKFILL/REPLAY não avançam watermark incremental. Reabrir adapters na
mesma transação prova leitura/reconstituição SQL, não recuperação após COMMIT
ou queda de processo. O runner reconstitui sua fixture a cada invocação.

Reconciliação de captura: `considered = inserted + updated + noop`.
Resolução: `resolved = inserted + noop` e
`considered = resolved + orphaned + blocked + superseded`; `updated` mede links
históricos desativados por revisão e não é somado novamente às decisões.
Sucesso exige as três entidades capturadas e zero pendência, quarentena ou
candidato sem evidência. Três capturas vazias completas são válidas; ausência
de captura é incompleta. A conclusão de partição também verifica a evidência
e a fila em SQL. Os agregados retornam tamanho fixo, sem universo de chaves na JVM.

O caminho real de itens/bindings permanece UNSOURCED/BLOCKED onde faltam
contratos. GraphQL OBSERVATION_ONLY continua sem permit. Nenhuma saída deste
laboratório é um dos cinco fatos V2-036 ou das19 views publicadas V2-037.
