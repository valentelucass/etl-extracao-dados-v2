# Checkpoint 0042 — correção offline do B60 e pacote corretivo

## Identificação e objetivo

- Data: 10/09/2026; mesmo B60, sem novo bloco funcional.
- Anterior: 0041-bloco60-campanha-interrompida-compensada.md;
  SHA-256 91a389f1452acc924f06a0222162539893dcb9d8fe6b0b8291d04296c8e1a78d.
- Pedido: continuar corrigindo, verificando e testando sem depender de mensagens
  de “continuar” enquanto o usuário dorme.
- Estado: TESTADO_NA_CAMADA / OFFLINE_CORRECTED_PHYSICAL_APPROVAL_PENDING.
- Escopo adotado: corrigir o bloqueio do bundle, testar, preparar a retomada contra
  V024/SERVICE v19 e preservar a execução anterior. Não renovar seu OPEN/orçamento.

## Correção e provas

- AdministeredArtifactVerifier admite `$` em caminhos relativos do manifesto.
  Hash, traversal, duplicidade, dependências, ACL, escopo e validade permanecem.
- Novo teste falhou antes da correção com ENTRY_INVALID; depois passou, inclusive
  rejeitando bytes alterados de RuntimeUsersPhysicalProbe$Fault.class.
- O helper test-only carregou o verificador do JAR original: exit12/ENTRY_INVALID.
  Com o JAR corrigido em bundle real: exit0; variantes expirada e inconsistente
  recusadas por escopo e hash. Não houve SQL nem prova de ACL nesse helper.
- Maven offline/Java17 verify: 1.394 testes, zero falhas/erros, quatro skips;
  formatter, estilo, arquitetura e cobertura passaram. Heap limitado a 512 MiB.
- Evidências: target/b60-correcao-20260910/{red-01,green-01,verify-01,semantic-red,
  semantic-green-02,review-01}. Preservadas também a primeira geração incompleta
  (grupo longo) e a primeira checagem semântica (validFrom divergente), corrigidas
  na revisão final sem alterar o JAR nem ampliar sua janela original.

## Pacote preparado e autorização

- database/proposals/bloco60-correcao/package.json; SHA-256
  cc4f84cb37f697248d8a096249480985b252a7303c2408fb4cc4614bc829a167.
- 224 arquivos vinculados, 74 casos com novos IDs e oráculos preservados.
- Alvo localhost/ETL_SISTEMA_V2_SHADOW, janela 10–16/09/2026 UTC; mesmos limites,
  identidades e permissões do predecessor. Sem DDL. SERVICE19→20→21;
  quatro escopos Users2→3→4; políticas v2 novas, v1 preservadas revogadas.
- Compensação inclui perfil exato e multiconjunto de linhas históricas também
  após falha. Exceções explícitas: um mapping SERVICE e quatro escopos Users.
- A aprovação anterior a3d28ade…db57e foi consumida por campanha encerrada.
  A pergunta adicional sobre uma campanha corretiva permanece sem resposta.
  A ausência do usuário não autoriza novo OPEN; nenhum SQL novo foi executado.

## Continuidade e próximo passo

- Inventário inicial: 1.752 arquivos, cópias exatas em target/b60-correcao-20260910/before/.
- Manifesto sucessor: docs/catalogos/bloco60-correcao/manifesto.json; seis arquivos
  existentes alterados, snapshots completos; cadeia até B55 preservada.
- Consolidação final e diff: target/b60-correcao-20260910/final/.
- Progresso permanece 67/115=58,26%, 48 pendentes, 191 rotas, zero AGORA.
  Nenhum checkbox novo; QUALIFICACAO_FISICA_LOCAL_USUARIOS não comprovada.
- Não fechar V2-022, Q-USR, V2-012a/b/c, bootstrap/sweep/medição/E2E/release/cutover.
- Para continuar fisicamente: conferir a aprovação específica do pacote corretivo,
  sua validade e hashes; só então executar o controlador novo. Parar na primeira
  falha e compensar. Não reutilizar pacote/ledger anterior nem renovar automaticamente.
- Não pedir nova confirmação para ações que a futura aprovação cobrir; não abrir
  campanha enquanto a pergunta estiver pendente. SQL sintético não vale como fonte real.
