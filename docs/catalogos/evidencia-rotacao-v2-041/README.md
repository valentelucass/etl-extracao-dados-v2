# Contrato de intake sanitizado — V2-041

Este catálogo transforma a evidência mínima do runbook
[`conter-e-rotacionar-segredos.md`](../../runbooks/conter-e-rotacionar-segredos.md) em uma entrada
estrutural fail-closed. Ele não executa rotação, não consulta secret store, não faz health check e não
autoriza rede, fonte, release ou deploy.

O resultado local máximo é `STRUCTURALLY_VALID_UNVERIFIED`. Isso significa apenas
que o recibo sanitizado tem todas as classes, papéis, resultados categóricos, contagens, timestamps e
scans esperados. `V2-041` continua em `EXTERNAL_HOLD` até o owner validar a autenticidade da referência
restrita e atualizar primeiro o `STATES.md`.

## Invariantes do recibo

- JSON UTF-8 estrito sem BOM, sem comentários, trailing comma, propriedade duplicada ou campo extra.
- Exatamente as seis classes do inventário canônico: ESL REST, ESL GraphQL, ESL Data Export, Raster,
  SQL Server legado e sombra/testes V2.
- ESL REST deve estar `RETIRED`; Raster deve estar `ROTATED` ou `DISABLED`; as demais classes devem
  estar `ROTATED`.
- O atestado deve declarar o valor anterior como invalidado, o rollback como pronto e todos os passos
  aplicáveis como `ASSERTED_PASS`; o validator não transforma essas alegações em fatos verificados.
- Os sete owner-papéis são obrigatórios, com a identidade nominal mantida somente no registro restrito.
- Scanner auxiliar, Gitleaks do worktree e Gitleaks do histórico devem declarar escopo completo,
  pelo menos um item inspecionado, zero item não inspecionado/oversized, zero finding e o mesmo SHA
  do `HEAD` local validado.
- Nenhum segredo, nome de credencial, URL, tenant, payload, cursor ou identificador de negócio entra
  no recibo. O schema contém somente enumerações, booleanos, contagens, timestamps e SHA do repositório.

O arquivo [`atestado-completo.synthetic.json`](fixtures/atestado-completo.synthetic.json) é apenas uma
fixture de contrato. O marcador `SYNTHETIC_CONTRACT_TEST_ONLY` impede que ele seja aceito como evidência
operacional.

## Validação

Valide primeiro o contrato e suas contraprovas sintéticas:

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-V2041RotationAttestation.ps1 -ContractOnly
```

O modo é obrigatório e exclusivo: omitir o modo ou informar os dois modos falha fechado. O sucesso
acima é identificado como `CONTRACT_VALID` e declara `evidence=NOT_EVALUATED`; não é um aceite de
evidência.

Quando Segurança/Operações produzirem um recibo sanitizado, mantenha a evidência detalhada no canal
restrito e coloque temporariamente somente o JSON minimizado no caminho ignorado pelo Git
`target/v2-041/sanitized-attestation.json`. Valide-o sem imprimir seu conteúdo:

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-V2041RotationAttestation.ps1 -ValidateEvidence
```

O recibo operacional usa `evidenceKind=UNVERIFIED_EXTERNAL_ATTESTATION`. Mesmo com exit code zero, a
saída é `STRUCTURALLY_VALID_UNVERIFIED`, nunca `UNLOCKED`: autenticidade, validade da
referência restrita e aceite nominal não podem ser provados pelo parser local.

Não copie o recibo detalhado, referência restrita, nomes, hosts ou valores para Git, chat ou log. Não
use este gate para liberar V2-025d sem a reconfirmação separada de janela/teto, nem para herdar
autorizações de Q-*-01, Raster, feed/NVD, banco ou produção.
