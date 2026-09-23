# Runbook — Q-FND-02 perfis offline de Fretes e Localização

## Fronteira

Este runbook executa somente testes locais sobre dois perfis de entidade, três canais e fixtures
sintéticas. Não acessa rede, fonte, configuração externa, banco ou runtime. `PREPARED_NOT_EXECUTED`,
`ORACLE_REQUIRED` e `NOT_EXECUTED` são pós-condições obrigatórias, não pendências mascaradas.

## Gates

Use JDK 17 apenas no processo e execute:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress -Dtest=Qfnd02*Test test
.\scripts\validation\Test-V2012FretesLocalizacaoProfileExtension.ps1 -ArtifactsOnly
.\scripts\validation\Test-V2012CharacterizationFoundation.ps1
.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify
```

O primeiro validator deve informar lock 43, dois perfis, três canais, duas entidades e 48 mutações.
O validator Q-FND-01 precisa continuar verde sem alteração de seus 43 arquivos.

## Falha e rollback

Não existe estado externo a reverter. Remova apenas os artefatos novos do bloco em uma revisão
humana futura se a decisão for rejeitada; nunca altere Q-FND-01 para acomodar a extensão. Drift no
lock retorna `Q_FND_01_BYTE_DRIFT`; manifesto ausente retorna `Q_FND_02_MANIFEST_MISSING`.

O bloco não autoriza executar Q-FRE-01/Q-LOC-01, receber payload real, criar relação Fretes–
Localização, inferir tradução temporal ou habilitar completude, snapshot, sweep, publicação,
bootstrap, paridade ou cutover.
