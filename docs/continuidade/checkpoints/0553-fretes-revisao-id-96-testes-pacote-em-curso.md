# Checkpoint0553 — revisão ID:96 testes passaram; pacote em curso

24/10/2026. Continuação de0552 no mesmo pedido direto do usuário.

## Alteração concreta e provas concluídas

`DataExportTemplate.FRETES` passa da versão `2026-08-31.v2-025a.1` à revisão semântica `2026-10-24.fretes-id.v1`, com default `id asc`. Filtros, per máximo100, GET_WITH_QUERY, classificaçãoTRANSITIONAL e bloqueios de completude preservados. Demais templates não alterados. Não é solução universal de timeout:0552teve terminal37/per25 e falha422/per50. Não promove os114campos reais ao release estrutural sintético.

Dois testes existentes atualizados: serialização ID/versão nova e uma nova invocação que exige recusa da ordem por minuta pelo gate **antes de chamar a fonte**. RED contra produto anterior:8casos,3falhas esperadas,zeroerros/skips. Após mudar produção: compilação estrita749main/673test e **96casos de13suítes passaram**,semfalhas/erros/skips; formatter dirigido e Checkstyle passaram. Nenhum limiar, exclusão ou política foi relaxado.

Manifesto ativo versionado com fingerprints computados do código compilado; catálogo6908 e Usuários permaneceram iguais. Baseline anterior preservada em `docs/catalogos/contratos-primeira-onda/historico/manifesto-2026-09-01.v2-025a.3.json`, SHA-256 `D7910FF0E3D0F78BEE57AAAD5178561DB8BCB0D0D253448531B606BCEF45950C`. README explica escopo e limitações. Validador PowerShell atualizado para exigir versão nova, ordem exata/transitória e hash histórico; **execução desse validador ainda pendente**, prevista antes do índice.

## Pacote e estado atual

JAR candidato foi gerado. A primeira verificação de7IT teve1aprovação/6erros porque o snapshot omitiu `database/migrations`, arquivos lidos pelo fixture de integridade. Isso é falha da preparação local, **não SQL executado**. Cópia de `database` e `scripts/validation` completada, relatórios falhos preservados; recuperação executa somente os7IT, sem repetir96testes ou alterar classes/JAR.

**EM_EXECUÇÃO:** observar sessão protegida de recuperação/resultado real antes de repetir qualquer etapa. Pacote ainda NÃO qualificado neste checkpoint. Último JAR qualificado continua0547; não adotar o candidato ou afirmar aceite integral.

Base externa:`C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/fretes-ordem-id-20261024-01`:

- `red/result.json`: RED causal confirmado,Job0/pico676802560B.
- `prepare/result.json`: compilação e fingerprints candidatos,Job0/pico674496512B.
- `qualify/result.json`:96GREEN,formatter/Checkstyle/package passaram; permanecefalho por6erros dosIT. Não reclassificar esse recibo.
- `recover/`: recuperação em andamento; preserva `initial-failed-package-reports` e verifica os mesmos bytes.
- `qualification-plan.json`, snapshot prévio de manifesto/template e relatórios nativos preservam procedência. Primeira chamada Maven com regex contendo pipe foi recusada pelo cmd antes de teste; evidência em `red-failed-cmd-pattern`,corrigido para filtros individuais,sem mudança de controles.

Nenhum ESLHTTP/GraphQL/SQL novo na qualificação; fixtures locais podem usarHTTPloopback. Nenhum deploy,instalação,DDL/Flyway,serviço,credencial,permissão,identidade,V1 ouWMS alterado. SQL16.0.1000.6,C78rollback-only,MainHTTPhistóricoUNKNOWN e gates restantes mantidos.

## Retomada imediata

1. Conferir recuperação7IT/bytes/JAR,sem repetir os96aprovados.
2. Executar validador catálogo e atualizarAST protegido,conferindo hashes finais.
3. Sincronizar estado final,distinguindo código local qualificado de captura/retencão/paridade e do timeout ainda dependente de investigaçãoESL.
