# Checkpoint 0270 — B16 Contas a Pagar por aceite interno de sombra

Data: 22/09/2026. Sucede o checkpoint 0269.

## Objetivo e resultado

Executar a menor rota independente autorizada após a política B16: aceitar
Contas a Pagar somente no escopo interno de sombra, sem afirmar contrato,
identidade ou paridade reais. A unidade foi concluída e a sublinha de CAP foi
marcada em `BLOCOS_ETAPA_2.md` após o registro em `STATES.md`.

## Evidência e limites

V2-029 já registra 28 campos, raiz/parcela/rateio explícitos, filtros
`issue_date + created_at`, captura/aplicação/consulta locais e rollback-only.
`mvn -o -Dtest=ExpansionLaboratoryContractTest test`, com JDK 17 apenas no
processo, passou com 15 testes, zero falhas, zero erros e zero ignorados. A
tentativa anterior sob JDK 25 foi recusada pelo enforcer antes de rodar testes.

O aceite não transforma `ant_ils_sequence_code` em chave de raiz/linha. A
amostra 8636 permanece limitada a 86 linhas físicas, sem `accounting_debit_id`
e com 85 valores distintos de `ant_ils_sequence_code`. Não houve fonte, rede,
banco, DDL/DML, escrita, agenda, deploy, credencial, publicação, cutover ou
produção.

## Próximas ações

1. Cotações/P17, somente após release tarifária, janela fechada, oráculo e
   autorização de fonte/alvo fornecidos pelo fornecedor, owner de tarifas e
   Negócio.
2. CAP/P17, somente após o fornecedor e Financeiro entregarem identidade tipada
   de `accounting_debit`, prova raiz→parcela→rateio, referência financeira,
   janela e oráculo independentes.
3. Faturas, somente após condição externa nova e registrada para a sonda 4924,
   além da identidade linha→título/documento/Frete de fornecedor, Fiscal e
   Financeiro.
