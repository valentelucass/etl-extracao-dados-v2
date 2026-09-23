# 0121 — Regressão integral em execução e entrega em consolidação

EM_EXECUCAO A–N; construção32/45, aceite histórico67/115. V098 instalada e imutável.
Predecessor0120 SHA256 7c4fe6103b4e2d5d05b1bcc36417ed417b992929772cfdb250d2c93c79cd18ad.
Prompt adotado SHA256 990e481060eefd1c1ee01c168d46ba85c7d384edd5d61ce746ef9974d8d01a33.
Escopo permanece localhost/ETL_SISTEMA_V2_SHADOW, fixture sintética e rollback.

plan01 terminou11IT/1erro: Plan3, Isolation4 e cap Raster1 passaram. Runtime
falhou porque SQL10.Inicio passou a admitir nulo na V097; metadata exportada
confirmou somente essa divergência. Catálogo corrigido sem enfraquecer outras colunas.
final-gaps01 terminou18IT/3erros: Dimensions8, Reader2 e Runtime3 passaram;
CivilTime2 recusas passaram,3 positivos usavam nomes de coluna errados na asserção.
Corrigida somente a consulta de teste para os aliases reais exportados.

verify-physical-analytic-01/session94968 iniciou com orçamento prévio1800s,
Java17/heap512MiB, verify integral e perfil físico sem filtro de testes. Processo
Maven próprio33064. Às03:17 locais, novos39relatórios físicos já produzidos,
regressão expansão ainda executando. Resultado agregado permanece desconhecido;
consultar exit.json antes de repetir. pom inclui sete classes novas antes omitidas
pelo padrão AnalyticLaboratory*IT; todos os padrões anteriores preservados.

Scanner offline01 terminou oito falsos positivos em três fixtures conhecidas.
Guardas novos01 falharam apenas no caso positivo. Inspeção da captura regex provou
que V071 corresponde ao identificador SQL normalized nas comparações de token,
e não ao literal do algoritmo como inicialmente suposto. Exceção exata corrigida
por regra/caminho/valor; contraprova altera o identificador. Quatro demais casos
negativos passaram. Nova revisão ainda precisa executar guardas5 e anteriores11.
Poda da enumeração metadata usa as mesmas exclusões antes de descer em target.

N: módulo de sucessão exata e gerador de entrega criados, somente parsing estático;
manifesto analítico ainda ausente, validadores não aprovados. Whitelist exata17
arquivos preexistentes inclui scanner; snapshots iniciais2704 preservados.
Exportadas definições reais19views para view-definitions-01.json privado, visando
matriz final673colunas. JAR40 ainda não executado nem qualificado.

Próximas ações:
1. Reconciliar verify integral; corrigir falhas observadas e executar JAR40 com
   comparação da identidade das fontes/classes da revisão aprovada.
2. Executar scanner/contraprovas afetados e consolidar campos, provas, escalas,
   planos, quadro45 e relatório sem promover gates reais.
3. Sincronizar STATES/trilha/RETOMADA, selar sucessão/diff contra inventário inicial,
   validar continuidade e entregar A–N integral. Sem pedido de continue.
