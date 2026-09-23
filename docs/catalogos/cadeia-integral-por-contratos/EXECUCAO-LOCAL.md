# Execução local reproduzível

## Conteúdo e pré-condições

A entrega contém o pacote ZIP do runtime, exemplos completos `set-a`/`set-b`,
inputs, oráculos, hashes, diff/overlay e recibos. Os exemplos são sintéticos,
autorados antes de SQL. O JAR não precisa de JUnit nem de `target/classes` para
consumi-los. O índice dos exemplos vincula os bytes ao SHA-256 desse JAR.

Use Java 17 e Windows com autenticação integrada já existente. O único alvo é
`localhost/ETL_SISTEMA_V2_SHADOW`, com schema V2 até V102 já instalado. As migrations
versionadas estão no pacote; a execução abaixo não instala schema. Preserve
V2-041 e as restrições de [G01–G08](ENTRADAS-E-EFEITOS-EXTERNOS.md).

Antes de executar, confira o SHA-256 do ZIP contra o selo externo e o inventário
de membros; extraia o runtime para um diretório local curto. `QualificationPackage.psm1`
fornece `Expand-QualificationPackage`, que confere arquivo, manifesto, membros,
caminhos e hashes. A prova entregue usou essa função e iniciou `java -jar` sobre
o arquivo efetivamente extraído, em processo filho com PID, teto e logs próprios.

## Comando do cenário completo

No PowerShell, estando no diretório do runtime extraído, a variável abaixo vale
somente no processo atual e nos seus filhos. Ajuste o caminho local do exemplo.

```powershell
$env:V2_SHADOW_JDBC_URL = 'jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=45000'
$exemploIntegral = 'C:\caminho-local\examples\set-a'
java -Xmx512m -Dfile.encoding=UTF-8 '-Djava.library.path=./native' `
  -Dshadow.local.integration.enabled=true `
  -Dshadow.local.integration.profile.active=true `
  -jar ./etl-dataexport-v2.jar local-scenario run `
  --input "$exemploIntegral/input.json" --oracle "$exemploIntegral/oracle.json"
```

Repita com `set-b` para a segunda massa. O processo compara cinco fatos e todas
as 19 saídas SQL; somente a comparação íntegra chega ao preview das 33
responsabilidades. A sessão JDBC bloqueia commit e reverte sua transação ao
fechar. A prova entregue também compara as contagens agregadas de todas as
tabelas antes/depois, no alvo conferido pelo master. Igualdade de contagens é
uma conferência adicional; a garantia transacional vem da sessão com rollback.

O código 0 significa comparação local completa. O código 20 recusa entrada ou
configuração; o código 40 registra divergência de dados/fato/SQL. As provas
negativas entregues removem USER, alteram o valor esperado de SQL02 e trocam um
destino LOC_FREIGHT por chave inexistente. Os mutantes têm arquivos e hashes
próprios; nenhum altera o exemplo íntegro ou habilita fixture implícita.

## Tetos e retomada

O CLI tem deadline de 240 segundos; o processo de prova externo foi limitado a
300 segundos, heap de 512 MiB e logs de até 16 MiB. A escala B24 continua uma
prova local limitada, sem qualificação de desempenho produtivo. Os limites do
contrato e da paginação estão em [CONTRATO.md](CONTRATO.md).

Ao interromper uma execução, reconcilie primeiro o PID registrado, o resultado,
as contagens e o rollback. Uma saída perdida não autoriza repetir efeitos de
estado desconhecido. Use outro nome de tentativa para um retry conhecido;
preserve o recibo anterior. Não há serviço, agendamento, API autenticada, deploy
ou cutover nesta execução.

## Regressão do código

O build qualificado executa `spotless:apply verify` com o perfil
`shadow-local-integration`, a propriedade `shadow.local.integration.enabled=true`
e `v2.measurement.receipt=true`. O perfil inclui `Integral*IT.java`. Os relatórios
reconciliados por `classname#name`, com quatro skips históricos preservados,
estão vinculados em [verificacao-local.json](verificacao-local.json).

O diff usa `--no-renames` e foi aplicado em cópia isolada da base 0154. A cópia
usa metadados Git próprios e `* -text` em `.git/info/attributes` para conferir
bytes históricos, inclusive finais de linha. O overlay e `removals.json` são
uma segunda forma de aplicar a mesma revisão. Selo/readback registram o conjunto
completo; o índice Git real permanece inalterado.
