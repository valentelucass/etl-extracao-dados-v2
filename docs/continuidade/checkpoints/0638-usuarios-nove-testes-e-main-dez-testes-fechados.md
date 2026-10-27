# 0638 — Nove testes de Usuários e dez da entrada principal passaram

Em 27/10/2026, Root executou duas qualificações locais novas, serialmente e com
limite de 1 GiB. Nenhuma dessas execuções fez consulta externa ou conexão SQL.
O avanço do checkpoint0637 está publicado em 41ea5a3c7c6fa64a9c3e6b1f960f7f5f8e4a910b,
com sete arquivos, push confirmado, árvore limpa e blobs raw MFT/COT conferidos.
Prova da publicação: E271C9D0BE2D1189779159C1254654EF18E6613815632DD6D15E13DCC2AD52D0.

## Resultados executados

| Escopo | Resultado real | Tempo / pico do grupo protegido |
|---|---|---|
| Composição auxiliar de Usuários first20 | 9 métodos JUnit, zero falhas/skips/aborts; compilação estrita Java17; 34 classes; Original0 | 11.024 ms / 508.014.592 bytes |
| Entrada principal → API V6 retornável | 10 casos JUnit, zero falhas/skips/aborts; três compilações estritas e cinco filhos terminados0; Original0 | 16.307 ms / 367.017.984 bytes |

Usuários: manifesto 587BDDE015758D4AEAB0E24A5FEC676155C7DEA8BFCA77252B41111A379CE92F,
receita 0AC600DF9D002C11D6BBFFF04C409D17368E0D7641C208EC6B31DA0841CD2BD8,
revisão independente 5B881BBF0C5DD74DFA0A0D37150C75463ECDB70C3090DEACC86DC6C5383F6A24.
Execução privada uw38-06380002; qualificação
B579D2A1CDF36EE7823EE17599811269026DD1F94C5906AD864A24992D3BF0E9;
Original 9A57D19C585916E1E0A3AC8B304B8C531084933919D87B66740B63D6B28457F6.

A composição usa factory/executor/hook/profile/sidecar/parser/mapper genuínos,
com transporte sintético sem socket. Mantém um único ciclo e limites totais;
20 é o tamanho máximo por página, não o limite total de Usuários. Os campos id/name
selecionados preservam presença e lexemas suportados quando o parser ou mapper os
recusam. Corpos inválidos ou fora dos limites do perfil não recebem promessa de
preservação integral. Não houve adoção dessa composição no produto canônico,
gravação durável, leitura real nova ou prova de fim do cadastro.

A revisão encontrou e corrigiu dois erros antes da execução: página terminal
vazia era recusada; a cobertura podia ser positiva sem preservar uma resposta
recusada pelo perfil. Agora zero+hasNext=false é terminal local validado;
zero+hasNext=true para separadamente. Cobertura exige resposta entregue ao hook
e correspondência de todas as páginas selecionadas. A versão anterior ficou
preservada e não foi executada. Os nove casos incluem essas contraprovas,
quarentena, parserSTOP, limites, 429 sem retry, prazo, cleanup e cursor repetido.

## Entrada principal e limite da prova

O comando candidato entity-rollback seleciona somente COLETAS6908, MFT6399 e
COT6906. A Main já possui caminhos operacionais dessas entidades; a limitação
histórica às quatro P3 diz respeito à porta observed-staging, que foi preservada.
O novo caminho usa uma API fixa do V6. As entradas auxiliares agora têm retornos
internos; System.exit permanece apenas nas bordas CLI antigas. Três casos chamam
Main.run de verdade até a recusa por CODE vazio; o caso de retorno físico é
isolado e fechado. API invocada diretamente não ganha prova de origem Main.

Manifesto E1D288D63828634292C667AA5C5CE5DDB330B9980BD673229D29ADC006923BF2;
revisão D5BD2F76E1B9F01503864AF02CBF497EE086E5D49D45B9170312A952847B7D6C,
suplemento do caller C5684C9FAD2307422CF2BD99CD698AA3A2FC949B01FF01EF648929FF01898C67.
Qualificação me38-06380002:
C9838AD90F61BBA2B02F1F2A21312603AA72EE356036D8695B1FDD90199392F1.
O JAR derivado C4A01B51930FE2172675E0936E705A44A9C56030542B3222A73A4997EDC3312D
tem prova fechada local, não admissão física. JAR153 e Core40E históricos ficaram
intactos. O Core ainda fixa153/NO_MAIN; exige sucessor explícito, origem Main real,
pins finais e novas qualificações antes de qualquer chamada ou gravação por essa
porta. Não transferir os testes200/624, CODE, janelas ou capturas anteriores.

A primeira ocorrência Main fechou Original1 antes do JUnit: o JAR copiado apontava
para oito bibliotecas relativas ausentes, recusadas por -Werror. A correção copia
somente as oito bibliotecas pinadas para lib, verifica tamanho/SHA antes/depois e
mantém os dez testes e as cinco operações. O segundo grupo passou. Uma recusa
anterior do caller por C:/ versus C:\ foi corrigida por GetFullPath nos dois lados,
preservando o hash exato; ocorreu antes de slot, diretório de execução ou Original.

## Banco: Raster é um quinto grupo separado

Os 18 SQL/quatro grupos de Fretes, Localização, campos originais de Usuários e P3
continuam propostas sem execução. Raster não pertence a esse inventário: tem
sete DDL materiais para cinco alterações, em proposta separada: latitude e
longitude de decimal(28,8) para (36,16), dois CHECKs e um gatilho de controle.
Mapa DF1A9F0F7186AD6A0FC507E08A8DB1CDA04A24D414828E26D0DE98D0CEEEDC99;
materialidade A5C273E4112216A7489E00FEDE5A655FC8640DAA3338F4AD3F74977F9C8BDF85.
Isso não constitui SQL sintaticamente qualificado no engine ou autorização.

Foi pedida exceção específica também para Raster; nenhuma resposta foi recebida.
A pergunta anterior dos quatro grupos não a cobre. AGENTS mantém a proibição
permanente de DDL/grants/Flyway. Aprovação futura não substitui before protegido,
vínculos, owners, admissão, rollback/readback e fechamento reais. A sessão ESL
continua aguardando nova entrada para confronto somente de consulta.

## Continuação efetiva

1. Finalizar estilo das duas fontes Main em cópia isolada, qualificar os bytes
   resultantes e preparar o sucessor físico fechado ao JAR final e à origem Main.
2. Implementar publicação privada de Usuários por página antes de avançar, com
   prefixo salvo separado de Source terminal. A composição atual retém em RAM até
   retornar; 64 MiB também não garantem cem mil linhas. Qualificar o novo caminho
   antes de decidir uma campanha nova, finita, first20; não retomar o V3 perdido.
3. Após respostas concretas, tratar os ajustes de banco autorizados e confrontar
   os mesmos recortes no site. Manifestos315/Cotações287/Coletas1 já têm provas
   privadas de fonte→banco→readback sem diferenças e rollback; Raster108/355 tem
   códigos/placas e previsões das paradas conferidos com Excel até o minuto.

Etapas3/4, carga permanente e fidelidade externa integral continuam abertas.
SQL16.0.1000.6, contas, permissões, PM2, V1 e produção foram preservados.
