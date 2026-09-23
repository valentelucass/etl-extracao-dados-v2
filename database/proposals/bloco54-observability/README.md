# Delta de observabilidade B54 — aplicado e verificado

O owner autorizou os três grants em 08/09/2026 e depois respondeu “pode” à quinta
campanha finita no saldo já aprovado. V019/V020 passaram em baseline/upgrade
rollback e nos cinco hashes de módulos antes do commit. Os três EXECUTEs foram
aplicados e os 25 exatos conferidos em nova conexão. Não repetir install-schema.sql
ou apply.sql sobre o checkpoint atual: eles exigem a fase anterior de V018/22 grants.

| Conta existente | EXECUTE adicional aplicado | Efeito máximo |
| --- | --- | --- |
| SERVICE / etl_v2_exec | ctl.usp_runtime_observation | Uma linha agregada da ocorrência consumida |
| SERVICE / etl_v2_exec | recon.usp_runtime_raise_alert | Alerta da ocorrência consumida; correlação derivada no SQL |
| OPERATOR / etl_v2_view | ctl.usp_runtime_observation | Uma linha agregada; nenhum efeito de escrita |

Nenhum entrypoint genérico, tabela, DDL ou role ampla foi concedido. Health é da
ocorrência selecionada, sem certificação global. V019 reaproveita o sink V2-023;
V020 vincula o consumo à ocorrência existente, à fronteira incremental e à janela
 temporal. As duas migrations agora estão aplicadas e são imutáveis.

## Evidências e checkpoint atual

O manifest aprovado para a aplicação tinha SHA-256
e5f53caf92dbc45880ac148815b461610a893236c7d337b06d2c0ee63e85672e.
O manifest atual acrescenta estado aplicado e atualiza os hashes documentais;
approvedApplicationManifestSha256 conserva a referência dos bytes aprovados.
Scripts SQL e migrations permanecem iguais aos usados na qualificação/aplicação.

Evidências privadas em target/bloco54/resume-approved-supplemental:
qualify-upgrade.sql.log, qualify-baseline.sql.log, install-pending.sql.log,
apply-approved-grants.sql.log e verify-new-connection.sql.log. O primeiro ensaio
histórico foi revertido por erro de previsão do hash; Bloco54SqlModules.psm1
passou a conservar o batch e a normalização SQL de CREATE OR ALTER. A quinta
campanha confirmou os cinco hashes reais antes de instalar, superando essa lacuna.

O JAR final passou em 16 casos, mais STATUS manual e diagnóstico. A matriz
separada confirmou 31 casos antes de falhar no gravador SQL após uma revogação.
A recuperação do mesmo caso restaurou SERVICE revoked=0/v4, validade e todos os
outros campos originais, com nova conexão confirmando 25 grants. OPERATOR e oito
scopes continuam v1; nenhum REPLAY/FORCE_RUN foi concedido. A falha foi corrigida
e testada offline. Não considerar a matriz interrompida integralmente aprovada.

Catálogo final: 6a4d90971e7a111d695ace72cac98983fef8a1ef6b080153b0897a8dc27af45a,
5.318 linhas, zero sessão restrita/transação de usuário remanescente. B54 conserva
99/128 reservas, cinco campanhas encerradas e 29 unidades de saldo. A
[campanha suplementar](supplemental-campaign.md) foi utilizada; essa é a fotografia anterior à sexta, atualizada abaixo.

## Verificação e recuperação

Executar verify.sql somente por leitura, com sqlcmd integrado, -S localhost
-d ETL_SISTEMA_V2_SHADOW -E -N -l 10 -t 20 -b -f 65001, neste diretório.
Ele exige os cinco módulos, 25 grants e os dois mappings/oito scopes originais.
O diagnóstico do launcher usa -ObservabilityProfile. Validator 053 conserva
intacta sua exigência histórica de 22 grants; não o relaxar para esta fase.

Para commit incerto de concessão, ler verify.sql e 053 separadamente. Um único
perfil exato precisa conferir; drift impede repetição. recover.sql exige 25 grants,
revoga somente os três deste pacote e valida 053 antes do commit. Essa recuperação
de grants está preparada, mas não foi aplicada: os 25 grants autorizados permanecem.
Schema, auditoria, dados e vigência ficam retidos; nenhuma conta é recriada/renovada.

A compensação da revogação do ensaio é distinta da recuperação de grants. Seu
recovery-reviewed.sql privado exige o estado SERVICE v3 revogado e já foi aplicado;
recusa repetição. O resultado SERVICE v4 mantém papéis originais, sem renovar.
Não repetir a instalação B53 nem usar os scripts de grants para reparar mappings.

Se uma campanha posterior autorizada acrescentar dois scopes REPLAY, usar somente
verify-retained-replay.sql para os 25 grants e exatamente oito scopes originais mais
dois SERVICE/REPLAY revogados. recover-retained-replay.sql revoga os três grants e
valida verify-original-retained-replay.sql, retendo os scopes. Esses scripts exigem
uma fase futura exata e não se aplicam ao checkpoint atual de oito scopes.

## Atualização após a sexta campanha

A sexta foi autorizada e encerrou com 109/128 reservas. Oito novos casos do harness
passaram: seis mutações entre decisão/consumo e dois processos para consumo/reinício.
OPS02 foi compensado sob a reserva 109 após corrigir o parser UTC, com hash/locks,
equivalência funcional e nova conexão. Estado atual: 5.328 linhas, SERVICE v15,
scope original 1 v10 e demais versões v1; 25 grants e oito scopes, sem papéis extras.
O catálogo permaneceu idêntico. Os SQLs e o delta deste pacote não foram alterados.

A revisão v4 implementa execução temporal e alerta de contrato recusado; passou em
1065 testes e em exportação/diagnóstico, sem RUN físico. O [pacote residual](../bloco54-residual-runtime/README.md)
prepara as 19 unidades existentes e exige autorização de sétima campanha, ainda
não concedida. Os resultados da quinta e seus hashes de aplicação ficam históricos.
