# 0610 — Catálogo real de Coletas validado e Raster48 aprovado

Coletas passou a conferência estrutural completa do banco: 37 SELECTs concluídos, 14 tabelas e 14 módulos, incluindo `stg.coleta_record` e `stg.usp_stage_coleta_record`. As regras e o inventário foram finalizados, a conexão JDBC foi fechada e a identidade SQL Server16.0.1000.6 permaneceu igual. Esta prova é de catálogo, não de gravação ou extração completa.

## Coletas: 142 verificações e catálogo real37

A revisão independente do primeiro candidato37 encontrou um bloqueador antes da execução: o Java emitia PASS142 e o controlador ainda exigia PASS140. O candidato original foi preservado e não executado. O sucessor v2 corrige esse literal e três descrições de contagem, com atualização derivada do hash do plano. A inversão recuperou os três arquivos de conteúdo anteriores; os 142 corpos, consultas, regras, limites e pins da última INFO0607 foram preservados.

Manifesto v2 `EAE79FC03C33E332E964222EBEE93B694DA45CFDF8EEADF8236D7018579E112F`, 166 arquivos próprios e702 externos sem divergência. O Root executou142 casos distintos, recompilando41 classes, em Original1GiB/4min: exit0,26.653ms,381.661.184B. Os140 corpos anteriores foram reexecutados com as21 mudanças de expectativa já revisadas e dois grupos causais novos. Zero HTTP, SQL ou providers nessa qualificação. Prova privada `RootColetasCatalog37Own142Qualification0610.json`, SHA300432B985EF01D1D231285EF302928A65959D954817EAD9BFE1AB3CB111AD56.

O Root emitiu uma nova unidade finita exclusivamente de leitura37, depois de revisar a interrupção anterior de Usuários e a última INFO real. A autorização antiga de16 consultas não foi reaproveitada. Nenhum EOF, fechamento gracioso ou recibo original de Usuários foi inventado.

O catálogo real concluiu37/37,14/14, com uma conexão/provider, query10s/data180s/controller220s, Original1GiB/4min. Original0,29.360ms,320.958.464B;519.908 bytes UTF8 recebidos. Tabela Coletas ordinal20:23 colunas,6 CHECKs,3 índices,3 FKs e nenhum trigger/RLS próprio. Procedure ordinal34:23 parâmetros, definição igual à referência revisada e ao fonte normalizado. As duas observações adicionais são reais, não uma promoção do antigo35/13/13. Zero HTTP/DML/DDL/commit explícito, sem captura admitida ou nova autorização de Source.

Prova privada `RootColetasCatalog37ActualQualification0610.json`, SHAB7A0EE97986215FEB1CD00AAF42A2B960B70ABF7D27D6510968049B7998BDFC2. Relatório real `catalog-result.json`, SHA5A3C08898C3CD1C406873595D60E0BBDA6247EEBA473818F49DFDC219943AC5A. Recibo Original772EBB5BA06B435D435408163EB2D0C825E6631DD5EAAE3380F63CC5B44B134A. O Root conferiu os37 arquivos de observação contra o relatório e seus mapas. Não foi afirmada uma observação de fechamento de socket independente que esse coletor não produz.

BoundV16 continua WIP. Pode usar as observações reais37 para os inputs de catálogo MFT/COT/Coletas e para os50 casos do carregador. Esses inputs não dependem do JAR da fonte, mas a qualificação do fluxo completo depende. Preservar owner11, controles até18 parâmetros antes do begin, borrower23 somente no staging, JSON completo31 campos e readback por faixa do relógio do banco. Novo produto, loader/Bound e gravação real de Coletas ainda não passaram.

## Raster: preparação concreta passou48 casos locais

O candidato privado v4 materializou sete statements para as cinco alterações semânticas Raster: duas coordenadas, dois CHECKs e um trigger. Preserva os26 SQLs Fretes/Local e os43 corpos anteriores, salvo cardinalidade do enum2→3; acrescenta cinco grupos causais. Registry permanece vazio e não existe consentimento humano DDL.

Manifesto2B739D772ED0051C5BEA3B441954A1A36FA8E90D5635129259F90FD796DA3463,68 próprios/120 externos conferidos. Revisão independente995567D7FD7050032AF9168B3D16B7AC6BAD7B37DAF61CF636DF2A3C78206097. O Root compilou estritamente27 classes e executou48 casos distintos: Original0/13.044ms/528.867.328B,1GiB/5min. Prova privada `RootRasterV4Own48Qualification0610.json`. Zero conexão SQL, DDL, HTTP ou provider; não significa validação da sintaxe pelo SQL Server.

Próximo passo Raster é o catálogo atual completo de22 consultas, com fechamento revisado. O controlador exige uma entrada privada exata mesmo para CATALOG_READONLY; prepara-se apenas o mecanismo para o Root emitir essa unidade de leitura, sem liberar ENGINE_ROLLBACK/APPLY/RESTORE. A coorte exata que receber a entrada futura deverá reexecutar os48 casos; não transferir o PASS do registry vazio. Só após a prova real e revisão cabe apresentar eventual pedido humano específico para DDL.

## Pacote final e serviços existentes

BuilderR45 preparado e revisado sem bloqueadores estáticos:31 arquivos próprios,84 pins, base15AB mais os quatro inputs aprovados, compilação778/704,1395 classes e55 recursos esperados. Manifesto32589E9A49094FF0F4FC8F65320F37F601860A92B77F5BAAF42B66E139BE764F; revisão507CD376017405D97E2F4048948DE31577B7F9CD9498343CE8532BE3A1398631. Nenhum JAR novo foi criado e nenhum resultado200/624/Clock/integração foi transferido.

Qualificador Final8 preparado e revisado: oito corpos propostos de Coletas byte-idênticos, classpath somente testes+JAR final+oito libs, conferência de CodeSource/hash do JAR/hash da classe. Os quatro controles antigos ficam no F2A. RevisãoC257C243706A2B6C24C4940C7D5E253532BD6792D4695511C77AB801FE589B1F; descritor de coordenaçãoD8433D0A4E96BDF9B82A3856CF33065E5F171831CBCDE1B479E99A4C3CD6577A. Pins do produto final continuam pendentes.

Antes do build, a observação Root encontrou dois Java adicionais aos cinco selecionados; os dois terminaram naturalmente. Um foi correlacionado ao arquivo PM2 `WORK-SFTP-CLIENTES-5.pid`, sem parar serviços. RPC PM2 somente leitura retornou ACCESS_FAILED, sem criar daemon; não alegar lista viva obtida por RPC. A observação posterior voltou exatamente às cinco identidades F7EF; nenhuma políticaR37, guard, Capture ou modelo40 foi alterado. Nenhum processo foi admitido por nome/pai, nenhuma identidade nativa foi inferida e nenhum limite foi relaxado.

A tentativa RootR45 seguinte parou na conferência de caminhos antes de criar claim, output ou recibo Original: REPARSE_PATH_REFUSED. Não houve Original, compilação, Java, testes, HTTP ou SQL. O alias Python `cpython-3.12-windows-x86_64-none` é uma junction. O Root mediu o caminho físico `cpython-3.12.14-windows-x86_64-none/python.exe`, com os mesmos bytes SHA1326CF78AEEF6A027DEE9FC991CDF275B113A7360E6D7DB3C3E196B49FAD1B10. Prova privada `RootFinalR45PreOriginalStop0610.json`, SHA9D5BD2C8DD858C56DEB449010BD197E62DFAE5849F9BEEF113694C1686405BA5. Unidade aposentada, R45 congelado e slot Root fechado. Prepara-se sucessor mínimoR46 que usa o caminho físico e preserva Assert-NoReparse, os limites, os quatro inputs e todos os corpos de teste; nenhum resultado de pacote foi antecipado.

## Pendências preservadas

Cotações287 e Manifestos315 mantêm suas provas anteriores de gravação/leitura/rollback; isso não prova todas as entidades, histórico integral ou operação diária do V2. Última Source continua INFOColetas0607/delta1, contrato antigo recusado e diagnóstico qualificado; contadores globais não finais. Usuários segue semEOF. Chrome aguarda reconexão, sem Excel/paridade. Etapas3/4, pacote final, gravação completa das demais entidades, adoção e deploy continuam abertas.

Root é o único executor. Demais chats e agentes preparam/revisam arquivos. ESL continua exclusivamente para consultas, filtros e downloads, com FretesDataExport6389 separado de GraphQL e Localizador8656 separado de Raster. Sem alteração de servidor, serviços, contas, ACL, certificados, V1/PM2 ou commit de negócio retido. Este fechamento documental segue commit/push autorizado; artefatos privados, metadados e dados reais permanecem fora do Git.
