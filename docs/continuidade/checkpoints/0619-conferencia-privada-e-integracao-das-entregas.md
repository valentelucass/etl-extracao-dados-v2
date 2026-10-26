# 0619 — Retenção privada testada; falha local de preparação de Coletas localizada

Data: 2026-10-26. Etapas 3 e 4 continuam abertas. Nenhuma chamada HTTP,
conexão SQL, DDL, implantação ou mudança de PM2 nesta unidade.

## Entrega material e limite

Root consolidou as entregas dos três chats, integrou auxiliares em cópias
privadas e executou duas ocorrências da suíte preparada de 284 casos existentes,
20 novos casos de retenção e três preflights. As duas ocorrências fecharam com
Original1. Portanto **não há qualificação agregada dos 304 casos**.

Os 20 casos novos produziram PASS nas duas rodadas. Eles verificam getters JDBC
reais antes da comparação, tipos/nulos/precisão, payload ausente versus null,
duplicatas físicas, separação dos 23 binds enviados, limites, saída parcial e
publicação completa condicionada ao fechamento do owner. São testes com fixtures,
sem dataset real do banco e sem confronto externo.

Na segunda rodada, os preflights de Manifestos e Cotações completaram. O de
Coletas parou antes de Source/SQL. Root reproduziu a causa com um auxiliar
diagnóstico finito e com as mesmas classes compiladas e o mesmo JAR153.

## Causa comprovada e correção em andamento

1. Primeira ocorrência: Original1, 104.847 ms, pico 705.458.176 bytes. O produtor
   preservado dos quatro testes de Cotações emitiu
   `BANK_V15_COT_P2_PER100_CAUSAL_MODELS_V1`; o runner V16 exigia a string V16.
   Os quatro casos e todos os outros predicados desse gate passaram. A falha e
   os arquivos ficaram preservados.
2. Sucessor novo: mudou somente o literal esperado para V15; os outros 146
   arquivos ficaram idênticos. Root corrigiu também o metadado do controller
   para apontar ao V16 e criou quatro entradas offline com IDs novos.
   A revisão independente aprovou a nova ocorrência, sem herdar PASS.
3. Segunda ocorrência: Original1, 171.894 ms, pico 694.165.504 bytes. Ultrapassou
   o gate corrigido e chegou ao preflight de Coletas. Manifestos/Cotações
   produziram `PREFLIGHT_COMPLETE`; Coletas produziu `UNCLASSIFIED_STOP`.
4. Diagnóstico real: Original0, 7.032 ms, pico 635.580.416 bytes, diagnóstico
   apenas. O stack observado apontou
   `DataExportRequestProfile.semanticsFingerprint:125` chamado por
   `PhysicalContextCandidate.read:83`. O getter exige perfil observado;
   Coletas usa legitimamente `LEGACY`, sem esse fingerprint. O literal
   `OBSERVED_REQUEST_PROFILE_REQUIRED` foi inferido da fonte exata do JAR,
   sem guardar a mensagem bruta da exceção.
5. A inspeção encontrou o mesmo getter inválido no preflight de
   `PhysicalCompositionCandidate:150`; esse segundo ponto ainda não foi
   alcançado em execução. A correção mínima foi delegada ao chat de contratos:
   uma derivação auxiliar explícita da semântica real do pedido de Coletas,
   usada nos dois pontos, com causais próprios. Produto, getter e wire da
   requisição permanecem preservados. Implementação/revisão/nova suíte pendentes.

Não transformar Original0 do diagnóstico em teste PASS, não reclassificar as
duas ocorrências Original1 e não reconstruir valores históricos de MFT/COT.

## Entregas integradas e revisadas

- Retenção privada: três overlays, patch/inverso e receita revisados; duas
  cópias Root com 147 arquivos. Primeira com 1.292 pins externos; sucessor
  causal com 1.300, preservando os anteriores. Nenhum valor de negócio foi
  incluído em logs, manifesto público ou Git.
- Binding final: P1 de autoria do Java Direct7 foi corrigido no `bd60`.
  O mesmo objeto Popen fornece PID/fechamento, cruzados com PID/pai Java,
  nonce, unidade, resultado e origem. Os 21 bytes finais foram integrados
  diretamente pelo Root, sem aplicar o diff documental com header deslocado.
  Revisão do código e do envelope aprovada; nove causais técnicos e
  Direct7/Bind/111/4/Main7 ainda não executados. Nenhum binding final emitido.
- Banco: proposta privada contém V106 Users, V107 P3, baseline e três
  validadores: seis caminhos, oito corpos preservados, quatro tabelas/quatro
  procedures, 57 colunas, 33 parâmetros e 17 constraints. Revisão permite
  preparar/integrar fontes no escopo próprio; não autoriza DDL. Guard P3,
  caller, dependências/colisões, histórico e provas de engine/rollback pendentes.
  Users pode confirmar antes da recusa P3; atomicidade conjunta não está provada.
- Ordem de integração: os seis caminhos não intersectam os 68 da adoção g1,
  mas baseline e `SchemaFoundationSqlContractTest.java` pertencem aos 1.710
  caminhos hoje iguais. Adotar os seis antes invalidaria essa igualdade.
  Fechar sujeitos/adoção68 primeiro; integrar migrations como mudança própria,
  com qualificação própria, sem transferir 624/Clock9 a essa mudança.

## Processos e produto preservados

Root observou as cinco tuplas anteriores mais duas tuplas externas: o perfil
literal de cinco não estava elegível naquele momento. PID18712 foi correlacionado
ao arquivo `ETL-EXTRACAO-DADOS-LOOP.pid` do PM2; o comando não identificou os
caminhos V1/V2, portanto não se atribui uma versão. Nenhum processo foi parado,
nenhum novo perfil de sete foi admitido e nenhum rebuild foi disparado.

JAR153/g1, Own200, Final8, Full624 e Clock9 permanecem preservados. Adoção68
continua condicionada aos bindings finais reais. A última Source do Root segue
INFOColetas0607/delta1. ESL permanece somente consulta; nenhuma ação no site.

## Evidências privadas principais

Base `B`: `C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01`.

| Artefato em B | SHA256 |
| --- | --- |
| RootColetasPrivateReadbackControllerFailure0619.json | 75F9B2DE39CB0EF21D2605F499755046A68393D3CB1C7F7FD5013BF82531C4DE |
| RootColetasPreflightFailure0619b.json | 1668BB8D912FD0AAEE4C94CEE93E87C3AFBF956CC3483B979B70F96AC6B6AEC7 |
| RootColetasPreflightTrace0619.json | 7195006C219894A2D3A0F7A84562D4ECE754ED3BD8DA5CBC3216F8D85B9E9DF1 |
| RootR55BindingManifestIntegration0619.json | 7932D9E815B37528AF54A30DC729EB349652259999E998F9DB5C944E425CDDD5 |
| r55-bd60-envelope-review-files-only-0619/review.json | F4395E226CF7813632E9923D3D67151858E564A2C15C1A0ADF8442178806D17C |
| names8-migration-baseline-review-files-only-0619/review.json | 3C6A5D54B36DF15FC7F9E48635B218C92A63D8E2D7B24598FE4E552E1804E394 |
| RootNativeEligibilityObservation0619.json | D7A4ECCFFB54D5862670E705DA8AFDD41DA1AEABCAB53DDF49501A634864B798 |
| RootExternalETLLoopObservation0619.json | EEE5E8642B1B1279FE833EA51320B54AE0D60E85107A3DA0543E16E09EC50000 |

## Próximas ações e aceites que continuam abertos

1. Corrigir as duas chamadas de semântica de Coletas no auxiliar, revisar e
   executar nova suíte finita com três preflights completos e Original0.
   Só então avançar a captura Source→staging→readback→rollback com retenção
   privada real, mediante os gates físicos existentes.
2. Executar a sequência de bindings somente após reconciliação pessoal do
   perfil nativo elegível; manter PM2 intacto. Depois aplicar adoção68 exata.
   Catálogos Fretes/Local e os pré-requisitos das migrations continuam abertos.
3. Concluir as entidades e Users até EOF; comparar valores reais do banco com
   ESL/Excel por registro/campo na mesma entidade, chave, granularidade,
   campo de data, janela, filtros e fuso. Os Excel são verdade nos intervalos
   indicados; isso não identifica automaticamente a entidade nem prova paridade.

Não há carga completa de todas as entidades, commit de dados retido, comparação
externa concluída ou encerramento das quatro etapas.
