# ADR 0069 — GET com corpo explícito e release observado de Fretes

- Data:24/10/2026.
- Estado: suporte de configuração qualificado localmente e no cliente real; sem adoção operacional Main/SQL.
- Escopo: escolha explícita de transporte, evidência estrutural6389 e V2-025a/V2-022.

## Evidência

A documentação Postman indicada pelo usuário foi relida em sua coleção pública atual. O exemplo
Data Export usa GET com JSON no corpo (`search`, `page`, `per`), com intervalo mínimo de2s por IP.
A prova nova usa3s, uma tentativa, semredirect/fallback. Dia17/10/2026:

- cURL/GET_WITH_BODY sem ordem adicional: per25 e per20 chegaram à página3vazia,37IDs/37linhas
  em cada travessia, igualdade de mapas completos de valores/multiplicidade,37mapperVALID.
- Matriz controlada, mesma janela/page1/per25: query semordem200; corpo/id asc200;
  query/id asc422 com `errors=[timeout]` e `formatted_errors=[Tempo limite excedido]`.
- Cliente Java oficial, GET_WITH_BODY/id asc: INFO e duas travessias per25/per20 completas,
 37IDs/37linhas/37mapperVALID por passagem, contrato independente congelado antes das chamadas
  e igualdade de mapas integrais. Não é apenas um teste com JSON sintético ou cURL.
- Regressão no pacote novo, carregando a escolha BODY pela RuntimeConfigurationFactory:
  INFO e per50/dia17 completaram três chamadas, 37IDs/37linhas na primeira página e terminal
  vazio na segunda, 37mapperVALID/zero quarentena. O caso per50/página2 antes falho passou
  neste caminho; não houve comparação de valores com as passagens já encerradas.

São observações desta rodada. O histórico contém sucessos e falhas com query/id, portanto não
declarar que o transporte/ordenação seja a única causa interna dos timeouts ou que corpo garanta
disponibilidade universal. A mesma credencial existente funcionou; não houve troca/rotação.

## Decisão

1. Permitir `GET_WITH_BODY` **explicitamente** em `dataexport.transport` ou
   `V2_DATAEXPORT_TRANSPORT`, além do `GET_WITH_QUERY` já aceito. O mesmo valor percorre
   settings, fingerprint efetivo e factory HTTP existentes; nenhum segredo é materializado por
   carregar a configuração. POST_JSON continua recusado e não se adiciona fallback automático.
2. Manter defaults, perfis privados, templates/semântica histórica e flags de fonte atuais.
   A capacidade de carregar BODY não altera a admissão de cada rota nem autoriza captura.
   O catálogo sintético ativo não vira contrato real por essa opção.
3. Versionar o release6389 real separadamente em
   `docs/catalogos/contratos-primeira-onda/observados/fretes-6389-20261024/`.
   Registrar metadata114/16 sem tipos declarados, raizARRAY,115paths, nulidade/tipos observados
   e15paths sempreNULL. Não preencher tipos não observados nem usar nomes para inferi-los.
4. A adoção operacional exige selecionar/bindar esse release e o transporte real à ocorrência,
   preservando os gates de identidade, completude, limites, fonte e SQL. Os perfis C78/6908 e
   o `approvedTransport` histórico dos templates não recebem autoridade BODY por herança.

## Verificação e reversão

Os casos novos de arquivo e ambiente devem falhar contra a validação antiga, passar com a menor
correção, conservar uma tentativa/intervalo2s e gerar fingerprint distinto de QUERY.
As suítes existentes continuam recusando POST e defendendo os perfis/contratos anteriores.
Resultado executado: RED causal2erros esperados,88 testes locais/7IT aprovados, compilação estrita,
formatter/Checkstyle/pacote aprovados;1543 inputs sem drift após AST e prova real. Novo JAR
`977C1A0B6C46AE88C38879E5ECF860F9D6A8F2F30147034EDB6A3B25C54F1CBA`, sem implantação.
O checkpoint0558 registra recibos e limites. Não alegar verify integral ou cobertura global nova.

Reverter a aceitação na factory restaura a restrição anterior. Nenhum perfil operacional foi
editado e nenhum dado SQL foi gravado. Fonte, banco16.0.1000.6, contas, permissões, serviços,
V1/WMS, produção e gates de retenção/completude conservam seus limites.
