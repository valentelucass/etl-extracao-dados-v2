# Checkpoint0441 — primeira extração Java pela API comprovada

- Data: 05/10/2026; execução real entre 17:30:43 e 17:30:47 UTC.
- Anterior: `0440-primeiro-run-certificado-alterado.md`, SHA256 `515BF15C88DCAADFEE5D72F471DB5433BEF059F39DF4F79C066B95DDC30A415F`.
- Objetivo: pedido direto do usuário para rodar V2, corrigir causas com documentação/testes e conseguir a primeira extração, deixando o V1 parado.
- Estado: extração Java pela API concluída na camada de memória; SAMPLE com SQL aguarda decisão específica de confiança. STATES continua canônico.
- Unidade: `target/first-extraction-20261005/`. Git inicialmente limpo; alterações rastreadas apenas em documentos de continuidade. Helpers e recibos privados nessa unidade, sem mudança de Java de produção, POM, CI ou schema.

## Execução e limites

Fontes executou uma ocorrência real sob `source-only03-dispatch.json`, SHA256 `03a344f16abaca6a23e31a2c87b58246055ebdc7c50041f3e48c5f8479d974b0`. Reserva própria gravada antes do Java. Coletas6908, dia01/10/2026, página1/per1, teto1000linhas,2HTTP,30s/10MiB por chamada e deadline60s; HTTPS estrito, sem retry/redirect/fallback. Os ledgers Hermes e das recusas anteriores permaneceram fechados.

| Critério | Observado | Evidência na unidade |
| --- | --- | --- |
| INFO e contrato de metadados | HTTP200,31campos, ACCEPTED | `runtime/source-only03/run01/child-receipt.json` |
| Página de dados e contrato | 2XX; código exato não exposto pelo port; ACCEPTED | mesmo recibo |
| Limite e mapper real do JAR | 1 entidade,1 linha física,1 mapeada,0 quarentenas | mesmo recibo |
| Orçamento | 2 chamadas, INFO1+dados1; saldo reutilizável0 | `runtime/source-only03/run01/ledger.json` |
| Encerramento | exit0, processo/lease/mutex fechados; PID próprio ausente na revisão | `runtime/source-only03/run01/close.json` e revisão Root |
| Camada SQL | 0 conexões nessa amostra; sem staging, readback, rollback de staging ou promoção | `runtime/source-only03/run01/receipt.json` |

Root conferiu os sete recibos, hashes do despacho e da observação V1, cronologia reserva→processo→término antes do deadline e ausência do PID próprio. Revisão `supervisor-first-extraction-final-review.json`, SHA256 `7343875e45afee0251c7ebf27e51905c18185321a8965a3f31acc2246233809b`. Nenhum resultado incerto ou processo próprio remanescente. Não houve persistência de payload, IDs ou segredos, nem prova de completude da janela.

V1 observado antes da execução: PID conhecido ausente, zero descendentes conhecidos, cache ID7 parado/PID0. Sem RPC/prova de ausência global. Nenhum efeito PM2 e nenhuma restauração por suposição.

## Correções e qualificação

- Primeira recusa local: conversão implícita PowerShell reinterpretou05/outubro como10/maio. Sucessor02 preservou strings ISO e fez parse explícito com offset/invariante;29contraprovas PASS em três culturas. Recusa original sem reserva/Java/HTTP preservada.
- Segunda recusa local: verificador excluía `$` de nove classes internas Java legítimas. Sucessor03 admite esse caractere somente no nome final de uma classe interna `.class`, mantém caminhos relativos confinados, hashes e inventário.19contraprovas PASS. A função real do controlador validou offline12arquivos Runtime,13Fonte,11classes e9JARs do manifesto final.
- Fonte36testes, controlador15,9recibos causais e preflight sintético anteriores preservados sem repetição idêntica. Artefatos Fontes SHA256 `20bbe29093b14b699a3a3ee6ea0ff468bf8223b161a259469fbfaea3b6ec0ff4`; Runtime03 `c40686e492b2151ee9b8561e4f9eeec7c77a9d1090be482459e7cc2ca5ddb651`; admissão final `ed7621d2817e7f5e8d566378474124f90a06bfb63311e53b3c80725b46bdccc9`.
- JAR de produção e pacote qualificado permaneceram iguais. Sem novo Maven integral ou Graphify, pois as alterações de código são helpers privados. Correções JDBC e composição integrada continuam qualificações offline; a nova prova JDBC foi recusada antes da autenticação por `LEAF_CHANGED`.
- Preparação documental PASS33etapas/48IDs/9pacotes; scanner PASS4252candidatos/4250textos/2binários aprovados/0achados antes deste checkpoint. Validador histórico de trilha FAIL `HANDOFF_PATH`: falta `target/tres-etapas-20260922-01/closed-receipt.json`. Não reconstruir recibo nem alterar manifesto para esconder a lacuna. UTF-8/diff finais serão registrados em recibo próprio.

## Próximas ações — até três

1. Usuário responde à pergunta já apresentada sobre confiança manual no novo certificado SQL SHA256 `E3A5A7061FB56C225F93CC330CD733F5911F7D3F420D608EBE9AFA7371ACE276`. Proposta concreta `banco/public-leaf01/identity-decision-proposal.json`; ainda sem aprovação.
2. Somente com essa decisão, preparar e qualificar o perfil cliente restrito ao SAMPLE local, preservando recusa de folha alterada e todos os demais perfis; depois Banco verifica identidade/contrato físico sob reserva nova.
3. Com gates de origem, janela e proteção de writers comprovados, executar a amostra integrada reservada com staging/readback/rollback/POST, sem commit/promoção. A prova API atual não substitui esses critérios nem renova orçamento.

A nova folha foi capturada em1socket sem autenticação/SQL/segredo, com fechamento observado. É autoassinada, RSA3072/SHA256, sem SAN/extensões, válida de05/10/2026T12:36:39Z a05/10/2056T12:36:39Z; procedência independente não comprovada e hostname localhost recusado offline pelo driver. AGENTS exige “não aprender/renovar pin automaticamente”; a aprovação antiga cobria exclusivamente BF723. Nenhum certificado, serviço, listener, conta, permissão, versão16.0.1000.6, schema ou produção foi alterado.

Nenhum checkbox/P08/G01/cutover novo. CONTEXTO_GLOBAL continua ausente. Banco, Runtime e Fontes ociosos após entrega; sem callback, polling, rotina ou processo de retomada. API concluída; próximo trabalho SQL depende da decisão específica acima.
