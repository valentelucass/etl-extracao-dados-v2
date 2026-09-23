# Checkpoint0070 — novo escopo de integração temporal de Coletas

11/09/2026. IMPLEMENTADO_NAO_QUALIFICADO. Predecessor
0069-bloco63-fechamento-tecnico.md, SHA256
7ef4147fab6fef7f5169a7a4d9dc53edac85d6512f73c8a5ad492f13767768ff.
Conferida a sucessão0069→0068→0067→ambos0066→0065. Não há sucessor anterior.

Pedido efetivo: integração completa no shadow e preparação verificável da
ativação, frentes A–E; implementação e testes neste chat, sem reabrir B63 nem
declarar B64. Prompt congelado em target/coletas-temporal-integration-20260910/request.txt.
Alvo autorizado exclusivo localhost/ETL_SISTEMA_V2_SHADOW, Windows existente.
Migrations aditivas permitidas; DML sintético com rollback; sem reset, API,
credenciais, UAC, grants, produção, commit ou push. Nenhum aceite externo novo.

Inventário2452 e cópia before completos no diretório próprio. Validador B63
com evidência privada e12 contraprovas passou antes das alterações. Estados,
manifests, SQL proposto, migrations V001–V024 e tentativas antigas preservados.

ADR0047: V025 recompõe auditoria Coletas necessária ao JDBC existente;
V026 adota proposta observacional; V027 representa segundos/nanos e acrescenta
view/procedure v2 e consumidor de raiz tipada no destino sintético próprio.
Overloads JDBC opt-in mantêm comportamento dos chamadores históricos. Runtime
local compõe streamers/gates/mappers existentes, binding e reconciliação SQL.
GraphQL permanece observacional, sem permit; Main/composition root intactos.

Preflight físico confirmou alvo ONLINE/NTLM e232 execuções preexistentes.
V2_SHADOW_JDBC_URL estava ausente no processo; será configurada somente no
processo opt-in para o alvo validado. Sem leitura de .env ou credenciais.
Auditorias antigas ctl.execution_audit/page_audit estavam ausentes no schema.

Schema-qualification-01 executou V025/V026/V027 em transação e reverteu,
sem objetos residuais. Aviso sobre largura do índice sintético foi corrigido
para PK nonclustered antes de instalar. Nenhuma migration desta rodada aplicada
duravelmente neste checkpoint; não confundir essa fotografia com sucessor.

Directed-01 falhou no Checkstyle em quatro linhas longas de testes; nenhum
teste executado nessa tentativa. Logs preservados; correções aplicadas;
directed-02 em curso. Conferir recibos/processo próprio antes de repetir.
IT preparada com fontes sintéticas contratadas, extração/auditoria, decisão,
consumo/replay e chamadas de staging/qualificação/consumo entre duas sessões.
IT ainda não executada. Nenhum resultado físico desconhecido.

Próximas ações:
1. Concluir compilação/dirigidos; qualificar novamente e instalar migrations
   separadamente da IT, com ledger/preflight e preservação dos232 registros.
2. Executar IT opt-in e concorrência; corrigir falhas e repetir afetados.
3. Concluir mecanismo do oráculo, verify, scanners, validadores, diff e entrega.

Falta externa: oráculo independente vinculado às capturas e correspondências
reais, janela/casos representativos, aceite nominal e requisitos V2-041.
Não impede trabalho local. Encerramento local somente após todas as frentes
independentes implementadas e verificadas, ou teste ambiental concretamente
impossível identificado com causa. Sem marcar testes pulados como aprovados.
