# Checkpoint 0023 — Usuários GraphQL testado localmente
Data: 2026-09-09. Anterior: 0022-bloco58-coletas-local.md,
SHA-256 68ad475aba75b30d0c3a4ab3436cbd3552e4e980b7cdb26e4f871ac12b148155.
Objetivo permanece A–D do prompt adotado pelo usuário; não encerrar apenas A/B.
A e B: TESTADO_NA_CAMADA. C/D: EM_EXECUCAO.

Autorização expressa “Execute o Bloco 58 local”; alvo V2 neste workspace.
Somente testes sintéticos, correções comprovadas e continuidade. Zero API real,
SQL inclusive leitura, campanha/runtime físico, instalação, credencial, agenda,
cutover, commit/push, orçamento consumido/renovado. Sem aprovação pendente.
Inventário/recuperação em target/bloco58-local/initial*; 1488 originais preservados.
Uma página/lote por vez; bytes65536, linhas1000/caso, páginas100, depth16,
paths256, nodes4096; Usuários máximo20. Nenhuma coleção de vida da execução.

Usuários usa ponte test-only Bloco58GraphQlAccess para GraphQlStrictJsonParser,
GraphQlResponseParser e GraphQlContractGate atuais. A observação vem do envelope,
nunca do oráculo. ExtrairUsuariosGraphQl stageia e a projeção compara o registro
entregue com expectativa literal vinculada ao hash do ADR0019.
42 casos de identidade/nome; INTEGER/STRING distintos, presença tri-state,
Unicode e fronteiras 255/256. Quarentena de mapper é saída tipada; o relatório
separa quarantineLayer=MAPPER de firstRefusal de parser/contrato/travessia.
Término local, página20/21, página curta, errors, shape, cursor repetido/cíclico,
caps, cancelamento e falha de staging têm comparação independente.
Contraprova altera valor/tipo esperado e exige divergência sem expor valores.
Nenhum defeito produtivo novo confirmado em Usuários; perfil histórico imutável.
ABSENT aceito localmente não prova nullability remota nem update current/history.
USR-01/02 físicos permanecem SQL; USR-03/04 preservados sem fonte temporal nova.

| Execução | Camada | Observado | Evidência |
| --- | --- | --- | --- |
| focused-b-01 | Java offline17/heap512 | 94 testes, zero falhas/erros/skips, exit0 | result.json, log, XML, reports |
| contracts-unit-b | estática | Q-FND-02 e pacote COT exit0 | results.json e logs |
| A anterior | Java/static | 98 testes e seis gates aprovados | checkpoint0022 |

B log SHA-256 a107f25230ac88a32fc590a930dbf6b3ee4a7a1c3ff111b4cfabaa9547defe70.
B result SHA-256 75d60cf8c83eae4c0faeda58e07223968c621d0c7a12af8792fcf00f60d796a8.
Sem processo ativo ou resultado desconhecido. Nenhum aceite novo: 67/115,
48 pendentes,191 rotas abertas. Fontes/provas anteriores não foram reexecutadas.

Próximas ações:
1. C: isolar relatórios dos testes B57 pela propriedade characterization.reports,
   preservando defaults/assertions/fixtures; rodar regressão focada COT/LOC/FRE/MAN.
2. Suíte final Maven offline verify em saída nova; fechar Enforcer/Spotless/
   Checkstyle/JaCoCo. Não repetir Java verde por ajuste só documental.
3. D: STATES → trilha → validadores/sucessão/guards, diff próprio, reverse --check,
   recuperação e recibo final. Não modificar manifest/receipt antigo.

Bloqueios externos: oráculo/janela/garantias/bindings e qualificação V2-041 dos
papéis ESL/domínio/Segurança; SQL/paridade/relações nos gates próprios. Matriz em
docs/catalogos/bloco58-local/README.md. Não reabrir fonte COT/MAN ou P08–P11.
