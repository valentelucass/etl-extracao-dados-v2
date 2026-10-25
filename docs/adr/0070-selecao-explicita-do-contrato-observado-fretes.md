# ADR 0070 — Seleção explícita do contrato observado de Fretes

- Data: 24/10/2026.
- Estado: implementado e qualificado localmente; sem implantação ou admissão física.
- Regra: RT-FRETE-OBS-001. Escopo: contrato da origem e vínculo com a ocorrência; V2-025a/V2-022.
- Responsável técnico: Plataforma. Não altera regras comerciais ou autoriza gravação física.

## Problema e evidência

O checkpoint0558 comprovou INFO e travessias terminais reais6389, com37 fretes/114 campos no
dia17/10. O cliente oficial aceitou o release observado congelado. A execução principal, porém,
resolvia Fretes pelo catálogo sintético, cuja metadata e raiz `/data` não descrevem essa resposta.
Aceitar BODY na configuração não resolve essa escolha de contrato.

## Decisão

O documento de execução pode selecionar explicitamente
`"sourceContractVersion":"2026-10-24.fretes-observed-curl.v1"`.
Essa única versão conhecida exige FRETES, GET_WITH_BODY, uma tentativa e ausência do recurso de
perfil laboratorial. Outra versão, entidade, tipo JSON ou transporte é recusado antes da composição.
Não há path de arquivo, carregamento arbitrário ou descoberta de provider.

Sem a propriedade, a resolução anterior é preservada. A seleção faz parte do documento canônico
e muda o fingerprint do pedido; release, política e configuração efetiva continuam vinculados
pelo ContractExecutionBinding. Uma nova invocation não transforma a mesma ocorrência em outra.

O catálogo observado é empacotado como recurso próprio, com limite64KiB, SHA256 do arquivo e
recomputação dos três fingerprints do release. O recurso tem exatamente os bytes do release
independente de0557/0558. Não contém payload, ID ou valor de negócio. Os114 campos continuam com
tipo de metadata UNDECLARED; os16 filtros preservam seus tipos/fingerprints reais. Os15 paths
sempreNULL conservam cardinalidade UNOBSERVED e nenhum tipo não nulo inferido.

Exemplo de seleção, como propriedade adicional do documento de execução existente:

```json
{"sourceContractVersion":"2026-10-24.fretes-observed-curl.v1"}
```

Esse trecho isolado não é um pedido completo, autorização ou recibo de captura. A admissão Main,
identidade/tenant/caller, limites, observação atual da origem, proteção transacional, schema e
readback continuam a ser exigidos pelas fronteiras existentes. Os produtores atuais de admissão
C78/Coletas não são generalizados por esse campo. C78 permanece rollback-only.

## Verificação e reversão

O teste positivo do pedido novo falhou contra o código anterior com OPERATIONAL_REQUEST_SCHEMA;
a evidência RED foi preservada. Foram aprovados57 casos distintos por composição (43 PASS da
primeira rodada e14 da recuperação),7 IT do pacote, compilação estrita750/674, estilo e integridade.
As duas falhas iniciais eram expectativa string/lista e fixture laboratorial com intervalo0; foram
corrigidas sem mudar a política de produção e seus recibos permanecem preservados.
O catálogo confere igualdade com o release observado publicado e contraprova de drift para um
campo antes sempreNULL. AST44475/116295 atualizado,1547 inputs sem drift. JAR
`D81052B61702F98A12D7085FB85394180FED90C070622C35CA3D125F3EF96BCB`, sem implantação.
Evidências e limites no checkpoint0560; nenhuma cobertura global nova ou verify integral alegado.

Reverter a propriedade do pedido restaura a seleção anterior para novos pedidos; isso muda o
fingerprint e não permite reaproveitar autorização de uma ocorrência. Remover a implementação
e o recurso restaura a recusa do campo desconhecido. Nenhum perfil privado, SQL, serviço, conta,
V1/WMS, produção ou gate de retenção foi alterado por esta decisão.
