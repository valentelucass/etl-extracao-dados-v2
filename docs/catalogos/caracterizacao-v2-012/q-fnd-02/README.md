# Q-FND-02 — perfis offline de Fretes e Localização

Esta extensão aditiva contém exatamente dois perfis de entidade e três canais de fonte isolados:
Fretes reúne o contrato Data Export 6389 e um subcontrato GraphQL sidecar estritamente
observacional; Localização reúne somente o contrato Data Export 8656. O sidecar não cria uma
terceira entidade, não amplia o contrato 6389 e nunca é autoridade de raiz ou frescor.

Todos os perfis permanecem `PREPARED_NOT_EXECUTED`, com `ORACLE_REQUIRED` e
`providerEvidence=NOT_EXECUTED`. As fixtures são sintéticas e exercitam 48 mutações fail-closed.
Nenhuma evidência desta pasta caracteriza o fornecedor ou prova completude, snapshot, relação,
paridade, publicação, sweep ou cutover.

O lock Q-FND-01 contém 43 paths e hashes literais. Seu agregado canônico é
`ab99feb5e413deb44bf0dfc26f737453fa802cd6b293805ba82aaa1c56b9029a`; qualquer mudança encerra o
gate com `Q_FND_01_BYTE_DRIFT`.

Validação local:

```powershell
.\scripts\validation\Test-V2012FretesLocalizacaoProfileExtension.ps1 -ArtifactsOnly
```

Não há comando de fonte, rede, banco ou runtime neste catálogo.
