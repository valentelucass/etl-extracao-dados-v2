# B60 — proposta corretiva após compensação

Estado: preparada para revisão; **nenhuma nova campanha física autorizada ou executada**.
O pacote aprovado `a3d28ade…db57e` encerrou com `NOT_QUALIFIED`: primeira JVM
retornou 20/UNCONFIGURED; zero HTTP e efeitos de runtime. A compensação confirmou
SERVICE v19, replay/force=0, 32 grants, quatro escopos v2 revogados e V024 preservada.
Recibo físico anterior: `f2b35d102e8517b4fda6be67d2d84b8b1945077670470f77ef1d8f31307b605b`.

A correção permite `$` nos nomes de arquivo do manifesto, necessário para classes
Java internas do probe. Permanecem as proteções de caminho, duplicidade, hash,
dependências, identidade, validade e ACL. O JAR original reproduziu a recusa offline;
o corrigido passou no bundle real. Essa verificação não abre SQL nem comprova ACL.

O pacote `package.json` vincula todos os bytes necessários à nova execução e seus
limites. Deve receber aprovação própria pelo SHA-256. Não reutilizar o saldo ou
ledger anterior. Janela da proposta: 10/09/2026 00:00 UTC até 16/09/2026 00:00 UTC,
exclusivamente `localhost/ETL_SISTEMA_V2_SHADOW`, servidor RTR-SVW-002. O manifesto
interno do artefato mantém a janela B60 original de 09–16/09; o controlador impõe
o início mais restritivo de 10/09. Nenhuma validade de conta é renovada.

Identidades: administrador RTR-SVW-002\suporte; executor etl_v2_exec e observador
etl_v2_view, ambos Windows restritos. Credenciais DPAPI existentes são lidas apenas
após ativação, nunca copiadas para o pacote. Fonte sintética em 127.0.0.1:62160;
nenhum acesso ao fornecedor. Legado permanece o único escritor produtivo.

Preflight exige o catálogo V024 `cfd68974…8e34`, perfil exato SERVICE v19,
nenhuma sessão restrita concorrente e ausência de colisões. Não há DDL, instalação
de migration ou repetição das duas qualificações por rollback já documentadas.
Ativação transacional: os mesmos dois grants de Usuários, SERVICE 19→20 com
replay/force=1, quatro escopos existentes 2→3 ativos, duas novas políticas DQ v2
e seus oito checks. As políticas v1 continuam revogadas. Os flags são do principal
SERVICE e também alcançam seus escopos BACKFILL já existentes nas cinco verticais.

Matriz: os mesmos 74 casos e oráculos, com novos UUIDs, chaves e grupos sintéticos.
Inclui JAR oficial e probes separados, seis verticais, páginas, no-op/update,
cancelamento, negativas, falhas/ACK/HALT, recuperação, replay/force e concorrência.
O JAR oficial não contém classes de teste. Uma falha interrompe a campanha.

Tetos iguais aos anteriores: 60 min, 80 JVMs, 240 sqlcmd (232 ordinários + 8 de
recuperação), 400 HTTP; até duas JVMs e dois sqlcmd simultâneos; 512 MiB/JVM,
60 s/JVM, 45 s/sqlcmd, 30 s/comando SQL, quatro páginas/80 nós por JVM. Todos os
demais limites de sessões, comandos, commits, bytes e linhas constam integralmente
em `package.json`. Não há reembolso, repetição de OPEN nem renovação automática.

Compensação: SERVICE 20→21/replay=force=0; quatro escopos 3→4 revogados;
políticas v2 revogadas; dois grants retirados. Dados sintéticos, históricos,
políticas v1, V024, bindings, decisões e recibos são preservados. O multiconjunto
de hashes de linhas anteriores é conferido também após compensação, inclusive
se algum caso falhar. Só um mapping SERVICE e quatro escopos exatos são exceções,
validados pelos perfis. Recuperação usa o ledger próprio e o escrow sem novo OPEN.

Entrada após aprovação específica: `scripts/validation/Invoke-Bloco60CorrectivePhysical.ps1
-ApprovedPackageSha256 <sha256>`; `-RecoverOnly` exige campanha própria existente.
Nenhum aceite físico deve ser marcado com os resultados offline desta proposta.
