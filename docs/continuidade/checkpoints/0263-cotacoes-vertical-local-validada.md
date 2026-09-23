# Checkpoint 0263 — linha local de Cotações validada

Data: 22/09/2026. Sucede o checkpoint 0262.

## Ação e resultado

A amostra read-only 6906 de 0262 tornou pertinente uma validação dirigida da
vertical já implementada. Foi executado
`pwsh -NoProfile -File .\scripts\validation\Test-CotacoesV2027ShadowVertical.ps1`.
Resultado: PASS para o contrato V2-027, exercício rollback-only e prova local
de concorrência da vertical de Cotações.

Essa é prova de implementação em sombra e limita-se à camada local. A amostra
da ESL confirma somente a forma de uma página não terminal; ela não fornece
release oficial, referência tarifária, estabilidade/tenant, completude, oráculo,
paridade, aceite nominal ou produção. Por isso foi marcada exatamente uma caixa:
“Validar a vertical afetada, deixando claro o limite entre teste local e fonte
real”, em `BLOCOS_ETAPA_2.md`. As duas linhas anteriores de B16 e todos os pais
continuam abertos.

## Registros e integridade

`STATES.md` registra a camada e a limitação; a trilha referencia a conclusão;
o checklist tem SHA-256
`f0698c9c270f30a54c36716705e1e1f6d4646877b9c18d43934e55b4fa21a0e5`.
`git diff --check` passou, com apenas avisos CRLF/LF preexistentes. Não houve
rede nesta validação, banco, DDL/DML, escrita de domínio, deploy ou corte.

## Próxima ação

Não repetir a amostra 0262. A próxima parcela de Cotações depende de nova ordem
se for necessária paginação adicional e, para aceite de B16/P17, de contrato,
referência tarifária e oráculo que a página de dados não substitui.
