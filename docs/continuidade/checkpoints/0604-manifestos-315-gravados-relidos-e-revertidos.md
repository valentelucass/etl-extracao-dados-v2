# 0604 — Manifestos: 315 registros gravados, relidos e revertidos

A unidade real de Manifestos Data Export6399 completou a extração do recorte de17/10/2026, a gravação e a comparação de315 registros no banco local. A leitura de volta foi igual à entrada. O rollback original, a ausência nas oito tabelas verificadas, o rollback das leituras de limpeza e o fechamento foram observados. Não houve DDL nem commit de negócio retido.

O resultado geral da execução permaneceu com erro: o processo Java fechou0, mas o controlador procurou um campo antigo no comprovante final. Essa diferença foi reproduzida e a correção mínima passou em cinco testes locais com o comprovante real. Os recibos originais não foram reescritos; isso ainda não é uma execução geral com exit0, adoção do runtime, promoção ou implantação.

## Prova real e limite do resultado

- Unidade: `root-manifestos-bank-0604-01`;315 linhas físicas,17 raízes,4 lotes. INFO1+DATA2: três reservas e três conclusões; página final vazia. Não é prova de cobertura histórica universal.
- Banco:315 gravadas e315 relidas com igualdade tipada;11 locks próprios de transação; duas passagens completas STRICT,70 SELECT de catálogo;467 operações; quatro commits do adaptador suprimidos.
- Fechamento: um ACK de rollback original, um ACK do rollback de leitura, ausência8, dez SELECT pós-rollback, identidade própria da transação de leitura provada e transação original ausente; conexão/processo/stdin/socket fechados conhecidos. SQL Server16.0.1000.6 e identidade nativa preservados.
- Processo Java0 e `physical.qualified=true`, fase COMPLETE. Controlador `qualifiedPhysical=false`, erro sanitizado preservado; executor original1, sem timeout,47.574ms, pico802.803.712B sob1GiB/6min.
- Consumidor antigo exige `completePhysicalTablesRevalidated`, ausente. O produtor emite `catalogLockedTrialTables=11`. A correção troca exatamente essa expressão, sem fallback para o nome antigo e sem relaxar cleanup, locks, contagens ou timestamps.
- Cinco testes próprios reproduziram a recusa antiga, aceitaram o comprovante real corrigido e recusaram campo ausente/10, somente alias antigo e cleanup incompleto. Original0,3.099ms,85.721.088B sob1GiB/2min; sem nova Source ou SQL.

Recibos externos preservados, sob `Armazenamento-Projetos/etl-extracao-dados-v2/quatro-etapas-20261024-01`:

| Evidência | SHA256 |
|---|---|
|0604/controller-receipt.json|718B0E66AD7484A2B33838103B98CEF2D7A24F96CF7C89591DD57532CD8BF215|
|0604/original-memory.json|654D1D1820C6D1DFDD90CF035FB5A50B2DDF33E844198235ECC3845F563D3616|
|0604/child/physical-result.json|2846E9AD50E550B0E2C8CC67BFEB7AE61F90AE454FCD62E35B1CA9D172B144C9|
|RootManifestosPhysicalCompleteControllerFailure0604.json|07C28D53145A0243A52AE962C47E406D28988C3020176BC9FB7549CF01483374|
|Consumidor own5 qualification|AFA3749299873B3D9A49E21693368ECEAADE5FF0D54CDCB4CAE83AC03AE7A97A|
|Consumidor own5 original|986569B55528B09FBA21550F4FC3C63A25B9486FADC26D36DFC473CD7260A67C|

O último par Source agora é0604, com delta local3;0601 continua histórico. Os contadores globais continuam não finais. As unidades e janelas anteriores estão aposentadas sem replay/renovação automática. O STOP permanece preservado. SQL-only0602/0603 não são pares Source.

## Preparação bancária qualificada

O wrapperV5/B27 não foi executado: revisão encontrou caminho antigo do classrootV13b. O sucessorV5b/B834 restringe a operação ao classrootV14 e permite o antigo apenas na regressão histórica explicitamente marcada.

V5b passou127 próprios,124 preservados mais três causais: original0/99.778ms/503.308.288B. Revisão independente sem bloqueios. O preflight operacional CURRENT também passou:127/274,16 descritores de classe,122 pins próprios e1107 externos; original0/25.549ms/118.607.872B. Essas provas precederam a emissão pessoal finita0604. Nenhuma delas foi tratada como prova antecipada de banco.

O emissor compilou com original0. Uma checagem externa inadequada de LASTEXITCODE interrompeu o encadeamento antes de chamar a Source; o recibo original confirmou o sucesso e a mesma unidade foi continuada dentro de sua janela, sem recompilar, renovar ou repetir consultas.

## Integração: causa medida, correção ainda em teste

R33 preservou a primeira falha da execução de integração: `CIM_HEARTBEAT_MISSING`; os erros de cleanup vieram depois. Original2, sem timeout/OOM. Não são sete testes aprovados.

O experimento isoladoR34, com arquivo próprio e sem CIM/JVM/API/SQL, reproduziu indisponibilidade durante File.Replace. Foram10.551 leituras e557 publicações: na cadência250ms, seis erros de ausência e sete de compartilhamento; na fase acelerada,84 de ausência e195 de compartilhamento. Controles estáveis996+986 válidos;32 tempos retidos e260 adicionais contados;14 ausências e18 falhas de compartilhamento retidas ocorreram dentro do intervalo de publicações. Original0/21.018ms/123.740.160B. Isso demonstra o mecanismo isolado; não prova retrospectivamente a causa exata deR33.

O controleR35, com MoveFileExW e leitor inalterado, falhou: acesso negado numérico5 na14ª substituição, após13 concluídas. As1.834 leituras da fase não tiveram erro, mas faltaram a fase acelerada e o controle final; resultado inconclusivo, sem alegar ausência de gaps. Original2/7.467ms/105.205.760B. Não houve retry nem mudança de permissões. Controle de sincronização entre publicação e leitura em preparação, mantendo limites e verificações.

INFO predecessorV6 passou30 modelos/original0; não repetiu os300 do coletor nem emitiu Source/CODE/janela. Com0604, esse reconhecimento de0601 é histórico.

## ESL, outras entidades e próximo trabalho

O Chrome foi observado autenticado no relatório de Manifestos; a prévia limita100 e o período padrão difere do recorte do ETL. Durante a preparação do filtro, o controle da aba foi interrompido. A aba continuava listada; o Root não clicou no botão de consulta ou exportação, nenhum Excel foi baixado, nenhuma paridade concluída. Reconexão solicitada de forma assíncrona; não houve logout ou fechamento solicitado pelo Root.

A matriz independente de11 entidades, com61 referências conferidas, separa reutilização, wiring e DDL: Coletas/Cotações/Manifestos têm reutilização candidata; Fretes altera dois objetos; Localização uma procedure; CAP/FAT/INV/SIN usam proposta compartilhada de cinco objetos; Raster cinco mudanças semânticas; Usuários integral possui três objetos adicionais propostos. Existência histórica não substitui catálogo atual, consentimento ou prova própria. Nenhum desses DDL foi aplicado.

Próximos passos: propagar a correção mínima do consumidor ao controlador operacional após revisão; completar integração com publicação sincronizada; avançar o caminho de Coletas e as qualificações próprias das demais entidades; concluir a decisão concreta de schema quando necessária; comparar os mesmos relatórios/cortes/filtros/fuso na ESL. Fretes6389 permanece distinto de GraphQL; Localizador8656 não é Raster. Usuários continua sem EOF; etapas3/4 e extração completa de todas as entidades permanecem abertas.

Root continua único executor pesado/API/SQL/browser/Git; outros chats somente arquivos. ESL somente consultas/filtros/downloads. Sem retenção de dados de negócio no Git, alteração de versão SQL/serviços/contas/ACL/V1/PM2, deploy ou cutover. Commit e push seguem obrigatórios para alterações concluídas.