# Checkpoint 0211 — P08 M/N interrompido no teto de VerifyPhysical

- Ordem: `p08-mn-final-20260921-18`; alvo confirmado em `master` como
  `localhost/ETL_SISTEMA_V2_SHADOW` ONLINE, Windows integrado e rollback-only.
- A única `VerifyPhysical` da revisão atual terminou `exit124` ao atingir3.600s.
  Rollback agregado confirmado, logs UTF-8 dentro de16MiB e nenhum processo
  próprio residual. Relatórios de integração já finalizados não traziam
  failures/errors; a suíte incompleta não é PASS.
- Pela parada obrigatória, pacote, reprodução, provas JAR, smoke, controles,
  scanner, sucessão, matriz e selo não foram executados. M/N continuam abertos;
  39/45 e67/115 preservados.
- Ledger: `target/macrobloco-qualificacao-pacote-20260913-01/p08-mn-final-20260921-18/ledger.json`.

Próxima ação somente mediante nova ordem independente, com orçamento revisto e
pré-flight novo. Não reutilizar este ledger nem recibos históricos.
