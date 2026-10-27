# 0624 — Catálogos de Fretes e Localizador concluídos; Coletas em validação

Continuação noturna de 26/10/2026, com registros UTC de 27/10. As etapas de
gravação e conferência dos dados continuam abertas. O usuário deixou o ETL
pausado e pediu avançar até concluir, antes da comparação com Excel e ESL.

## Consultas de estrutura concluídas

A consulta de permissões retornava zero linhas; o wrapper escalar passou a
retornar SQL NULL. A correção final converte exclusivamente esse NULL em
array JSON vazio na própria consulta de permissões. Os leitores genéricos,
53 casos anteriores, limites e consultas restantes foram preservados.

O sucessor 1FE88416 passou os 53 testes locais (22 classes), compilação
estrita e Original0, em 10.493ms, com pico de 503.111.680bytes.
Fretes concluiu 25 consultas reais, 23 documentos, Original0/16.727ms e
534.056.960bytes. Localizador concluiu 21 consultas reais, 19 documentos,
Original0/16.646ms e 539.136.000bytes. Ambas as conexões fecharam; peer antes
do login, encerramentos nativos e ausência do socket próprio comprovados.

Com as 11 observações Names8 anteriores, são 57 SELECTs de metadados em
três unidades independentes. Isso não é um snapshot atômico para DDL.
Não houve leitura de registros de negócio, DML, DDL ou HTTP nestas unidades.
A análise do conteúdo pelo Banco está em andamento. Estes resultados não
comprovam extração, gravação de Fretes/Localizador ou fidelidade externa.

Provas em B (`quatro-etapas-20261024-01`):

- RootFretesPermissionsNullOwn53Qualification0624.json: F51C49D0AB1CE7890D3BDF6B83DE842EEA8C57A9C6A7979E5E85DC80B530784D.
- RootFretesPermissionsNullMetadata25Qualification0624.json: 522B82A553B954A37D37B7ED52F2988E0092345F86D26064BB6632B8512D1D7C.
- RootLocalPermissionsNullMetadata21Qualification0624.json: 0CD81DEC577CF39D5ED2B5300B5B95042EA38EBF9D78D894B039D510CFB5B656.

## Coletas: segundo caller de caminhos identificado

A primeira correção de caminhos foi integrada no template E7BBDD89 e os
três testes próprios passaram novamente, sem repetir os 308 já aprovados.
O segundo preflight fechou Original1/27.727ms, ainda sem Source ou SQL.

Um trace limitado da execução real confirmou a sequência Temporal:24 →
Physical.Common:61 → Assert-RootPin → LatestSource:206. Esse segundo caller
relê os dois productQualificationEvidence do Core2843 usando C:/, enquanto
o registro de leases exige C:\. Os alvos e hashes são os mesmos. A proposta
33B33E06 altera apenas LatestSource, normalizando cópias desses dois pins
depois de verificar o Core. Os guards genéricos e os pins de properties e
request ficam intactos. Revisão independente e integração ainda pendentes.

O trace também confirma 26 descritores relativos de classes, legítimos e
resolvidos pelo protocolo existente. Oito era um número documental antigo.
Não há captura privada de valores JDBC real, CODE operacional ou nova janela
emitida. O destino privado continua reservado e precisa estar vazio no uso.

## Pacote e processos preservados

bd61 Prepare passou Original0. Run fechou Original2/111.446ms e
715.812.864bytes, sem arquivo de resultado JUnit ou testemunho Java/Popen.
Não é correto classificá-lo como sete falhas ou atribuir causa ao CIM.
O sucessor bd62 B659577C integra diagnóstico limitado do worker; sete
testes, nove causais e demais gates permanecem. Nenhum binding foi emitido.

O SFTP anterior terminou naturalmente. Root selecionou pessoalmente as
cinco identidades antigas mais a nova ocorrência PM2 SFTP28496. Seleção V3
47242E38 inclui a ausência observada dos PIDs aposentados, sem alegar prova
de imagem por seleção. A variante R58 A98815E3 passou novos 42 modelos,
quatro causais nativos e Capture6, todos Original0. Prova de captura:
RootR58CaptureQualification0624.json,
50A31E315A7F919D098BA7DD2BEAD037B306303447FF31C7B38857C65AF6A2D4.
Nenhum processo PM2 foi interrompido. A adaptação fechada dos auxiliares
bd62 ao native atual está em preparo, preservando os recibos históricos
native5 de 624/Clock9. Não repetir 200/624/9 nem reconstruir JAR153.

## O que falta

Concluir preflight e captura Coletas com retenção dos valores efetivamente
lidos, fechar bindings do pacote, adoção68 e avaliar as propostas de schema
sem executar DDL proibido. Users continua sem EOF. Não há comparação por
registro/campo concluída com Excel ou ESL, nem extração integral de todas
as entidades. A semântica dos Excel ainda exige identificação da origem.

Chrome permanece autenticado na aba preservada, mas a conexão de controle
está indisponível; nenhum novo filtro ou download foi confirmado. ESL é
exclusivamente consulta. Root é o único executor pesado/API/SQL/browser/Git;
PM2, versão SQL, contas, permissões e produção preservados. Commit e push
obrigatórios para cada fechamento revisado.
