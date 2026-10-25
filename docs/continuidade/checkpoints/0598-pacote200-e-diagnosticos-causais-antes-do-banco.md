# Checkpoint 0598 — Pacote200 e diagnósticos causais antes do banco

25/10/2026. Base publicada: 7e353f87c87db3b04e394e271ae5dceed40c0250.
Etapas 3 e 4 continuam abertas. O pacote mínimo passou testes locais; a gravação
real, a integração ampla e a comparação com a ESL ainda não estão concluídas.

## Novo pacote mínimo Manifestos e Cotações

Uma única construção nova compilou 756 fontes principais e 681 de teste. O mesmo
JAR passou 200 execuções: 150 de regressão, 12 de Manifestos P2, 14 do perfil DATA,
17 de Cotações P2 e os sete originais de integridade. Nenhum relatório ou PASS
anterior foi copiado. Os corpos dos 150 e dos sete permaneceram inalterados.

O original 1 GiB/8 min fechou 0 em 372.807 ms, pico 714.764.288 B, sem timeout.
O verificador externo concluiu a qualificação e o Root conferiu os recibos,
relatórios, seis autorias sintéticas, hashes do JAR e o delta do pacote.
Prova privada: RootCombinedP2Own200Qualification0598.json.

Selo: 70D5795473A58BD3ED4EBE0E309D8CAEE8CA89136473B4A7B6BC8FF16BF168A3.
JAR: F2A78CBD884C828ECE78F66B2064DCEB155634B74F8DF599E2D343699EEE2D35.
O delta conserva recursos anteriores e oito bibliotecas; altera somente as duas
classes previstas e acrescenta o recurso do contrato Cotações P2. Não houve
adoção canônica, implantação, HTTP, provider ou SQL nessa qualificação.

## Wrapper do banco e recusa real antes do acesso

O wrapper V2 causal passou 15 modelos Java e 45 PowerShell, incluindo a leitura
do principal P2 real e seus sete bindings. A falha do V1 fica preservada; V2
corrigiu a expectativa do modelo de derivação duplicada do runtime, sem relaxar
o predicado do produto. Prova: RootBankWrapperOwn60V2Qualification0597.json.

O Root emitiu uma unidade física nova, root-manifestos-bank-0597-01. O original
fechou 1 antes de READY, sem reserva ou conclusão HTTP, sem leitura de token ou
credencial SQL e sem escrita. A identidade nativa permaneceu igual. O diagnóstico
interno foi sanitizado e não reteve uma causa tipada específica; não inventar uma.

A margem da janela no início real do original foi 333,8179394 segundos, inferior
aos 340 exigidos. Esse predicado necessariamente recusa. As verificações lentas
anteriores à ativação e a repetição de leitura/verificação são a fronteira causal
do sucessor, mantendo os limites de 30/330/340/360 segundos e 1 GiB/6 min.
Prova: RootManifestosPreReadyTemporalStopReview0598.json.

ROOT_BANK_V13_SOURCE_STOP permanece preservado. Não renovar ou repetir a unidade
antiga, apagar STOP, trocar seu caminho ou emitir CODE por delegado. O sucessor
precisa de próprios novos, revisão independente, leases transitivos, resolução
Root vinculada ao spec/novo CODE/unidade/ocorrência e validação dos recibos reais
da falha. A resolução não pode se apoiar apenas em booleanos declarados.

O ensaio continua sendo Source → staging → leitura integral independente →
rollback → ausência, sem DDL ou commit de negócio retido. O V13b mantém seus
271 próprios; nenhum teste local é apresentado como escrita real.

## Cinco contratos INFO: falhas e causa do relatório

O V2 de 300 modelos falhou na compilação por atribuição de variável final.
O V3 corrigiu somente essa causa e compilou, mas não terminou o relatório final:
há 445 arquivos sintéticos e três marcadores CLOSED. A ausência do relatório
não prova zero modelos executados nem autoriza contar 300 aprovados.

O diagnóstico V4 reproduziu o preparo original de Coletas6908 sem rodar os
modelos. Origens das classes, inicialização, subject e contexto passaram; a
serialização exata de catalogMetadata falhou com InvalidDefinitionException
em UnsupportedTypeSerializer. Somente tipos, classes, métodos e linhas foram
retidos, sem mensagens, nomes de arquivo ou valores. Original 1 GiB/4 min fechou
0 em 5.831 ms, pico 287.404.032 B. Exit 0 confirma a medição, não a qualificação.
Prova: RootInfoTypedDiagnosticOriginalClosure0598.json. Sucessor V5 em preparo.

## Produto amplo: duas causas distintas medidas no guard

O diagnóstico numérico confirmou que o lançador Python da venv colocava o worker
num Job interno sem limites de memória, embora o Job original continuasse em
1 GiB. A contraprova com o mesmo worker e Python base pinado leu corretamente
os dois limites de 1 GiB. Não alterar o executor original ou relaxar o guard.

Com Python base, a captura passou a recusar identidade externa indisponível.
R12f mediu OpenProcess: quatro JVMs antigas retornaram erro Win32 5; uma passou
todas as etapas; uma seleção antiga ausente retornou 87. Original fechou 0,
sem alterar processos ou privilégios. Isso não é PASS dos sete testes do produto.

RootCurrentExternalJvmCimSelection0598.json registra explicitamente as seis
identidades atuais, a ausência da seleção antiga e a nova instância do worker
PM2 já observada antes da limpeza. O perfil CIM proposto é distinto do perfil
nativo FILETIME/imagem/hash. Não usar fallback ou exceção por nome. Exige captura
fresca protegida, heartbeat, identidades exatas, barreiras, negativos próprios
e recusa de novas JVMs, reuso, desaparecimento ou informação incompleta.

## Chrome, continuidade e restrições

O usuário autorizou Chrome, e o Root confirmou a aba ESL autenticada no TMS.
O acesso continua exclusivamente de consulta, filtros e downloads. A comparação
independente aguarda banco comprovado e o mesmo template/período/data/filtros/fuso.
Fretes Data Export6389 não é GraphQL; Localizador8656 não é Raster; Faturas4924
é outra entidade. Nenhum Excel ou comparação foi aceito nesta continuação.

A limpeza de processos foi autorizada separadamente pelo usuário. Foram fechadas
sobras de testes e Teams, preservando as identidades dos processos PM2, SQL e
Chrome da ESL. O ajuste de início automático do Edge foi recusado pelo Windows;
o arquivo administrativo está preparado fora do Git, sem alegar aplicação.

A última Source continua Cotações0595-01: controller-receipt.json e
original-memory.json. Nenhuma chamada nova foi feita desde essa unidade;
os contadores globais continuam parciais. Usuários conserva 54.540 observados,
seis quarentenas e hasNext=true, sem EOF, cursor ou replay automático.

Root é único executor pesado/API/SQL e editor canônico. Os chats preparam e
revisam arquivos. Mantidas as restrições de SQL16.0.1000.6, versões, identidades,
credenciais, ACL, serviços, listeners, certificados, trust global, V1 e produção.
As autorizações específicas de limpeza não autorizam outras mudanças em WMS/PM2.
Sem DDL novo, deploy ou cutover. Commit/push obrigatórios; dados reais, pacotes,
evidências privadas e preparações permanecem fora do Git.
