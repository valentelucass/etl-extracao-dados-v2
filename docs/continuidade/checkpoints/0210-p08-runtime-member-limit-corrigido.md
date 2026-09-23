# Checkpoint 0210 — P08 runtime member-limit corrigido

## Fotografia

- A falha histórica do guard extraído foi explicada: o pacote declarava724
  membros, aceitos pelo montador, mas `QualifiedPackage` impunha512 membros e
  514 arquivos no runtime.
- O runtime agora usa `MAX_MEMBERS=1024` e
  `MAX_CONTENT_FILES=MAX_MEMBERS+2`; o teste de limite e as descrições de
  arquitetura acompanham o contrato.
- `QualificationBoundedContractsTest`, `ArchitectureRulesTest` e
  `QualificationContractTest`:26 testes, zero falhas/erros.
- `p08-runtime-member-limit-build-17`: exit0, sem timeout. O pacote-candidato
  `p08-runtime-member-limit-candidate-17` tem724 membros e manifesto
  `86e26ccd96389e588d254b88306b8d6513fb8fd453c599b38fc7f842c9b6680a`.
- `p08-extracted-guards-runtime-limit-17`:21/21 PASS_LOCAL,
  `childrenCreated=0`, `jdbc=NOT_STARTED`; `missing-member` observou
  `QUAL_JSON_MEMBERS`.

## Limites preservados

Não houve SQL, DDL, DML, leitura de auditoria `ctl`, fonte, produção ou
commit. M e N seguem abertos e sem aceite; não foram executados smoke, guard de
controle, scanner, selo/readback, sucessão ou as contraprovas pendentes.

## Próximas ações

1. Executar as contraprovas diretas ainda pendentes contra o candidato corrigido.
2. Executar smoke e guard de controle sob seus contratos próprios.
3. Atualizar scanner, selo/readback, sucessão e gates de M/N somente com seus
   recibos correspondentes.

Evidência completa:
`target/macrobloco-qualificacao-pacote-20260913-01/p08-runtime-member-limit-offline-ledger-17/ledger.json`.
