# Checkpoint0068 — staging temporal e qualificação SQL de Coletas

10/09/2026. Predecessor0067-coletas-continuidade-conciliada.md,
SHA256 97df9f1b2b8fce5d4f0a8383572eb8e3dbdf569223a1c8efa53919d27f2f2938.
Pedido: “conclua todo o bloco”; decisões locais fundamentadas no STATES.

Implementados consumidor JDBC da captura, selo terminal local, bindings
escopados e cruzamento/contagens set-based em SQL próprio (proposta versionada).
Precisão epoch/nano, fonte/tenant, campos brutos e presença preservados.
Dados6908, migrations e baseline anteriores não alterados. ADR0046.

Inventário2433 e before completos em target/coletas-temporal-sql-20260910/.
Conferidos AGENTS/STATES/CONTEXTO_GLOBAL/RETOMADA, cadeia0067/ambos0066 e0058/0057.
Validador predecessor passou com evidência privada e9 guardas antes das edições.
Todos os2433 arquivos iniciais ainda iguais ao inventário antes de iniciar o
delta documental; nenhum código anterior foi editado. Cinco fontes Java novas.

Dirigido60/0/0/0 (22 casos novos) passou; quatro contraprovas adicionais de
consistência do JSON/texto foram acrescentadas depois, com verify-01 em execução.
Conferir checkpoint sucessor e recibo final antes de alegar suíte completa.

SQL físico: physical-01 recusou MATCH por classe de caracteres T-SQL inadequada;
rollback confirmado. Correção de hífen/underscore; physical-02 passou33 casos.
physical-03 passou34, incluindo exclusão por execução em duas sessões e sua
liberação após rollback. Todos os casos usaram DDL/DML transacional revertido
em localhost/ETL_SISTEMA_V2_SHADOW com autenticação Windows existente.
Cada reserva e resultado preservados; objetos próprios/escopo/@@TRANCOUNT
conferidos após cada caso. Nenhum reset, DDL permanente ou efeito desconhecido.

Projeção SQL produz candidatos observacionais; promotion_authorized permanece0.
Prova da trava não é corrida de promoção; representação nativa diferente fica
NATIVE_PRECISION_UNVERIFIED. Nenhuma API,.env,credencial,UAC ou B60/B62 novos.
Aceites COL-TIME-01/Q-COL-01/V2-012a/b/c/V2-041 não decorrem dessa prova.
67/115,191 rotas,zero AGORA; não ampliar o roadmap.

Próximas ações:
1. Concluir verify e conferir hashes das775 fontes efetivamente testadas.
2. Selar relatório/diff, sucessão documental, scanner e critérios A–D.
3. Encerrar B63 no escopo autorizado, mantendo aceites externos identificados.
