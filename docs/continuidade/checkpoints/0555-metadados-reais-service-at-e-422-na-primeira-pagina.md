# Checkpoint 0555 — Nomes reais de Fretes corrigidos; HTTP422 também na página1

## Objetivo, autoridade e estado

24/10/2026, após09:58BRT. Anterior: [0554](0554-fretes-id-qualificado-terminal37-timeout-remanescente.md),
SHA256 `42B530E9E9EF4CF053B94B672B735A16167CB45EA3A47595F3D57BA37C64691C`.
O usuário pediu: “sua missao é continuar isso aqui ate conseguir tudo que precisa”.
Continua o objetivo de extração completa por Data Export; este checkpoint não o substitui por
qualificação local. Estado: correções locais qualificadas por composição; fonte real e captura
completa ainda abertas. AST da revisão está em execução no Job protegido, sem nova fonte/SQL.

## Mudanças materiais

1. `DataExportTemplateInfoParser`: filtros com `table` e `field` separados passam a conservar a
   identidade qualificada, como `freights.service_at`. Preserva nomes explícitos, campos sem tabela,
   campos de resposta e nomes já qualificados. Recusa tabela inválida ou qualificação contraditória.
   Cinco testes novos demonstraram três falhas/um erro no parser anterior e passaram com o ajuste.
2. Fixture técnica derivada dos metadados observados em24/10:114 campos sem tipo declarado/16filtros,
   sem labels, valores de fretes ou corpo HTTP original. O teste confirma **20 mudanças críticas**
   contra a baseline sintética e exige que o gate continue recusando-a; não houve promoção automática
   do INFO a contrato de resposta, nem alteração do catálogo ativo.
3. `FreteDataExportRecordMapper`: usa `/service_at` na posição serviço de FRE-02 quando a propriedade
   histórica `/servico_em` está ausente. Preserva path/valor/payload originais, precedência e NULL
   histórico. Data inválida continua em quarentena; `updated_at` não vira frescor. A correspondência
   de serviço já existe em RG-FRETE-001 e no mapper V1 `serviceAt`→`servicoEm`, lido sem alteração.
   Três casos novos: RED3/duas falhas contra o mapper anterior; todos passaram com a correção.
4. [ADR0068](../../adr/0068-fretes-service-at-e-metadados-tabela-campo.md) e RG-FRETE-018 documentam
   a tradução local e seus limites. Nenhum alias de CT-e/criação, heurística de datas, relação ou
   atualização incremental foi inferido. Fuso/precisão do fornecedor e paridade seguem sem atestado.

## Qualificação local e pacote

Base externa: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/fretes-contrato-real-20261024-01`.

- Compilação estrita:749fontes de produção/673de teste;73recursos de teste, incluindo a nova fixture.
- Parser/contratos/profiler/gateway:55testes PASS. Após fixture e mapper:24testes PASS, com oito casos
  sobrepostos ao conjunto anterior. União identificada por classe/nome: **71casos distintos**, sem
  falhas/erros/skips. Não somar55+24 como79 testes distintos.
- Formatter dos quatro Java alterados e Checkstyle1422Java PASS.
- Pacote: **7IT PASS**, sem fonte externa ou SQL.1319classes/45recursos conferidos byte a byte com a
  compilação; oito bibliotecas iguais às da revisão anterior. Não é verify integral/cobertura nova.
- JAR atual, sem implantação:
  `fretes-contrato-real-20261024-01/snapshot/target/etl-dataexport-v2.jar`, SHA256
  **`42251A42CEC7938EAA529C61E3045E31215334131904C5C64C1232814130D398`**.
- `final-recovery/result.json`, SHA256
  **`B48C6B10BF5454FE1481171F40B9DB1DCA927DBFD49B9A72138B5916D16350D6`**,
  contém a composição, inputs finais, integridade e referências à falha anterior preservada.

Falhas de instrumentação não foram apagadas ou convertidas em PASS. `green` compilou, mas parou
antes dos testes por nome inexistente na seleção; `green-recovery` executou a seleção correta.
`final` passou24testes/7IT e depois falhou no coletor de hashes por prefixo Windows longo duplicado;
`final-recovery` corrigiu somente a conferência, comparou todo snapshot com fonte e pacote/libs,
sem repetir os testes aprovados. Recibos/resultados originais continuam falhos. O AST ainda não
recebe aceite neste checkpoint; observar `final-index-memory-job-receipt.json` antes de concluir.

## Fonte real: quatro unidades fechadas, oito tentativas

Os scripts próprios usaram os componentes Java do V2, segredo existente por stdin, cliente HTTPS
com transporte GET_WITH_QUERY, uma tentativa, sem redirect/fallback, intervalo mínimo3s,
timeout30s/resposta10MiB, mutex de sonda e executor de memória original. Cada unidade reservou
orçamento/deadline antes de HTTP, parou na primeira falha e descartou saldo. Nenhum retry automático.

| Unidade externa | Calls | Resultado observado e limite |
|---|---:|---|
| `fretes-contrato-real-20261024-01/live` | 2 | INFO aceito; primeiro DATA25/p1 parou em ContractDriftException durante observação estrutural. Status exato/corpo dessa resposta não foram retidos. A fronteira inicial não cobria elemento de `invoices_mapping`; não afirmar causa exclusiva sem stack original. |
| `fretes-contrato-real-20261024-02/live` | 3 | Após qualificar fronteira exata `invoices_mapping/*` para lista de STRING, INFO e DATA25/p1 processados; DATA25/p2 falhou com IllegalStateException. Status/corpo não retidos: não reconstruir422/timeout dessa tentativa. Sem terminal nem comparação. |
| `fretes-contrato-real-20261024-03/live` | 1 | INFO aceito; falha local ao serializar Optional antes de qualquer DATA. Código/recibo preservados. |
| `fretes-contrato-real-20261024-04/live` | 2 | Após corrigir/testar DTO sanitizado, INFO aceito; DATA15/p1 retornou **HTTP422**, capturado pelo gateway e classificado pelo texto estrito da exceção. Nenhum segundo DATA. |

Os sucessores tiveram deltas causais explícitos e preparo offline próprio; não reabriram recibos
nem renovaram a consulta antiga50/p2. As unidades02/04 não completaram a travessia/amostra planejada.
Os mapas/digests ficaram somente em memória. Nenhum candidato de release de resposta foi concluído;
as contagens ou nomes não substituem os tipos, nulidade, grão, resposta terminal e comparação.

O INFO atual comprova parsing114/16 e20diferenças contra a baseline sintética. Isso não comprova
quota futura, disponibilidade de DATA, tipos declarados ausentes ou admissão Main/SQL.

### Requisição atual para ESL

GET `https://rodogarcia.eslcloud.com.br/api/analytics/reports/6389/data`, **sem body**:

```json
{
  "search[freights][service_at]": "2026-10-17 - 2026-10-17",
  "page": "1",
  "per": "15",
  "order_by": "id asc"
}
```

24/10/2026, início**09:44:59,386BRT**, término**09:45:01,723BRT**, status**422**.
O corpo/headers/request-id não foram retidos nesta prova Java. Não chamar esse novo422 de timeout
comprovado sem corpo/log ESL; o timeout exato anterior50/p2 continua preservado em0554.
`esl-investigation-request.json` reúne os dois requests e a pergunta específica, sem segredo.
Solicitar logs/causa/correção da execução6389 nesses horários; a primeira página pequena também
falhou, portanto diminuir `per`, ordenar por ID ou dividir relatório não são soluções demonstradas.
Nenhuma mensagem foi enviada ao gestor/ESL e nenhuma nova chamada está programada.

`source-units-summary.json` contabiliza8tentativas e hashes dos quatro resultados. A primeira
exportação tinha totalnull e foi preservada com sufixo `invalid-total`; a contagem atual foi refeita
explicitamente. O corpo genérico histórico de timeout permanece apenas na evidência própria.

## Preservação e próximos passos

SQL/TDS/GraphQL/DDL/Flyway/deploy/commit de banco nesta unidade:zero. V1/WMS não reiniciados ou
interrompidos. SQLServer16.0.1000.6, contas, segredos, permissões, identidades, ACL, serviços,
listeners/certificados/trust, produção e arquivos preexistentes preservados. C78rollback-only;
MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED; 43RAW/53exclusões e gates anteriores mantidos.

1. Observar o AST já em execução e reconciliar inputs/recibo; atualizar os índices de continuidade.
2. ESL investigar o request422 atual e o timeout anterior. Não repetir consulta fechada apenas por
   tempo, mudar credencial ou atribuir causa a franquia/chave/SQL interno sem evidência.
3. Após retorno/correção demonstrável, qualificar o release real de resposta114campos com travessias
   terminais e comparação; só depois bindings/admissões e prova de processamento/persistência/paridade.
   A melhoria local de mapper não autoriza captura nem fecha a extração completa.
