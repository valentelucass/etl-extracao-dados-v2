# 0073 — macrobloco relacional em construção

11/09/2026. EM_EXECUCAO. Objetivo integral A–J do prompt adotado, congelado em
target/macrobloco-relacional-20260911-01/request.md. Não encerrar após uma frente.
Anterior0072-coletas-integracao-temporal-encerramento-local.md, SHA256
844ce02280a9571f37fe757281648e3e15c67887e47685c599aa600c1b834a2b.
Sucessão0072→0071→0070→0069→0068→0067 lida e conferida; nenhum sucessor anterior.

Autorização: usuário adotou integralmente construção local, migrations aditivas,
baseline e testes sintéticos em localhost/ETL_SISTEMA_V2_SHADOW com Windows.
Somente DML transacional revertido; DDL fora da IT e preflight master obrigatório.
Sem API, .env/segredos, fornecedor, grants, reset, V1/dashboard, produção,
scheduler, deploy, commit/push ou orçamento de rodadas anteriores. Nenhum B64.

Inventário2494 e before byte a byte no diretório próprio da rodada. STATES recebeu
abertura preservando o histórico. ADR0048 e V029–V031 escritos, ainda NÃO
qualificados nem instalados. V001–V028 intactas. Baseline ainda será ampliado.
Java novo: RelationalLaboratoryPolicy, RelationalBinding, JdbcRelationalLaboratory,
RelationalSyntheticSource e LocalRelationalRuntime. POM torna o driver JDBC já
existente disponível na compilação para TVP tipado; nenhuma versão alterada.

SQL novo projeta capturas, preserva raiz/componentes/presença/tempo exato,
resolve bindings MC/CF com cardinalidade explícita, materializa somente destinos
de laboratório e integra recibos/backlog/claim/lease/tentativas/resumos.
Runtime reutiliza casos de uso, parsers, guards, auditoria JDBC, staging e sessão
rollback-only existentes. GraphQL continua observacional. O control plane usa
DEGRADED/REL_LAB_CAPTURE_ONLY para liberar lease sem fingir promoção operacional.

Nenhum teste executado ainda nesta fotografia. Cópia isolada de build em
preparação no diretório build/ da rodada (processo próprio74774; conferir retorno).
Nenhum efeito físico iniciado; nenhuma migration com resultado desconhecido.
Correções previstas antes de testar: replay de captura já selada deve aceitar
estado DEGRADED; completar política persistida e executor/runner de recomposição.
Revisar contrato de frescor, coortes/filhos antigos, evidência de alias ambíguo,
janela/fingerprint dos alvos e política de falha recuperável na sessão.

Próximas ações:
1. Compilar/formatar e validar schema com rollback; corrigir falhas antes de
   instalar V029–V031 em campanha própria reservada e com preflight.
2. Completar executor histórico/delta/hidratação/replay, runner e testes Java/JDBC,
   concorrência efetiva, reconciliação e medição em três escalas.
3. Concluir verify, regressões/validadores/scanners, baseline, diff contra inventário,
   matriz A–J, relatório e continuidade com snapshots explícitos dos deltas.

Aceites externos de R01/R02/V2-046a/b/V2-047/V2-012/V2-050/V2-038/V2-041
continuam específicos. Não impedem as frentes locais. Macrobloco ainda não
concluído e nenhuma frente recebe PASS por existir apenas código.
