# Direção de desenho C78 e recorte de modelo puro

Decisão D-C78-RETENTION-DESIGN-01 / [ADR0067](../../adr/0067-design-de-retencao-c78-com-modelo-puro-sem-perfil-operacional.md). Status do desenho: **DESENHO_ADOTADO_SEM_PERFIL_OPERACIONAL**. Qualificação: **RECORTE_DE_MODELO_PURO_QUALIFICADO**.

Root adotou somente a direção técnica e este consumidor autônomo de revisão após o checkpoint0535. Os44 expected literais foram efetivamente comparados com a saída do modelo em cópia privada:44 correspondências, zero divergências. O recibo e os bytes executados estão no índice do checkpoint0536. Não houve integração ao runtime/build, nova API Java, perfil, flag ou owner físico.

| Arquivo | SHA256 |
|---|---|
| retention_design_model.py | dec2eeed63ba6f8f487c7b0237c75f0dc7a56de9fb702434a6995dab446e3a97 |
| contract-vectors.json | cf7c093fd1eb82ee9940edb4cfb1260e66d9979f2889c21bbb2fad34afce7fca |
| Resultado privado model-result-01.json |69a7efcb27c87edc85095d0434592f34ac2c8ee94e68dd537bba013c751e1b8f|

`DESIGN.md` distingue a especificação futura, o modelo reduzido e as entregas amplas ainda abertas. O modelo é Python stdlib: importar não lê arquivo nem inicia processo/thread/socket; seu CLI lê apenas o JSON vizinho e cria resultado novo na mesma pasta. Não importa adapters do projeto, provider ou JDBC. Os dados são sintéticos.

## O que os44 vetores qualificam

- 10 INTENT: geometria1..12/uma raiz/página1/batch1, terminal e três símbolos MODEL. Não contém perfil/operação/ciclo/template/per/janela/fuso/fingerprints/release/caller reais; não valida intenção operacional completa.
- 19 BUDGET: quatro buckets de créditos abstratos, soma int64 exata, custo/prova/sufixo, tentativa única, falha sem recreditamento e finalização sem reabertura. `COMMIT_ATTEMPT.costCredits` é inteiro positivo explícito, debitado antes do registro, separado de FINALIZE/sufixo. Não inventaria comandos, driver, deadline ou orçamento físico completo.
- 15 OBSERVATION: ACK/UNKNOWN/readback/rollback/close independentes; confirmações com símbolos de conexão/unidade/verifier distintos. Símbolos não autenticam recursos reais. Ausência não prova rollback; correspondência atual não reconstrói ack; indisponibilidade não é ausência.

Os expected completos são literais escritos a partir dos requisitos, não gerados pela saída do modelo. A comparação distingue boolean de número. Os três domínios são avaliados separadamente e não compõem elegibilidade de owner. Todos os envelopes/resultados, inclusive positivos, conservam `MODEL_ONLY_NO_EFFECT_AUTHORITY` e `authorizesPhysicalEffects=false`. Um crédito abstrato não corresponde a um SQL/HTTP/conexão ou tempo autorizado.

## Reproduzir somente o recorte

Usar uma nova cópia privada dos dois arquivos de input, conferindo os hashes. Executar Python canônico com `-I -B -S`, dentro do executor vigente serial2GiB, watchdog finito, e `--output` apontando para novo arquivo vizinho `model-result-NN.json`. O CLI recusa sobrescrita, path/link inadequado, campos duplicados, números não finitos e input acima262.144bytes/64casos/32eventos. Guardar source/input/output hashes, receipt e resultado; não escrever outputs de execução neste catálogo.

Exit0 significa somente correspondência dos expected do recorte puro. O resultado não autoriza Source, SQL, provider, commit, worker operacional ou emissão de permit. Não somar os44 aos testes Java nem ao JaCoCo. A reprodução não transforma fixtures em prova física.

## Entregas que continuam abertas

Intenção completa, máquina/gates do owner, inventário de budgets físicos, perfil operacional, catálogo/proteção atuais e confirmação/reconciliação concreta continuam pendentes. C78 permanece rollback-only; a aprovação E3 não é herdada. ESL está em standby. MainHTTP histórico UNKNOWN e recibo ausente permanecem assim. Nenhuma captura, retenção, paridade, TTL/legal hold/purge, release ou cutover foi implementado por este modelo.

Originais preservados: proposta0533 SHA c13832c068bc1350b8ad9294cb3a3860b034208055a4a3022805cb96af6e6f0a; detalhamento SHA e31a25064f7669f97541cdeba6298aff6fd0462c5dd966435ac824f1e20333fd. O recorte adotado não fecha retroativamente o escopo maior dessas propostas.
