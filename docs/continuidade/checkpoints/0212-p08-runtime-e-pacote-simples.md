# Checkpoint 0212 — P08: runtime e pacote simples requalificados

Em 2026-09-21, a tentativa `p08-mn-final-verify-21` passou com `exit=0`,
rollback agregado confirmado, 492 testes sem falhas/erros, 247 classes
unitárias e 105 classes de integração. O pacote
`p08-mn-package-primary-21` passou smoke do JAR extraído, oito guardas de
controle e 21 guardas de entrada, sem filho/JDBC nas recusas pré-efeito.

M/N permanecem abertos: o pacote atual contém 180 membros e não inclui os
artefatos declarados para as sequências A/B. Restam as contraprovas diretas
pendentes, scanner, sucessão, selo/readback e gates por parcela. Não houve
fonte, produção, DDL, commit ou cutover.

Próximas ações autorizáveis:

1. Localizar e validar a origem declarada dos artefatos A/B antes de montar um pacote ampliado.
2. Executar contraprovas específicas apenas contra esse pacote ampliado e pinado.
3. Só depois executar scanner, sucessão e selo/readback previstos para N.

Evidência: `target/macrobloco-qualificacao-pacote-20260913-01/p08-mn-final-20260921-21/ledger.json`.
