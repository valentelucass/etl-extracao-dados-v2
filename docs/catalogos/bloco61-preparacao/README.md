# B61 — atenção registrada e proposta de consolidação local

Estado: **ATENCOES_REGISTRADAS_B61_PROPOSTO_NAO_EXECUTADO**.
O usuário pediu atualização do STATES e prompt de continuidade. Implementação
e testes do B61 ainda não começaram. Nenhum novo aceite, orçamento ou rota.

[Prompt](../../runbooks/prompt-bloco-61-consolidacao-local-e-paridade.md) e
[checkpoint0050](../../continuidade/checkpoints/0050-avaliacao-e-preparacao-bloco61.md).

O manifesto sucede exatamente `bloco60-renovacao/manifesto.json`, preservando
as revisões anteriores de quatro arquivos. O adaptador de validação histórico
usa essas revisões; o novo validador confere as revisões atuais e os demais
arquivos preservados. O recibo B60 não é atualizado para esta fase documental.

Validação local, sem executar SQL/JAR operacional:

```powershell
./scripts/validation/Test-Bloco61Preparation.ps1 -SelfTest -IncludePrivateEvidence
./scripts/validation/Test-Bloco60RenewedClosure.ps1 -SelfTest -IncludePrivateEvidence
./scripts/validation/Test-ContinuidadeAgentes.ps1 -IncludePrivateEvidence
./scripts/validation/Test-Gpt56ChatTrail.ps1
```

O modo `IncludePrivateEvidence` verifica também os hashes históricos do recibo
B60, resolvendo somente as quatro revisões explicitamente sucedidas. A presença
de um hash não comprova autorização ou execução nova. Os registros da avaliação
comparativa ficam preservados em `target/avaliacao-v2-v1-20260910/`.
