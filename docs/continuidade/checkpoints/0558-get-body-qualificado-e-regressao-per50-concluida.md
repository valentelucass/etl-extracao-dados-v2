# Checkpoint 0558 — GET com corpo qualificado e regressão de 50 por página concluída

24/10/2026, após 11:03 BRT. Anterior: [0557](0557-documentacao-curl-e-java-concluem-fretes37.md),
SHA256 `0DDDA995486BE69AD0C1B0DDA8A3F1FFABBC88C18654C11A89B25B2723DEC42C`.

## Objetivo, autorização e resultado

O usuário determinou continuar a extração completa e, nesta retomada, investigar diretamente com
Postman/cURL e com a documentação ESL já fornecida. A unidade atual fecha a qualificação da escolha
explícita de GET com corpo e comprova, com o pacote novo, a leitura do caso que antes falhou com
50 por página. Não é necessário aguardar uma resposta genérica da ESL para esse caminho de leitura.
A extração completa, a execução principal e a gravação SQL continuam com seus critérios próprios.

As provas anteriores desta mesma retomada estão em 0557: coleção oficial relida, duas travessias
cURL sem ordem e duas travessias Java com `id asc`, dia 17/10/2026, `per=25/20`, todas terminais,
com 37 IDs/37 linhas por travessia, igualdade integral de valores/multiplicidades entre as passagens
comparadas em RAM e 37 registros válidos no mapper. Nenhum valor ou ID de negócio foi retido.

## Mudança local qualificada

`RuntimeConfigurationFactory` aceita agora a escolha explícita `GET_WITH_BODY` em arquivo ou
ambiente, além de `GET_WITH_QUERY`. O cliente HTTP já suportava corpo. O valor selecionado percorre
o fingerprint efetivo e a factory HTTP existentes; carregar a configuração não materializa segredo.
POST continua recusado. Não se mudou default, perfil privado, catálogo ativo ou mecanismo de admissão;
não se adicionou fallback. ADR0069 registra a decisão e o release observado independente.

Evidências em `C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/adocao-get-body-20261024-01/`:

| Verificação executada | Resultado |
|---|---|
| RED causal contra a restrição antiga | 2 erros esperados; evidência preservada |
| Nove suítes selecionadas, incluindo configuração, bindings, HTTP, resiliência e proteção6908 | 88 testes, zero falhas/erros/skips |
| Compilação estrita | 749 arquivos de produção e 673 de teste |
| Checkstyle e formatter dos dois arquivos modificados | Aprovados |
| Integridade offline do pacote | 7 IT, zero falhas/erros/skips |
| JAR versus classes/recursos efetivamente compilados | 1319 classes, 45 recursos, igualdade byte a byte |
| Bibliotecas de runtime | 8, sem drift em relação ao pacote anterior qualificado |
| AST atualizado sem LLM | 44431 nós/116151 arestas; processo concluído com exit0 |
| Integridade após AST e prova real | 1543 inputs atuais iguais aos qualificados; drift0 |

JAR novo, sem implantação:
`adocao-get-body-20261024-01/snapshot/target/etl-dataexport-v2.jar`,
SHA256 `977C1A0B6C46AE88C38879E5ECF860F9D6A8F2F30147034EDB6A3B25C54F1CBA`.
Qualificação green: 261025ms, pico de árvore866123776B, limite2GiB, exit0, sem timeout.
AST: 211839ms, pico1299730432B, limite2GiB, exit0, sem timeout.
Isso não constitui novo `verify` integral ou nova cobertura global. Os resultados anteriores,
43 RAW, 53 exclusões e os gates globais não foram reclassificados.

## Regressão real no pacote novo: per50 e página2

Nova unidade própria, sem reabrir as consumidas: `regressao-body-per50-20261024-01`.
Plano Root antes do efeito; helper compilado estritamente e selfcheck offline aprovado antes da
rede. O helper carregou `runtime.properties` pela **RuntimeConfigurationFactory do JAR novo**,
validou BODY/uma tentativa/intervalo3s e carregou o mesmo release real congelado antes das chamadas.
Não foi usado um mock, nem foi invocado Main ou aberto SQL.

Reserva: INFO6389 e duas DATA, máximo3 chamadas/150s, timeout30s/resposta10MiB, `per=50`,
máximo2 páginas/100 IDs/1000 linhas físicas, uma tentativa, serial, intervalo3s após conclusão.
Parada no primeiro erro HTTP/429/contrato/limite/quarentena; sem redirect, retry ou fallback.
Mesma origem e token existente, lido somente em memória pelo provider autorizado e entregue por stdin.
Os rótulos diagnósticos de source/tenant não recebem autoridade de identidade operacional.

- Início13:59:11,632Z e conclusão13:59:23,622Z (10:59:11–10:59:23 BRT).
- INFO aceito pelo contrato congelado de114 campos/16 filtros.
- DATA página1: **37 IDs distintos/37 linhas físicas, 114 campos**, serviço no dia17/10;
  contrato de resposta, ordem/progressão por ID e limites aprovados.
- DATA página2: **vazia, terminal**, sem timeout.
- Mapper atual: **37 VALID, zero quarentena**.
- Três chamadas bem-sucedidas, nenhum429; Job exit0/14602ms/pico255197184B/limite1GiB/sem timeout.
- Fingerprint da configuração efetivamente carregada:
  `ecabe94eea92430889f0ea33d9520bce86221b824c873222d40a66757eddd51c`.

Esta terceira granularidade chegou ao mesmo volume observado. Não houve comparação de valores
com as passagens antigas porque seus dados não foram persistidos; a igualdade integral documentada
em0557 continua limitada aos pares comparados vivos em RAM. A prova não garante um snapshot global.

O GET envia JSON no corpo, conforme a [documentação oficial](https://documenter.getpostman.com/view/20571375/2s9YXk2fj5#50f26905-387b-4764-88f0-86c0205af9b6):

```json
{
  "search": {"freights": {"service_at": "2026-10-17 - 2026-10-17"}},
  "page": "1",
  "per": "50",
  "order_by": "id asc"
}
```

Endpoint: `GET /api/analytics/reports/6389/data`. A página2 usa o mesmo body com `page="2"`.
Autenticação existente permanece fora de documentação, argumentos, respostas e logs.

## Evidências e interpretação

| Artefato, relativo à base externa do projeto | SHA256 |
|---|---|
| `adocao-get-body-20261024-01/green/result.json` | `1A018580B0FB25E53915E31BE36C5898EE53396DA151965146DBAD45F6107DF4` |
| `adocao-get-body-20261024-01/green-memory-job-receipt.json` | `60FA6DFAB897DB23F5D1C2E5D57A5C2C5CC0A69316B8096177D990F9DFE04415` |
| `adocao-get-body-20261024-01/index/result.json` | `409FE85D38F90CEECDFFAD01967F9FE08EF9FFC62E8465E456854289C4B4A854` |
| `adocao-get-body-20261024-01/index-memory-job-receipt.json` | `7C9FF97840DE7C06C4091164F14DB9EDE195E327C8A0132F56C2FABB8EDAEFD1` |
| `adocao-get-body-20261024-01/closure.json` | `C0BBD87012AEDF4CC586A97CC8A754D8BC7E848D0EEAF6F3CEA3FBFACDABB84E` |
| `regressao-body-per50-20261024-01/java-body50-qualification.json` | `93574AC18B281EDE0F9358373118241D8C8F593173165710175C9AFDC1A70B93` |
| `regressao-body-per50-20261024-01/java-body50-live/results.json` | `2535C074052CD8A8696DB53477B2AC25BCCF9D49ECFF46D2086571D41BE15191` |
| `regressao-body-per50-20261024-01/java-body50-live-v2-memory-job-receipt.json` | `C8EE79C97E265ED8BA27F2F7CD269FA7436FB74AE78AFB0B1FB77FDDB5B2480F` |

Total desta retomada: **20 tentativas de fonte** (17 de0557 +3 desta regressão), um422 explícito
na comparação QUERY/ID, demais chamadas bem-sucedidas, nenhum429. O422 foi interrompido/preservado
em sua unidade. O histórico também contém sucessos com QUERY/ID: não declarar uma causa interna
exclusiva ou que BODY elimina toda intermitência. A mesma credencial funcionou; não houve rotação.

O release observado continua com SHA256
`19B4D5D9E093A1BA1C88978EDD46EF5755F9D6F35772B3EFA8213D3F89AD6ADB` e fingerprint
`ad072100143c765705cd7b01e581f19ce3e5bb9a38d181f9cfeae4bcbd141b35`.
Os15 paths sempreNULL continuam sem tipo não nulo observado. O catálogo sintético não foi substituído.
Nenhum ID, valor de negócio ou digest individual foi retido. Todos os processos próprios encerraram;
não há efeito pendente sem confirmação nesta unidade. Os zeros HTTP/SQL em `closure.json` são do
verificador de integridade, não apagamento das20 tentativas reais anteriores.

SQL/TDS/GraphQL/deploy/DDL/Flyway, versãoSQL16.0.1000.6/instalação, contas/credenciais/permissões/
identidades/ACL/elevação, serviços/listeners/certificados/trust global, V1/WMS/produção: zero alterações.
C78 continua rollback-only, MainHTTP histórico UNKNOWN/MISSING_NOT_RECONSTRUCTED. Nenhuma mensagem
à ESL ou ao gestor, novo chat ou automação foi criada.

## Retomada imediata

1. Integrar seleção explícita do release observado à rota principal: atualmente
   `RuntimeOperationalRequest` usa `RuntimeLaboratoryContract.resolve`, que seleciona o catálogo
   sintético fora do laboratório. Vincular release/configuração/ocorrência reais, sem tornar o
   sucesso diagnóstico uma admissão Main ou alterar por herança os perfis6908/C78.
2. Preparar e admitir a pequena prova de captura/persistência com coorte e limites atuais, proteção
   física e leitura independente do alvo; Banco conserva a exclusividade de SQL/TDS e C78 não ganha commit.
3. Comprovar paridade e ampliar janelas/entidades em unidades próprias. Não solicitar confirmação
   externa genérica sobre o caminho de leitura já comprovado, nem declarar extração completa antes
   dos critérios de persistência, cobertura e paridade.
