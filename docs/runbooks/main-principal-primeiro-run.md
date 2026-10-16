# Primeiro RUN da Main principal

O pedido HERMES_4c5eefc033c7 e o complemento funcional do Lucas foram executados
pela classe pública `br.com.esl.etl.v2.bootstrap.Main`, em JVMs próprias Java17.
Não foi usado DevLocalMain como substituto. O V2 é uma CLI de execução única:
este teste não precisa manter um servidor ativo.

O comando de classe é:

```text
java -cp <classes;recursos;bibliotecas qualificadas> br.com.esl.etl.v2.bootstrap.Main run --config <configuração privada> --request <request>
```

O POM também declara essa classe no manifesto de `etl-dataexport-v2.jar`.
Nesta unidade, a execução foi direta pelas classes compiladas, com dependências
pinadas já presentes no cache e recursos atuais; não houve instalação ou deploy
do JAR administrado. Os argv, classpaths, configurações, classes, recursos,
reservas locais, saídas completas e fechamento estão no storage privado do
pedido, em `hermes4c5eefc033c7/runtime01/`.

## Evolução real

| Tentativa | Resultado observado | Consequência |
| --- | --- | --- |
| 01, PID75308 | JVM exit1: caminho absoluto de -Xlog recusado | Não entrou na Main. Lançador privado corrigido para arquivo relativo e cwd próprio. |
| 02, PID67016 | Main exit20: política deny-all | Configuração oficial carregada e preflight executado; nenhum request ou fluxo de negócio. |
| 03, PID10576 | Main exit2: configuração/pedido inválido | Request histórico real fornecido, mas a guarda de alvo precedeu sua leitura e ocultou a causa. |
| 04, PID6208 | Main exit2: WINDOWS_WORKLOAD_TARGET_REQUIRED | Diagnóstico corrigido; mesma recusa de segurança e mesmo código de saída. |
| 05, PID39204 | Main exit20: autorização UNCONFIGURED | Configuração não secreta preparada, request lido, plano/contrato/scope construídos; gate do artefato administrado recusou antes da conexão. |

Na tentativa05, a base URL foi recuperada seletivamente da configuração
provisionada existente, sem selecionar tokens ou senhas. Origem/tenant/policy
vieram do preparo histórico. O target Windows estrito foi apenas metadata da
cópia dev local, sem inferir TLS, isolamento, writers, credenciais ou quota
comprovados. O request histórico permaneceu inalterado: sua leitura não reutilizou
permit, fence ou orçamento nem reexecutou a ocorrência de negócio.

A classe verifier da própria pasta de classes local recusou a localização
administrada exigida. O controle faz essa verificação antes de carregar a
configuração da autoridade, verificar ACL ou abrir SQL. Essa recusa é o limite
observado, e não uma autorização para instalar artefatos, renovar manifests ou
modificar permissões.

## Diagnóstico e validação

MAIN-STARTUP-DIAG-01, origem neste RUN03 e revisão das guardas existentes:
`Main.runScopedOperationalAction` pode expor somente os dois códigos estáticos
`WINDOWS_WORKLOAD_TARGET_REQUIRED` e `VERIFIED_LOCAL_WORKLOAD_TLS_REQUIRED`.
Demais falhas continuam com mensagem genérica; não imprimir a mensagem arbitrária
da exceção, paths privados, URL, credencial ou payload. Exit2, autorização,
TLS e demais gates permanecem iguais.

Exemplo: auditoria desligada mais `run --request` informa o primeiro código antes
de ler o arquivo de request. O novo
`MainOperationalStartupDiagnosticProcessTest` executa a Main em processos reais
e verifica essa guarda, a recusa de TLS não verificado e a não exposição de path
em falha não revisada. Responsável técnico: Runtime e Qualificação; documentação
e integração: Supervisor Codex. Não houve revisão humana alegada.

Três casos novos passaram em rodadas2+1, com reuso explícito dos dois anteriores;
seis regressões pertinentes da Main passaram com o mesmo bytecode final.
Compilação estrita, formatter e Checkstyle passaram. Tentativa01, contraprova RED,
primeira qualificação falha e correções de assertions/classload foram preservadas.
Carregar uma classe não prova chamar seu método; a causa usa também o ramo,
a saída real e a ordem das guardas.

Os processos próprios encerraram, com saídas completas abaixo64KiB e deadlines
finitos. Não houve HTTP fornecedor, provider de segredo, SQL/TDS, captura, escrita
de domínio, DDL, grants, Flyway, alteração do V1/PM2, deploy ou produção.
SQL Server16.0.1000.6 e sua instalação devem permanecer intocados.

O fluxo ETL de fonte/persistência não iniciou e nenhuma tabela foi populada.
Seu avanço exige uma composição operacional vigente compatível com o perfil
efetivamente aprovado e suas provas/reservas; C78 não herda a autoridade Windows
da Main. STOP429 continua na operação dependente de quota. Não repetir a mesma
tentativa para tentar liberar esses gates.

Rollback da correção de diagnóstico: remover apenas os dois códigos públicos
adicionados e seu teste, mantendo a recusa genérica original. Não remover
evidências, dados retidos, regras de segurança ou arquivos preexistentes.
