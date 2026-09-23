# Checkpoint 0271 — B16 Usuários por aceite interno de sombra

- Data: 2026-09-22T19:37:00Z.
- Anterior: `0270-b16-contas-a-pagar-aceite-interno-sombra.md`, SHA-256 `7a4ff3b8675a3641d3b0296f870367c4206d7fb883303e9f25f97e9c69ef80a2`.
- Objetivo: concluir uma unidade B16 elegível pela dispensa explícita do owner para aceite interno de sombra.
- Estado: aceite interno de Usuários concluído; P16 agregado e P17 reais abertos.

## Autorização, alvo e limites

A decisão de 22/09/2026 registrada em `STATES.md` dispensou release oficial como
pré-requisito genérico de B16, exigindo evidência própria por entidade. O alvo
desta unidade foi somente a vertical local de Usuários e seu registro documental.
Não houve consulta externa, banco, alteração de código/schema, DDL/DML, deploy,
agenda, credencial, controle do ETL legado ou cutover. Recuperação: reverter
apenas o delta documental desta unidade se a evidência for refutada, preservando
todo o histórico e as alterações preexistentes.

## Delta e prova

O inventário inicial mostrou working tree já sujo, sem pacote novo de Cotações
para P17. A matriz `qualificacao-p07-p33/matriz.json#/entities/0` registra
V2-033 como implementação local de current/history GraphQL e mantém o oráculo
real pendente; `#/stages/7` preserva P16 agregado aberto. O delta desta unidade
foi registrar o aceite interno de Usuários em `STATES.md`, espelhá-lo na trilha
e marcar somente sua sublinha em `BLOCOS_ETAPA_2.md`.

| Critério | Camada | Execução | Resultado |
| --- | --- | --- | --- |
| Extração e runtime de Usuários | offline/JDK 17 | `mvnw.cmd -o '-Dtest=ExtrairUsuariosGraphQlTest,RuntimeUsersIntegrationTest' test` | 45 testes; zero falhas, erros ou ignorados; BUILD SUCCESS |
| Estilo e compatibilidade | offline/Maven | mesmos gates da execução dirigida | Enforcer, Spotless e Checkstyle passaram |
| Integridade documental | working tree | `git diff --check` nos documentos afetados | sem erro de whitespace; aviso de CRLF/LF preexistente em `STATES.md` |

Não houve defeito demonstrado nem mudança de código. Fixture e execução local
não provam contrato, completude, histórico real, paridade ou aceite produtivo.

## Retomada — até três ações

1. Cotações/P17: fornecedor, owner de tarifas e Negócio devem entregar release
   6906, referência tarifária autorizada, janela/oráculo independente e
   autorização do canal e alvo sombra; então caracterizar e comparar.
2. Usuários/P17: fornecedor/owner de dados deve entregar oráculo GraphQL nominal,
   correspondência escopada e prova de completude em janela autorizada;
   Segurança/Operações devem autorizar canal e alvo. Histórico precisa de fonte
   própria autorizada.
3. Outra entidade B16 só pode receber aceite interno com evidência própria e
   teste causal; nenhuma pendência externa pode ser marcada por analogia.

Faturas/4924 continuam em `HTTP_NON_2XX`, sem condição externa nova registrada
para repetir a sonda. A parada é na ausência dos inputs acima; não há efeito
desconhecido desta unidade.
