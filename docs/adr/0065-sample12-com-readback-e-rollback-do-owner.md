# ADR0065 — SAMPLE12 com readback e rollback do owner

- Data: 13/10/2026.
- Estado: produtor, owner, consumidor e coorte integrada qualificados no pedido HERMES_f4eda62e83d1 (Banco131, Regras128, Runtime142 e readbacks Root126/133/134/139). Main SOURCE0 real155 qualifica a recusa da origem;140 falhou e permanece consumida. Fonte e captura permanecem bloqueadas. Esta decisão não libera fonte, conexão ou captura.
- Responsáveis: Banco implementa admissão/persistência e é o único executor SQL/TDS; Runtime implementa consumidor e pacote; Regras revisa contratos; Codex decide unidades e mantém documentos/CI. Responsável de negócio conhecido: Lucas.

## Problema e escopo

O caminho SAMPLE anterior não provava o readback da expectativa real nem reservava toda a limpeza no teto de comandos. O rollback seguido de leitura DMV também pode abrir outra transação implícita no driver instalado. Três campos de auditoria têm collation CI_AS no alvo observado, enquanto o contrato global exige BIN2. Corrigir o caminho separado exige conservar os demais contratos, sem modificar o banco ou ampliar a amostra para caber artificialmente.

A unidade proposta `OWN_SAMPLE12_ROLLBACK_ONLY` tem request e pacote próprios: Coletas6908, per1, página1, até12 linhas físicas, até2 chamadas HTTP e até128 comandos SQL do cliente. O request histórico de1000 linhas, ordinary128, os perfis Windows/BF/E3, os defaults C78 e o contrato global BIN2 permanecem com seus critérios. Preparação e testes de componente não são captura ou autoridade física.

## SAMPLE12-EXPECTATION-001 — expectativa real em memória

Origem: Regras81, Root92 e port Runtime95/105. O consumidor entrega ao owner a ocorrência, uma raiz com sua multiplicidade física, página1 e batch1 observados na extração. Os limites são conferidos antes de copiar coleções. Essa expectativa fica somente em memória, com representação textual redigida. O owner confere vínculo da ocorrência, conjunto de raízes, multiplicidade, batches e estado de auditoria; um total agregado isolado não prova correspondência.

Exemplo: doze linhas da mesma raiz podem satisfazer per1; treze linhas recusam a unidade inteira, sem truncar a página. O resultado parcial é DEGRADED/CANCELLED ou falha real, sem travessia completa, promoção ou SUCCESS integral. O `finally` sempre chama `finishSample`, conserva a causa primária e registra falhas de limpeza. O recibo depende do readback e encerramento concordantes, não apenas do retorno da extração.

Testes: Runtime113 publicou18 casos dirigidos (port5, sample7, finish6), Java17/Werror/formatter/Checkstyle PASS. Root107 conferiu dez referências da qualificação117, com quatro casos de arquitetura PASS. São provas dessas camadas; implementação final do owner, binding e pacote agregado ainda exigem qualificação própria.

## SAMPLE12-TRANSACTION-001 — mesma transação e proteção após cada CALL

Origem: corpos instalados observados nas unidades39/63/94, Regras97 e Root103. Todas as29 tabelas protegidas continuam obrigatórias. Depois de adquirir a proteção, guardar o transaction_id original; após cada CALL exigir o mesmo identificador, TRAN1, XACT1 e as29 proteções próprias. Não aceitar uma transação substituta apenas porque ela adquiriu novos locks.

Os BEGIN/COMMIT internos dos procedimentos TSQL observados são balanceados sob a transação externa do owner. Não alegar ausência de COMMITs internos. Nenhum commit externo, promoção, DDL, grant ou correção de schema é permitido nesta unidade. `fn_runtime_tariff_valid` retorna0 antes dos descendentes quando reference_release_id é NULL ou menor que1; a dependência instalada finita dessa rota foi conferida, sem solicitar objetos de outros ramos.

Exemplo: um CALL que troca a transação recusa mesmo mantendo29 locks. Testes do owner e do contador devem exercitar esse caso com o consumidor atual; evidência sintética não substitui a futura prova física da captura.

## SAMPLE12-CLEANUP-001 — rollback e três SELECTs reservados

Origem: recusa real82, inspeção do driver83, Root92 e prova real99. Reservar três SELECTs de limpeza antes do primeiro efeito. Após rollback, consultar TRAN/XACT sem FROM; consultar ausência da ocorrência e proteções próprias por leitura delimitada; reverter a nova transação de leitura; consultar TRAN/XACT final sem FROM e fechar a conexão. Não usar SET, autoCommittrue ou COMMIT para produzir TRAN0.

A unidade99 executou36 SELECTs, duas chamadas ownerrollback e fechamento conhecido. Fases brutas34[0,0],35[0,1,1],36[0,0] demonstram a nova transação de leitura e seu encerramento. As29 proteções foram provadas naquela transação já encerrada; não constituem reserva contra writers em captura futura. A recusa82 permanece registrada e consumida.

O contador deve medir os wrappers reais, incluindo CALLs, conferências e limpeza, para N1/N12 e HTTP2. Batch fica limitado a100. A estimativa estática122/128 é hipótese de planejamento, não resultado executado ou saldo reutilizável. O owner deve preservar margem de limpeza e recusar antes de ultrapassar o teto.

## SAMPLE12-COLLATION-001 — exceção limitada aos três labels

Origem: catálogo39, readbacks independentes40/96 e Regras97, adotado por Root103. Somente a comparação da collation dos campos `ctl.execution_audit.status`, `traversal_verification` e `failure_category` pode ser dispensada no novo SAMPLE12 vinculado ao consumidor e aos corpos instalados conferidos. Tipo, presença e todos os outros gates continuam obrigatórios. Não declarar compatibilidade BIN2 global ou alterar as colunas.

Exemplo: collation CI_AS nesses três labels não recusa por si só essa rota delimitada; coluna ausente, tipo divergente, outra collation incompatível ou outro gate falho recusa. Testes devem comprovar essas recusas e a manutenção do contrato ordinary128/globalBIN2.

## Fonte, admissão e limite do aceite

6908 identifica o relatório Coletas, não um saldo numérico. A exigência local de quotaBucket/TTL/remainingRequests não tinha origem ESL demonstrada e foi corrigida conforme ADR0064. O bloqueio legítimo permanece: não foi retido o vínculo entre a configuração da operação aceita em05/10 e a configuração atualmente provisionada. Não usar chamada nova para descobrir liberação, recibo positivo inventado ou hash como procedência.

O produtor físico deve usar despacho Root, claim exclusivo e permit privado próprios, separados da inspeção. Fonte factual HELD recusa antes de credencial SQL, sessão ou HTTP. Cada prova física futura depende de bytes finais, alvo/identidade atuais, evidência da fonte, proteção efetiva, limites e reserva próprios. Recusa Main com zero efeitos qualifica somente a recusa.

Recuperação: desabilitar os novos opt-ins e fechar recursos próprios; conservar ocorrências, falhas e resultados incertos para reconciliação. Nunca apagar dados retidos ou repetir claims consumidos. SQL Server16.0.1000.6/instalação, contas/credenciais/permissões/identidades/ACL, serviços/listeners/certificados/trust global, DDL/Flyway, V1/PM2 e produção preservados.

Referências privadas no diretório `hermesf4eda62e83d1`: `root/sample12-preparation-decision92.json`, `sample12-closure-decision103.json`, `leaf-independent-readback96.json`, `protection-independent-readback101.json`, `architecture-independent-readback107.json`; `regras01/handoff97.json`; `banco01/handoff95.json`, `handoff100.json`; `runtime01/handoff-sample113.json`, `handoff-architecture117.json`; `fontes01/handoff32.json`. STATES conserva os resultados sucessores e pendências reais.


## Qualificação final dos componentes, 14/10/2026

O consumidor concreto chama C78Sample12Selection.from(scope, request.plan) e C78Sample12AdmissionProducer.sample12, passando o reader factual da fonte ao mesmo adaptador real. SOURCE0 recusa qualquer abertura mesmo se a fonte mudar. O guard de finish fica imediatamente após open, incluindo checkpoint, DataSource e gateway; tentativa única antes da chamada ao owner e close independente preservam causa primária, suppressed e Error. Correções R109/Root110 foram exercitadas em casos dirigidos.

O owner reserva dois slots normais antes de cada CALL para o CALL e sua prova de transação, além dos três slots de limpeza. Wrappers reais em componente mediram60/82 comandos para N1/N12 e H2; não são contagens de captura física. A coorte final tem746 fontes,1308 classes e zero drift atual;14 classes Banco foram reconstruídas idênticas. Dez casos componentes únicos, seis de arquitetura e o classifier afetado final passaram; falhas causais de fixture anteriores foram preservadas. Java17/XlintAll/Werror, formatter, Checkstyle e AST restrita passaram no escopo publicado.

Contratos instalados conferidos:312 colunas,309 conformes e somente as três exceções de collation;29 corpos de procedures correspondentes. Ordinary/globalBIN2 permanece não qualificado. Emissor privado adotado por Root e controlador imutável não são autoridade: unidade, limites, reserva e claim novos continuam obrigatórios. Readbacks privados: root/aggregate-independent-readback134.json, runtime-final-independent-readback139.json, native-independent-readback138.json; runtime01/handoff-integrated142.json, banco01/handoff131.json, handoff132.json, regras01/handoff128.json.


Resultado Main155:74848/exit20 SOURCE_QUOTA/STOP429_SAME_CURRENT_OPERATIONAL_SCOPE_NOT_PROVED, launcher70748/exit0/5,766s, Root privado e claim novos conferidos, sem START/token/provider/SQL/HTTP. Jobs atuais0 e fechamento conhecido; prova negativa somente. Regras153 aceita coorte atual8782/132 pelo delta exclusivo21LineNumberTable+23,9métodos/149codebytes e todos outros bytes idênticos; hashes brutos diferentes preservados. Dois testes pré-helper são atuais; seis helper122 continuam históricos. Fonte/captura/paridade/goalfalse; conferir checkpoint0525 e readback Root156.

Regras160 aceitou a prova negativa real155 (35checks/14classes), com stdout/stderr apenas consumidos e recibos semânticos retidos. Root163 conferiu o parecer final. Extração e paridade continuam não verificadas; f4 encerra por bloqueio externo, sem liberar nova unidade. HANDOFF_PATH histórico ausente continua lacuna documental, não reconstruída.


## Nova autoridade C54, 14/10/2026 — validação operacional atual

O pedido expresso HERMES_c54d6a25880d autoriza uma validação ESL nova e legítima com a configuração real existente do V1, lida sem alteração e sem publicar segredos. A restrição à chamada nova descrita no fechamento F4 continua como fato daquele pedido encerrado; não impede o procedimento controlado expressamente autorizado neste pedido. Não recuperar por suposição o vínculo histórico, nem repetir unidades consumidas.

A primeira operação definida é uma GET do INFO do relatório6908, uma tentativa, até30 segundos e10MiB, serial, sem redirect, retry, fallback ou troca de identidade. Antes da chamada, Root deve conferir bytes qualificados e emitir a reserva concreta. INFO aceito comprova acesso e metadados naquele contexto; não prova saldo numérico, captura, persistência ou paridade. Primeiro não2xx,429, erro de limite ou resposta inválida interrompe o dependente. A Main não serve de sonda para contornar o próprio gate.

O desenho composto conserva a observação real e os inputs efetivos na mesma unidade viva. Valores selecionados de V1/V2 são comparados somente em memória; arquivos permanecem sob leitura protegida até o encerramento do filho. A Main usa os mesmos inputs protegidos. Recibo JSON positivo, nonce ou hash isolados não provam a origem: o consumidor exige a ligação com o controlador genuíno, a mesma operação e o holder ainda aberto. Verificações repetidas conferem a observação existente, sem nova chamada HTTP. A qualificação deve demonstrar essa ligação e a recusa após fechamento ou divergência.

O teto composto declarado é INFO prévio1 mais até2 chamadas da Main, total3; o teto interno HTTP2 da Main permanece. Só se uma reutilização real dos metadados for implementada e qualificada pode o composto inteiro ser descrito como HTTP2. A futura unidade Main exige pacote final e admissão/reserva próprios. SAMPLE12 continua per1/página1/máximo12 linhas,128 comandos,29 proteções, mesma transação original, readback, rollback e cleanup3. A aprovação E3 para retenção não se transfere ao perfil C78 rollback-only.

Paridade limitada exige comparar o mesmo dado real com uma referência V1 independente e revisada. Igualdade dos catálogos de status em código é preparação; captura/readback próprios não provam igualdade V1. Nenhum resultado positivo da ESL, SQL ou Main foi obtido por esta decisão documental. Referências atuais: root/source-criteria-decision04.json, passive-decision06.json, source-design-decision07.json e source-provisioning-finding08.json em hermesc54d6a25880d.


## Validação atual independente INFO_ONLY C54,14/10/2026

O primeiro GET6908/info físico foi admitido e reservado por Root no caminho leve próprio, com Source/Bank hooks qualificados; HTTP200/4820bytes/31campos e fechamento conhecidos. Root39 encerrou antesSource/inputs/HTTP por alias UV junction;Root40 provou a causa;sucessor43 append-only usou o mesmo executável no caminho canônico sem relaxar noReparse,alterar instalação ou repetir39. O primeiro claim HTTP está consumido. Observação original e inputs efetivos pertencem ao holder encerrado,que não se reconstitui por JSON.

Este aceite é somente conexão/metadata. Não libera quota reutilizável,Main,captura,persistência ou paridade. O composto Root20 continua desenho qualificado,não execução;seus critérios não foram dispensados. A conferência integral Root32 e Main continuam recusadas pela proteção de memória86/87% acima85%. Após memória elegível,uma nova unidade física/observação viva precisa de decisão e reserva próprias;não reutilizar a primeira GET consumida. C78 SAMPLE12 mantém rollback-only/12linhas/128comandos/all29/cleanup3;E3 retido e todos defaults históricos preservados. Fonte d7a e controller e9 permanecem intactos;Source08 corrige apenas bootstrap/caminho e sucessão delimitada.
