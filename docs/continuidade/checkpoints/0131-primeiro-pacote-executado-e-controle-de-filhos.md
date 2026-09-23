# 0131 — Primeiro pacote executado e controle de filhos

EM_EXECUCAO A–N, 13/09/2026. Predecessor0130 SHA256
fcdf089c0a4c9bebbe2a6e2ae5f818d117a7625eca09f6bdfaf9cce9839e60df.
Continua integralmente o pedido de qualificação/pacote adotado. Não é entrega
final. Construção37/45, aceites67/115, mesmos limites locais e gates externos.

O pacote candidato passou pela primeira execução independente da árvore de
fontes. candidate-package-02 contém JAR, oito bibliotecas, DLL Windows/x64,
167membros, contratos, fixtures, oráculos, schema, configuração, SBOM e licenças.
Foi extraído em diretórios novos com espaço e Unicode; o entrypoint conferiu
seu JAR, classpath único, Java17 e manifesto. Os recursos vieram do payload.

| Artefato candidato | SHA256 |
| --- | --- |
| Revisão de entradas congeladas | cff39027b71f269130980d4ad325348346ec1e9d1c565208d42e21e19d865538 |
| Manifesto | c228ba6a9303d55cf8d43f8bbf13e66deac86fc36e3d7c36a11d4de79e1e8459 |
| ZIP | e21a086ea641644d3aec9119570242d00e99164447e67722429aa1bc01f982b2 |

Evidências em target/macrobloco-qualificacao-pacote-20260913-01:

| Tentativa | Resultado observado |
| --- | --- |
| campaign-entrypoint-compile-01 | Falha Werror por close declarando InterruptedException; corrigida sem suprimir warning |
| campaign-entrypoint-package-02/03 | Builds de desenvolvimento passaram; testes explicitamente não executados nessa fase |
| candidate-package-01/02 | Builder offline e schema oficial do SBOM passaram; candidatos, sem reprodutibilidade final comprovada |
| candidate-smoke-positive-01 | Falhou em inspect; decoder rejeitou stderr, bytes originais não retidos nessa tentativa, nenhum Java/JDBC iniciado |
| candidate-smoke-positive-02 | Falhou em inspect; bytes originais retidos. Resolução de java.exe juntava caminhos de Java17/25 e stderr usava encoding diferente |
| candidate-smoke-positive-03 | Seis comandos passaram pelo pacote: inspect/plan/run/status/resume/compare;19saídas/35escopos, duas raízes e rollback confirmado |
| candidate-barrier-before-sql-01 | Seis comandos passaram; dado CANCELLED, nenhuma conexão SQL do filho |
| candidate-barrier-during-capture-01 | Seis comandos passaram; cancelamento durante captura e dado CANCELLED, rollback reconciliado |
| candidate-barrier-after-preparation-01 | Seis comandos passaram; dado CANCELLED após preparação, rollback reconciliado |
| candidate-barrier-before-receipt-01 | Seis comandos passaram; filho exit0 sem recibo, dado OUTCOME_UNKNOWN, rollback reconciliado, resume sem duplicar caso |
| control-contracts-directed-01 |11unitários passaram,0skip:4Comparator,3ControlFiles e4Journal; repetições não são novos casos |
| control-cancellation-physical-01 |2IT passaram,0skip,3,739s; teto real JDBC e cancelamento atômico chegando ao driver; rollback confirmado |

QualificationLaboratoryMain/Worker/Supervisor integram os contratos, reserva
antes do subprocesso, nonce, PID/instante de criação, limites, baseline agregada,
recibo atômico e reconciliação. Cada filho reconstrói a fixture em transação
revertida. O journal sobrevive em arquivos; não recupera linhas revertidas nem
prova COMMIT/crash durável. DEFERRED representa adiamento antes de subprocesso.
Exit0 sem recibo não aprova dado nem autoriza repetir caso já reservado.

O launcher seleciona um único Java e emite UTF-8 explícito com leitura limitada
a1MiB e prazo numérico. O supervisor observa e cancela somente filhos próprios.
O teste JDBC limitou a duas chamadas e recusou a terceira antes de preparar SQL,
mantendo rollback/close disponíveis. O cancelamento por arquivo com nonce foi
consumido na barreira real de captura e interrompeu WAITFOR no driver em menos
de cinco segundos. Não foi alegada interrupção de processo alheio ou COMMIT.

No smoke positivo:32páginas,74.525bytes,46registros observados pelos adapters
instrumentados,23lotes, maior lote2, zero em voo,238prepared statements,
43registros técnicos retidos e15,305s. Esses contadores ainda não representam
todos os onze adapters; a instrumentação restante é trabalho deste pedido.
Pontos de heap não constituem platô nem SLO. SQL04 estava vazia no smoke básico;
sua prova positiva dirigida anterior existe, mas ainda deve passar pelo pacote.

Depois do candidato02, o código ganhou verificação exata de dependências/SBOM/
POM/proveniência, recursos de fixture/oráculo dentro do JAR, índice98migrations,
baseline e links de diretórios. Também ganhou inventário fechado do controle,
binding de escopo e exigência de barreira para aprovar o teste de perda de recibo.
Essas revisões compilaram nas provas dirigidas, mas precisam novo pacote e
contraprovas físicas. Não herdam o smoke do candidato anterior.

Nenhum processo próprio ativo ao criar este checkpoint. Todos os resultados
acima estão reconciliados. WORKLOG privado guarda detalhes e pendências técnicas.

Próximas ações, todas no mesmo pedido:

1. Exercitar replay/recomposição/ausência e integrar os quatro faults ao verificador
   com gates dos dependentes e CAP independente; completar variantes e metadata.
2. Completar concorrência física em claims consumidos, retomada adversarial,
   inventário/recibos, agenda e limites; qualificar o pacote com os novos guards.
3. Executar escalas, dois builds reproduzíveis e smokes finais, verify integral
   preservando378IT anteriores, scanners/schema/runtime/continuidade, revisar
   diffs contra o inventário inicial e fechar a sucessão/selos/STATES sem alterar
   provas históricas. Nenhum contador aumenta antes da qualificação integral.
