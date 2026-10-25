# ADR 0068 — Nomes da fonte 6389 no leitor e no mapeamento de serviço

- Data: 24/10/2026.
- Estado: aceito e qualificado no escopo local em0556; não concede admissão operacional.
- Escopo: RG-FRETE-018, FRE-02 e interpretação estrutural V2-025a.

## Evidência e problema

O INFO6389 observado nesta data possui 114 campos sem tipo declarado e 16 filtros.
Os filtros podem declarar `table` e `field` separadamente. O parser anterior
guardava somente `field`, perdendo, por exemplo, a identidade
`freights.service_at`. A metadata real tem 20 mudanças críticas em relação à
baseline sintética ainda ativa; corrigir o parser não torna essa baseline real.

Os 114 nomes observados incluem `service_at` e não incluem `servico_em`,
`cte_created_at`, `cte_issued_at` ou `criado_em`. O mapper da V2 procurava esses
quatro últimos nomes para a precedência de FRE-02. O contrato de partição já usa
`freights.service_at` como data de serviço (RG-FRETE-001). Na referência V1,
`integracao/mapeamento/graphql/fretes/FreteMapper.java:258` converte `serviceAt`
e o grava em `servicoEm`; essa leitura não prova equivalência de extrações.

## Decisão

1. Quando um filtro não tem nome técnico explícito, interpretar `table` + `field`
   como nome qualificado. Não prefixar novamente um campo já qualificado pela
   mesma tabela. Rejeitar tabela inválida ou qualificação contraditória.
   Nomes explícitos e metadados dos campos de resposta conservam a compatibilidade.
2. No mapper 6389, usar `/service_at` para a posição de data de serviço de FRE-02
   somente quando a propriedade histórica `/servico_em` estiver **ausente**.
   Preservar o path e o valor original na evidência e no payload; não fabricar
   um campo `servico_em` dentro da resposta da fonte.
3. A presença explícita, inclusive NULL ou valor inválido, conserva a precedência
   histórica. A ordem CT-e criado → CT-e emitido → criação → serviço continua a
   mesma. `updated_at` continua ignorado como frescor. Não introduzir aliases de
   CT-e/criação, heurística de datas ou relações a partir do nome do campo.
4. A interpretação temporal usa o parser conservador e a configuração de fuso
   já existentes. Não é atestado de fuso, precisão ou monotonicidade da ESL.

Exemplo sintético: `id=1, service_at="2036-03-17"` pode fornecer a posição serviço
na precedência local, com path `/service_at`. A data impossível `2036-02-30`
produz `INVALID_SERVICO_EM`; um `updated_at` válido não a substitui.

## Limites, testes e reversão

Os testes causais devem falhar contra o mapper anterior e passar com o ajuste,
cobrindo o conjunto de nomes observado, proveniência, data inválida e presença
histórica. A fixture de INFO conserva apenas estrutura técnica derivada, sem
valores de fretes, labels ou corpo HTTP original. O teste deve continuar
recusando essa metadata contra o catálogo sintético, sem relaxar o gate.

Reverter os dois ajustes de produção restaura o comportamento anterior; não há
DDL, alteração de banco, credencial ou configuração externa. O catálogo ativo,
classificação TRANSITIONAL, completude, quotas, pin C78 e rollback-only permanecem
iguais. Release real da resposta, comparação terminal, persistência, paridade e
implantação exigem suas provas próprias. O timeout/HTTP422 da ESL continua
independente dessas correções locais.
