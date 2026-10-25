# Checkpoint 0560 — Contrato real de Fretes integrado; pacote e índice qualificados

24/10/2026, após12:42 BRT. Anterior: [0559](0559-selecao-contrato-observado-implementada-em-qualificacao.md),
SHA256 `939AA741147C0D6F12327E8D50978E5ADCE3F24D722DB8637D3F9A7114E5D5EB`.
Pedido adotado: “pois continue”. Objetivo permanece extração completa. Esta unidade encerra a
integração local da escolha do contrato real ao pedido da execução principal; não encerra captura,
gravação física, paridade, retenção ou cutover.

## Alteração e critério concluído na camada local

RT-FRETE-OBS-001/ADR0070: RuntimeOperationalRequest agora aceita a propriedade opcional
`sourceContractVersion=2026-10-24.fretes-observed-curl.v1`, somente com FRETES/BODY/uma tentativa
e fora do perfil laboratorial. Sem a propriedade, preserva o comportamento anterior. Versão
desconhecida, path arbitrário, tipo JSON errado, outra entidade ou configuração incompatível
são recusados antes da composição. O campo participa do fingerprint do pedido e o release real
é ligado à ocorrência, à política e à configuração pelo ContractExecutionBinding existente.
A invocation nova não renova a identidade da ocorrência.

DataExportObservedFretesContractCatalog carrega um recurso próprio do JAR, com limite64KiB,
SHA256 e recomputação dos três fingerprints. O arquivo tem49988B e é idêntico byte a byte ao
release observado independente de0557/0558. Preserva114 campos de metadata UNDECLARED,16 filtros
com seus tipos/fingerprints reais,115 paths de resposta e15 paths sempreNULL sem tipo não nulo
inferido. O catálogo sintético ativo e os perfis Coletas/C78 não são substituídos automaticamente.

## Qualificação executada, incluindo falhas preservadas

Base externa: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/integracao-contrato-fretes-20261024-01/`.
Plano Root antes do efeito: implementação offline, HTTP0/SQL0, sem credenciais/deploy, batches
de2GiB/12min pelo executor original. Nenhuma campanha anterior foi renovada ou reaberta.

| Passo | Resultado observado |
|---|---|
| RED contra a implementação anterior | Um teste/um erro esperado; causa OPERATIONAL_REQUEST_SCHEMA; exit do batch0 |
| Primeira compilação estrita | 750 arquivos de produção e674 de teste,46 recursos; aprovada |
| Primeiro GREEN | 57 testes, um erro e uma falha; batch exit1, evidência original preservada |
| Causas dos dois testes | Assert comparava string com lista de ordenação; fixture laboratorial antiga usava intervalo0, recusado pelo piso ESL2000ms |
| Correção | Expectativa List.of("id asc") e fixture2000ms; nenhuma regra de produção relaxada |
| Recuperação | Duas suítes afetadas,14 testes novos na recuperação aprovados;43 PASS anteriores preservados byte a byte |
| Composição final | 57 casos distintos aprovados, zero falhas/erros/skips; não foi uma execução limpa única de57 |
| Checkstyle/formatter | Aprovados, incluindo o teste laboratorial ajustado |
| Pacote | 7 IT de integridade aprovados, zero falhas/erros/skips |
| Bytes do JAR | 1321 classes/46 recursos iguais aos compilados,8 bibliotecas sem drift |
| AST sem LLM | 44475 nós/116295 arestas; exit0/sem timeout |
| Integridade final | 1547 inputs atuais iguais aos qualificados; drift0 |

Os novos casos de contrato cobrem seleção e vínculo da ocorrência, escolha anterior preservada,
recusas de seleção não qualificada, igualdade do recurso com o release independente e rejeição de
drift de tipo em campo antes sempreNULL. Casos existentes conservam a recusa de composição sem
artefato administrado e os controles Coletas. Fixtures/doubles não são prova física.

JAR novo, sem implantação:
`snapshot/target/etl-dataexport-v2.jar`, SHA256
`D81052B61702F98A12D7085FB85394180FED90C070622C35CA3D125F3EF96BCB`.
Recuperação:257257ms/pico de árvore867090432B/limite2GiB/exit0/sem timeout.
AST:206753ms/pico1301606400B/limite2GiB/exit0/sem timeout.
Nenhum `verify` integral, cobertura global nova ou aceite global foi alegado;43 RAW/53 exclusões
e resultados históricos conservam seu escopo. Os scripts/recibos da tentativa falha não foram sobrescritos.

| Artefato externo | SHA256 |
|---|---|
| `red/result.json` | `B3DFBA5952B3E9D028E59B2D0E4BEBF632C40E73C8C2CA8B7CB2D91F4996079C` |
| `green/result.json` — falho | `69D5C46A451C693692CB10C65159FBE8579EF604F1FE5FD68D33911EB60C9BDA` |
| `green-recovery/result.json` | `23B1B1E44E51F0DFF2928691A4D45B00808E2FBF16E2D798F121F8168D7BFFFB` |
| `green-recovery-memory-job-receipt.json` | `69A986751E8130D1DA80E10F0E61DCC73364372A07D6EEFE83528B34396C1778` |
| `index/result.json` | `F7747992F30243D0B1E58D1648E06C0287CC1D7D6887A8E254375C6ED6693E72` |
| `index-memory-job-receipt.json` | `A42F9C5AD4AB9E29C0BD7A852B00AA1CAC420CE9109E10B5D46F9E6FAD42D288` |
| `closure.json` | `85FCC9E1DE554999CFE643253E737F090A97823E399E9A330BD791DC2C1E9511` |
| `bank-preparation-request.md` — preparado, não despachado | `5B09CF21745F6B8005B0FBBCCFE65CD7D10F8373E488B57212D78BC246E470CB` |

O recurso observado permanece com SHA256
`19B4D5D9E093A1BA1C88978EDD46EF5755F9D6F35772B3EFA8213D3F89AD6ADB` e release fingerprint
`ad072100143c765705cd7b01e581f19ce3e5bb9a38d181f9cfeae4bcbd141b35`.

## Gravação: pendência interna concreta e escopo preparado

Não é necessário pedir nova confirmação genérica à ESL para a leitura já comprovada em0558.
A próxima fronteira é interna:

- O reader DEV de evidência de fonte ainda aceita somente Coletas e recusa Fretes antes de fonte.
- C78DevEngineAdmissionProducer é inspeção e termina sem admissão física; SAMPLE12 é Coletas.
- O gateway normal de staging Fretes chama uma procedure com25 parâmetros e executa commit por
  microbatch. Ele não pode ser ligado diretamente à prova C78 rollback-only. É necessária uma
  capacidade própria de staging na transação Banco, com readback tipado/rollback/fechamento reais.

O escopo concreto está preparado em bank-preparation-request.md: implementação/testes offline
primeiro, preservação do comportamento normal, catálogo atual e reserva Root separados antes de
qualquer efeito físico. A intenção mínima é um frete distinto, até12 linhas físicas, INFO e
página1/per1; caps SQL/tempo/bytes finais devem ser derivados do fluxo implementado. Esses números
não são reserva ou admissão e nenhum prazo físico começou.

Foi apresentada ao usuário, via pergunta assíncrona, a opção de designar um auxiliar com papel
Banco, pois AGENTS seção9 reserva SQL/TDS a esse responsável e a delegação exige a escolha do
usuário. **Sem resposta recebida neste corte; nenhum auxiliar despachado.** A pergunta não pede
novamente autorização genérica para o projeto ou para a leitura ESL. Não inferir aprovação por
tempo decorrido. Nenhuma autorização física decorre do documento preparado.

## Preservação e retomada

Esta unidade executou API0/SQL0/TDS0 e não materializou credenciais. As20 chamadas reais anteriores
continuam consumidas/encerradas; as provas de37 fretes/114 campos e terminais per20/25/50 não foram
apagadas nem repetidas. O JAR novo recebeu prova local de integração, não nova prova de HTTP Main.
Todos os jobs próprios encerraram; nenhum processo auxiliar permanece ativo à espera.

SQL16.0.1000.6/instalação, contas/credenciais/permissões/identidades/ACL/elevação, serviços/listeners/
certificados/trust global, schema/DDL/Flyway, V1/WMS/produção: zero alterações. C78 rollback-only,
MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED, gates e falhas preservados. Nenhuma mensagem
externa, novo chat, automação, commit ou push foi criado.

1. Incorporar a resposta à pergunta de designação Banco; se aprovada, despachar o escopo preparado,
   inicialmente sem SQL/HTTP/credenciais, com ownership de arquivos definido.
2. Implementar e qualificar a capacidade própria de Fretes e o vínculo de admissão de fonte/runtime;
   Root integra bootstrap/documentos, Banco conserva a exclusividade física.
3. Revisar catálogo e reservar a prova real delimitada antes do primeiro efeito. Exigir staging,
   comparação tipada, rollback e readback/fechamento observados; somente então ampliar/paridade.
