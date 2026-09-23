# 0190 — autonomia reafirmada; autoria da fixture contra JAR

## Identificação e objetivo

- UTC2026-09-20T15:10:24Z; concluir P04, sem fragmentar em pedidos de continuação.
- Anterior0189, SHA-256 `0d2f62e39df6bfea4c96a5a7e3439e15c7ff2aa1fbebb819277e3901c23428e9`.
- Estado: EM_EXECUCAO, preflight offline; I/J ainda não aceitos.
- Critérios: escopo P04, SEQ-03/CP-01/FIX-01; regra permanente de12/09 reforçada.

## Autorização e limites

Usuário respondeu à proposta de correção/prova: “uma entrada e uma saída”, ler
documentação, testar e encontrar, sem ficar pedindo autorização já existente.
Continuar correções locais e testes sem reconfirmação. A prova física proposta
fica limitada a uma campanha de uma tentativa3600 s, após preflight, no alvo
localhost/ETL_SISTEMA_V2_SHADOW, integrado/sintético/rollback-only, duas travas,
512 MiB/1800/240/60 s. Não inferir orçamento ilimitado nem reabrir ledgers antigos.
P05/P06–P08, DDL, fonte, segredo, produção, commit e recovery durável excluídos.
Nenhuma reserva física nova nesta unidade. Registrar antes do efeito dependente.

## Alterações e decisões

- Baseline3570 arquivos: `target/p04-continuidade-0190/baseline.json`, SHA-256
  `9c471fc05befab50a1e80cbd02ac41494433597ccbe4711b039d6d8c85acf47b`.
- STATES/TRILHA: regra destacada no início, mais reforço na seção de interação.
- QualificationPackageFixture usa PackagedFixtureRuntime test-only: classloader
  com pai de plataforma, JAR primeiro e CodeSource conferido; fecha após autoria.
- PackagedFixtureBindingIT: verifica14 oráculos e três bindings adulterados sem
  JDBC. Guard de src/main e oráculos independentes permanecem inalterados.
- Controlador privado ganhou ArtifactDirected: monta JAR/bibliotecas e testa,
  sem perfil físico/SQL. Nenhuma mudança de POM/dependência/schema.
- Rejeitado: trocar fingerprints após gerar ou relaxar guard para passar teste.

## Execução e evidência

| Passo | Camada/limite | Esperado | Observado | Evidência |
| --- | --- | --- | --- | --- |
| Preparação | Offline | mapa consistente |PASS33/48/9 | Test-TrilhaPreparation |
| Binding | Offline/JDK17/512 MiB/1500 s |17 testes e guards corretos |EM_EXECUCAO; JAR montado | p04-0190-binding-offline/process.json, stdout, futuro result |

Raiz de execução: `target/macrobloco-campanhas-integrais-20260915-01/`.
Não repetir preflight se retorno se perder; consultar process/result. Não há
JDBC novo, aceite, processo físico ou efeito de domínio nesta unidade.
Recuperação: delta cirúrgico contra before, preservar worktree e ledgers.

## Próximas ações

1. Observar preflight e corrigir falha técnica offline, se houver, sem nova pergunta.
2. Com PASS, confirmar alvo/processos e reservar a campanha finita antes do Physical.
3. Conferir todos os critérios I/J; sincronizar STATES, trilha, verificações e índice.

Parar efeito inseguro, não confundir atualização/checkpoint com entrega final.
Nenhuma conclusão física é antecipada por este documento.
