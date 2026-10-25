# Checkpoint 0556 — Correções de Fretes qualificadas; fonte real ainda aberta

24/10/2026, após10:01BRT. Anterior: [0555](0555-metadados-reais-service-at-e-422-na-primeira-pagina.md),
SHA256 `916BCF258A526B31C557A8EE1A4D99873A80DD177D2276108897B24D739143D3`.
Mantido o pedido do usuário de continuar até obter o necessário para a extração completa.
Esta unidade fecha a integridade/índice pendentes de0555, sem novos efeitos ESL/SQL.

## Resultado verificável

- Duas correções locais qualificadas: filtros `table`+`field` e data de serviço `/service_at`, com
  presença, proveniência e precedência conservadas. ADR0068/RG-FRETE-018 no escopo local.
-71casos distintos por composição e7IT de pacote passaram; compilação estrita/style PASS.
  Não é verify integral, cobertura nova, captura real ou qualificação completa do produto.
- Pacote atual sem implantação:
  `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/fretes-contrato-real-20261024-01/snapshot/target/etl-dataexport-v2.jar`.
  SHA256 **`42251A42CEC7938EAA529C61E3045E31215334131904C5C64C1232814130D398`**.
 1319classes/45recursos e oito bibliotecas conferidos;1543inputs de fonte/configuração/manifesto
  sem drift antes/depois do índice. Correções de instrumentação e execuções falhas preservadas.
- Validador atual:3contratos/42aspectos/14fixtures sintéticas PASS. Não inclui a nova fixture como
  release ativo; o teste adicional derivado de observação permanece recurso de teste separado.
- AST concluído semLLM/agrupamento: **44392nós/116091arestas**. Job exit0,183751ms,
  pico1297178624B/limite2GiB,sem timeout, término10:01:39BRT. Semântica documental/HTML históricos
  não foram regenerados. Todos os processos próprios encerrados.
- Comparação offline extra com as classes qualificadas: os **130elementos técnicos** da fixture
  (114campos/16filtros) coincidem exatamente com a metadata Java real arquivada, incluindo tipos
  declarados e fingerprints do texto de tipo. ZeroHTTP/SQL. Os114campos continuam UNDECLARED em
  metadata; isso não inventa seus tipos de resposta.

## Evidências finais

Base externa comum: `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/fretes-contrato-real-20261024-01`.

| Artefato | SHA256 |
|---|---|
| `final-recovery/result.json` | `B48C6B10BF5454FE1481171F40B9DB1DCA927DBFD49B9A72138B5916D16350D6` |
| `final-index/result.json` | `7DC8CE6EC730F616F74D70ECD42E33A9C3AC08C3405401748F819B4EC5CAA866` |
| `final-index-memory-job-receipt.json` | `9ECCEC48ABDEDDD62DA31198C05F985641B4D25A14D8B83089DA4F93762481A1` |
| `metadata-check-result.json` | `C1C54232D5EA2E2B0616EF42A248306C682526AB08EDA16F20D853DD0FA027BD` |
| `source-units-summary.json` | `61BAC6D675013D5ED4187EA7483F799A15EE6B30FE2624240420E2C2AC6A94BA` |
| `esl-investigation-request.json` | `975C17A0C4691B96A41DEA425BF85A4FDB6EB3F5AAAA532F7050FF5A904FA1E4` |

Fixture canônica `src/test/resources/contracts/6389-info.observed-structure-20261024.json`, SHA256
`A86294A9AD298D5AF0D5C81FA2972404BC4D9EBB07A2D25B69E2B51F29E80D24`.
É estrutura derivada sanitizada, não o corpo HTTP original nem uma amostra de valores de negócio.

## O que continua sem prova

As quatro unidades de fonte de0555 consumiram8tentativas e estão fechadas. Houve INFO aceito e
uma página25 processada no leitor Java. A última consulta DATA15/p1 retornou422 em24/10
09:44:59–09:45:01BRT; corpo/request-id não retidos. Não atribuir a esse422 o corpo de timeout de
outra consulta. O timeout50/p2 de00:02BRT permanece prova separada e íntegra de0554.

**Não foi concluído o release real de resposta114campos**, nem a comparação de duas travessias
terminais, captura, processamento/persistência ou paridade. O mapper corrigido foi qualificado
com valores sintéticos e estrutura técnica real; não foi executado com novos registros reais após
o422. Nenhum tipo ou semântica ausente foi aceito por inferência. O catálogo ativo continua sintético,
TRANSITIONAL e sem prova de completude;20diferenças de metadata continuam recusadas pelo gate.

Não há job, polling, tentativa HTTP ou mensagem à ESL agendada/enviada. SQL/TDS/GraphQL/deploy,
DDL/Flyway, versão16.0.1000.6/instalação, contas/credenciais/permissões/identidades/ACL,
serviços/listeners/certificados/trustglobal, V1/WMS e produção não foram alterados. C78rollback-only,
MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED,43RAW/53exclusões e gates anteriores mantidos.

## Próximas ações

1. ESL investigar a causa/correção no relatório6389 com os requests/horários exatos já preparados.
   O422 atual ocorre na primeira página pequena; tamanho/ordem/divisão não são soluções provadas.
2. Com mudança demonstrável da condição da fonte, preparar unidade própria para o release de resposta
   atual e comparação terminal, preservando limites, identidade e decisões de nulidade/tipos.
3. Depois qualificar bindings/coorte/caminho real e, com o executor Banco e admissões correspondentes,
   comprovar processamento/persistência/paridade. Nenhuma dessas provas é herdada deste pacote.
