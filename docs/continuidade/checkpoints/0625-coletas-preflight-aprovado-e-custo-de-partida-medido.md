# 0625 — Coletas: preflight aprovado e custo de partida medido

Continuação noturna de 26/10, com registros UTC de 27/10/2026. A extração
e a comparação por registro/campo ainda não estão concluídas.

## Verificação completa de arquivos aprovada

Root integrou a segunda correção de caminhos de Coletas, revisada de forma
independente: template8EDA51F1, 42 arquivos e 154 dependências. Core2843,
JAR153 e os 308 casos anteriores ficaram intactos. Novo Own3 passou com
Original0/47.160ms/518.352.896bytes. O preflight completo passou com
Original0/29.916ms/131.137.536bytes: 147/1.334 pins, 308 casos anteriores,
três próprios, 26 descritores relativos, transitive validators e latest0607.

Provas em B (`quatro-etapas-20261024-01`):

- RootColetasV6ProductProofIntegration0624.json: 82ED235115978C55B34430466619A553273065504E75B7CE44606EFD1DBF82FD.
- RootColetasV6ProductProofOwn3Qualification0624.json: 5F1E3C20B31F64F98A48FCC23FE0AB032A07BDF73E066991126C07820977A6E2.
- RootColetasV6ProductProofFilePreflightQualification0624.json: 407FF76FAAAE47373D88F5744D7915BE18F83C02AB94A4590A93B2E4DF3EBE81.

## Tentativa física parou antes de receber dados

O emissor foi revisado, com 19 campos STOP, 15 chaves de projeção, construtor
de 16 argumentos e destino somente no CODE privado. Root escolheu uma única
unidade, ocorrência e janela literal de 360s. Compilação Original0/1.867ms,
164.425.728bytes; JAR somente classe Root e bootstrap, sem manifesto extra.

`root-coletas-bank-0625-01` fechou Original1/34.201ms/134.393.856bytes,
sem timeout. O prelúdio levou 30.625,16ms antes de entrar na função física;
o relógio acumulava 33.306ms no encerramento. A segunda verificação de entrada
recusou ROOT_NEW_ORIGINAL_BUDGET_WINDOW_REQUIRED_NO_REBASE antes de iniciar
o processo físico de banco. Original registrou dois processos totais; não
confundir esse total com uma alegação de que nenhum auxiliar Java existiu.

HTTP reservado/concluído, leitura de token e leitura de credencial SQL foram
zero. READY=false, diretório child vazio, nenhuma captura privada ou fase SQL.
O recibo do controller mantém childClosedKnown=false; o Original encerrou
seu Job conhecido. Não há TRIAL_COMPLETE ou qualificação física positiva.
Prova RootColetasNoSourceStartupFailure0625.json:
CE4F0F81FEAE98F4D85418B426471EAF1F9E9643646DCE169BF81FC5E6741925.

O driver registrou `source0625AdditionalConfirmedAdapterReservations=0`.
Esse fato foi preservado; precisa de revisão explícita no próximo escopo,
sem apagar histórico, renovar janela ou admitir consumo positivo. A última
Source real permanece INFOColetas0607. Não houve retry desta unidade.

## Gargalo medido sem Source ou banco

Um diagnóstico local nos mesmos arquivos fechou Original0/38.769ms e
79.011.840bytes, sem CODE, janela, provider, HTTP ou SQL. Não é novo preflight
qualificado. Medições em coletas-file-check-timing-root-0625-01/timing.json:

- Abrir o grafo de leases: 16.004ms.
- Validar o Bound completo: 12.301ms.
- Repetir PublicPath e hash nos 1.334 externos: 9.156ms.
- Conferir os mesmos handles vivos e hashes já retidos: 37ms.
- Repetir o parser UTC 2.668 vezes: 60ms.

A menor otimização está em revisão: evitar a segunda checagem de caminho
quando a leitura já possui lease vivo neste mesmo processo, mantendo a
validação da primeira abertura e todos os hashes. Não aumentar 30/340/360,
redefinir originalStartedAt, retirar pins ou presumir tempo futuro aprovado.

## Pacote: causa atual do resultado ausente identificada

bd62 Prepare passou Original0/9.151ms. Run fechou Original2/68.198ms e
649.711.616bytes, sem resultado JUnit. O diagnóstico reteve CIM_MUTEX_TIMEOUT
na aquisição de 100ms. Esse latch interrompeu o Java próprio antes do resultado;
o deadline de 180s não expirou. Isso não explica retroativamente a falha
distinta de seis aprovados/um reprovado de bd60, nem prova qual operação
manteve a trava ocupada.

A correção proposta move somente a escrita privada sample.pending para antes
da aquisição. Publicação atômica continua protegida, leitor e limites intactos.
Root revisou a única linha movida. Novo produtorFEDFA, variante D65F764D,
passou novos 42 modelos, quatro causais nativos e Capture6, todos Original0.
RootR58CaptureQualification0625.json:
8CBD93A98E93180088333CE4F6C080B5CA7FA4CDC16DD3359F6D39745B26269A.

SFTP28496 terminou naturalmente. Root selecionou pessoalmente a nova ocorrência
41252/parent7856/2026-10-27T02:44:42.2142150Z, cinco antigas intactas e
ausências aposentadas conferidas. Nenhum processo PM2 foi alterado. O novo
contexto e os bindings ainda estão em preparo. JAR153/200/8/624/Clock9 ficam
preservados, sem rebuild ou transferência de recibos entre coortes.

## Conferência externa e demais pendências

Os metadados dos dois Excel foram lidos sem editar os originais: produtor
Microsoft Excel, sem propriedades customizadas, conexões, links externos ou
identificação da origem/filtros. Isso não resolve a correspondência com Raster
ou Manifestos. O comparador privado foi preparado, ainda não executado; ele
separa valores JDBC reais de evidência de setters e deixa observedAt não
selecionado explicitamente sem conferência.

Fretes25/Local21/Names11 continuam positivos somente para metadados, total57
em três unidades independentes. Adoção68, propostas de schema sem DDL,
UsersEOF e fidelidade ESL/Excel seguem abertos. Chrome está sem conexão de
controle; aba autenticada preservada. Root único executor e commit/push;
ESL somente consulta, sem alteração em produção, SQL Server, contas ou PM2.
