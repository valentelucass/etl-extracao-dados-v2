# Checkpoint0066 — avisos Java dos testes corrigidos

10/09/2026. Manutenção local solicitada pelos dez diagnósticos enviados pelo
usuário. Estado: TESTADO_NA_CAMADA. Anterior:
0065-coletas-ligacao-temporal-verificada.md, SHA-256
f7170a5ffba5ad880df663e1c8925ff8acbb204eedadba128881bd22cb462648.

## Escopo e decisões

Somente cinco arquivos de teste editados. Métodos de fixtures de arquitetura
continuam disponíveis à reflexão, com supressão pontual do diagnóstico unused.
Contagem de registros por iterador preserva o consumo completo. Constante e
import sem uso removidos; assertNotEquals verifica RuntimeRoleSet contra String.
O import assertFalse do teste temporal já estava ausente ao iniciar.

Inventário de 2425 arquivos, snapshots e ação prevista preservados antes dos
ajustes em target/avisos-java-20260910/. Alterações externas no teste temporal
e em Test-ColetasTemporalLink.ps1 foram observadas e preservadas. Nenhum manifest
ou validador histórico foi reescrito por esta manutenção para esconder drift.

## Evidência executada

- verify-01: interrompido por Spotless antes dos testes; formatação do teste
  temporal corrigida externamente enquanto a cópia isolada era verificada.
- verify-02: Maven offline/Java17/512MiB em cópia própria, 1530 testes, 1526
  aprovados, zero falhas/erros e quatro skips. Enforcer, Spotless, Checkstyle,
  arquitetura, compilação estrita e JaCoCo aprovados.
- Eclipse JDT da extensão redhat.java: seis arquivos compilados com as classes
  testadas, diagnósticos indicados habilitados e failOnWarning; exit 0, log vazio.
- Evidência: verification-summary.json, eclipse-diagnostics.json,
  code-changes.json e diff-testes.patch no diretório privado da rodada.

Sem fonte real, credenciais, banco físico, DDL, orçamento de fonte, release,
runtime produtivo, commit ou push. Ledger físico não se aplica a testes locais.
Nenhum efeito externo desconhecido ou nova aprovação de critério funcional.
Recuperação: usar o diff próprio e os snapshots para reverter somente estes
ajustes, preservando alterações externas e as evidências anteriores.

## Continuidade

Manutenção dos avisos encerrada; nenhuma pergunta ou teste desta frente pendente.
O trabalho funcional de Coletas conserva os critérios e próximas ações do
checkpoint0065. Não reexecutar campanhas de fonte ou promover aceites por causa
desta limpeza de testes. Consultar STATES e a evidência da frente antes de agir.
