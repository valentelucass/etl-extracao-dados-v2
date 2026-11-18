# 0686 — Fretes: recuperação corrigida e integração local aprovada

Coletas permanece com três semanas integralmente aprovadas. Fretes permanece com zero de três semanas aprovadas: não houve nova consulta ESL nesta unidade. V1 permanece pausado.

O candidato congelado `D1E5A4E2` endereçou os quatro achados da revisão independente: contador lógico do parcial, distinção entre reserva/intenção/invocação/EOF/RAW, parada irreversível e fatos de repetição no host. A revisão no chat `01a1a869-7326-7750-b9d0-4bcf7d897578` não encontrou novo bloqueador material nos caminhos examinados. A revisão estática não substitui a qualificação do host completo.

O Root executou nova qualificação sobre as 16 fontes atuais: três compilações e 16 suítes executáveis passaram, incluindo as 15 anteriores e a integração de recuperação com porta/executor reais e cliente HTTP falso. Foram exercitadas recuperação 422→200, respostas de erro com bytes distintos, invocação confirmada versus incerta, ausência de cabeçalhos/EOF, prefixo retido, última intenção sem reserva, falha de projeção e recusa das operações após STOP. Original 16656 terminou em zero, 77.038 ms, pico 376.651.776 bytes; todos os processos próprios, stdin e pipes fecharam. O pai confirmou restauração de estado e ausência de vaga retida.

O produto resultante tem 3.517.706 bytes, SHA256 `66CA26BA835E27AA9F86D895A6AB39DF01004F55928DA0E685A65EDB8B7BD54F`, com 38 classes em 16 famílias; 1.483 entradas anteriores, 30 classes Coletas e manifesto foram preservados. Os testes não entraram no JAR. Evidência Root: `outputs/root-fretes-timeout422-java-qualification0686-01.json`, 3.526 bytes, SHA256 `14D9B2EEF64D618FABAE65A19E1702744053AAE596ACA07B81DD0113D961FB3C`, no diretório do supervisor. Esse aceite é da camada Java offline e não libera Source.

O adaptador do novo manifesto completo passou 13 casos causais e 24 regressões do comparador. Verificou a conservação dos 114 valores, campos extras da API, multiplicidades, lexemas numéricos/totais, associação dos locators ao RAW 200 aceito e contabilização dos erros retidos. Original 49328 terminou em zero, 3.080 ms, pico 81.383.424 bytes; Python e pipes fecharam, estado restaurado. Resultado em `work/fretes-retry-manifest0686-run01/diagnosis.json`. Nenhum arquivo de negócio real foi comparado nessa suíte sintética; nenhuma parcial foi admitida como completa.

O navegador interno foi reconectado à aba existente do relatório 6389, autenticada, com filtro de serviço 19/10/2026 a 19/10/2026. A própria tela informa 54 ligações, máximo recomendado de 30 e possível prejuízo à exportação. É uma pista de peso do relatório, não prova da causa interna dos timeouts intermitentes. Não alteramos o template nem retiramos campos para obter sucesso.

Canônico preservado: B369E2D1/713.677 bytes, ACL B4E214EB, Root0658, vaga vazia, 17 pares completos e cinco parciais fechados. Source/HTTP/SQL zero nesta unidade. Cinco diagnósticos 0684 continuam isolados da contagem de captura Main. Falhas anteriores e prova C936 continuam históricas.

Próximas ações:

1. Executar regressões do host e cadeia parcial V3 com fatos incertos/assinaturas/provas, corrigindo somente falhas demonstradas.
2. Derivar vínculo das 38 classes atuais/fingerprint e adotar host/produto; observar Native atual e emitir admissão finita própria.
3. Extrair um dia completo, baixar Excel próximo e comparar o conteúdo das 114 colunas; concluir os sete dias de cada uma das três semanas antes de aprová-las.
