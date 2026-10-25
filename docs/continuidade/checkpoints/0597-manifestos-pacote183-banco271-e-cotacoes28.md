# Checkpoint 0597 — Pacote Manifestos183, banco271 e Cotações28

25/10/2026. Base publicada: eee246d92d4666836f0778a0b56a9943ce945329.
As etapas 3 e 4 continuam abertas. Nenhuma execução desta unidade acessou
Source ou SQL. Testes locais não comprovam gravação ou extração integral.

## Pacote mínimo de Manifestos aprovado

O causal V3, selo B00430E6A2DA8A9B25AC1B1D3D9F5F4F6D600DC4931C9EA38558E22F70225457,
passou os mesmos sete testes de integridade. Original 1 GiB/4 min fechou 0 em
105.666 ms, pico 709.369.856 B, sem timeout. Os 5.072 arquivos congelados,
16 classes de verificação e duas do runner permaneceram iguais. Somente seis
relatórios de autoria sintética foram acrescentados, com caminho UUID v4,
schema, runtime, hashes e janela da ocorrência validados.

A composição explícita é 176 testes anteriores do mesmo JAR mais sete próprios
do V3: 183. Os originais V1 e V2 com Exit 1 permanecem preservados. Não houve
herança dos sete do LAB nem declaração retroativa de sucesso das rodadas falhas.
JAR: 654B181E2C12268F30D8C2D35A3ADE4B8CA4B2C284E9B3605EC35560E6F15DDA.
Prova privada: RootMinimalP2Composed183Qualification0597.json.
Isso qualifica o pacote mínimo localmente; não é adoção canônica ou implantação.

## Banco V13b aprovado localmente, ensaio físico ainda pendente

Root encontrou três argumentos enviados ao filho PowerShell sem parâmetros
declarados no V13. V13b remove somente esses argumentos redundantes: os valores
já são obtidos do manifesto. Todos os corpos dos 271 casos foram preservados.
V13 não foi executado nem modificado. Preparação V13b:
9F30CB6AB3A748618EF389E13CE7943216D8818AEB27035025B16CE7E4E4A4F3.

Root criou novos IDs do request Manifestos P2 e vinculou o pacote183 no sucessor
manifestos-cotacoes-cleanup-p2-bound-v13b-root-0597-01, selo
9B7295907B27C8B1C7EE11115506B03E94F188E49A56DF6FC4FFFE8102B67BCA.
Passaram 271 casos próprios, três hops stdin e dois preflights reais LOCAIS,
sem Source, provider ou JDBC. Sete grupos de classes foram selados. Original
1 GiB/4 min fechou 0 em 127.101 ms, pico 701.554.688 B, sem timeout.
Prova privada: RootBankV13bOwn271Qualification0597.json.

Próximo requisito: qualificar o novo wrapper/issuer em sua própria ocorrência,
revisar o principal P2 e os sete bindings, emitir CODE Root novo e executar
Source → staging → leitura integral independente → rollback → ausência.
Não reaproveitar releases, unidades ou orçamentos consumidos. Nenhum DDL,
commit de negócio retido ou liberação física foi emitido por estes testes.

## Cotações e Localizador

Cotações P2 passou 28 casos próprios: 11 no contrato antigo e 17 no proposto,
incluindo serialização DATA, seletor/paginação e contraprovas de filtros sem
tabela ou de tabela errada. Os 22 corpos anteriores permanecem preservados.
A proposta usa os seis filtros quotes.field efetivamente medidos no INFO e
preserva Manifestos P2. Selo:
311E573335260646DE3D806CBC7A2739E24D8E5491F17D99B61FD308EC1FE271.
Original fechou 0; Source/SQL/provider zero. Prova:
RootCotacoesP2Own28Qualification0597.json. Novo pacote integral e banco de
Cotações ainda precisam de provas próprias; esta proposta não foi adotada.

Localizador8656 passou seus 52 casos próprios, com recibo original fechado 0.
Não houve INFO novo desta entidade. Faturas4924 continua entidade distinta.
Fretes Data Export6389 não é Fretes GraphQL; Localizador não representa Raster.

## Produto amplo e causa ainda aberta no guard

R11b mantém 624+9 testes e style aprovados. Auditoria R15 conferiu os catálogos
e corrigiu somente dois hashes antigos em mixed-class-scope.txt, preservando
as classificações UNIT_INCLUDED e os conjuntos de cobertura. CI passou 3/3
e Package passou, cada qual com original fechado 0. Fontes Java, classes,
recursos e testes não mudaram nesta continuação. A falha CI anterior permanece.
Ainda faltam os sete de integridade do produto e as verificações finais no JAR;
os resultados do pacote mínimo não substituem os do produto amplo.

A captura R12b de JVMs externas fechou 2 antes de Java/testes. Diagnóstico R12c
fechou 2 e reteve somente ORIGINAL_ONE_GIB_JOB_LIMITS_REQUIRED. O recibo
original confirma 1 GiB: investigar os metadados nativos/estrutura lida pelo
guard antes de qualquer correção. Não relaxar limites, matar processos alheios
ou admitir JVMs novas. Ambas as ocorrências falhas ficam preservadas.

## ESL somente consulta e continuidade

Ao abrir a consulta de Manifestos, o site redirecionou para login: sessão
expirada. Root solicitou novo login ao usuário e continua trabalho independente.
Não houve download, exportação, upload, mensagem ou alteração no site.
A comparação aguarda sessão válida e dados de banco comprovados, com o mesmo
template, período, campo de data, filtros e fuso.

Última Source permanece Cotações0595-01, controller-receipt.json e
original-memory.json. Contadores globais continuam parciais, sem nova chamada.
Usuários mantém 54.540 observados, seis quarentenas e hasNext=true, sem EOF,
cursor ou replay automático; seus 95 testes locais não encerram a leitura.

Root segue único executor pesado e editor canônico; delegados preparam arquivos.
Restrições SQL16.0.1000.6, instalação, identidades, permissões, credenciais,
ACL, serviços, listeners, certificados, trust global, V1/WMS e produção mantidas.
DDL sem novo consentimento; nenhum deploy/cutover. Commit e push obrigatórios
para cada alteração revisada e validada. Dados reais e artefatos ficam fora do Git.
