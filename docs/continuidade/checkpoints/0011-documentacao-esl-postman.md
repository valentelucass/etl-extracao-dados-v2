# Checkpoint 0011 — documentação ESL indicada pelo owner

Registro em 08/09/2026, sucessor do checkpoint 0010. Objetivo B56 preservado:
consultar a referência recebida e resolver lacunas somente com prova pertinente.
Instrução efetiva: adicionar ao STATES o link Postman informado pelo usuário e
consultar a documentação ESL. Escopo: leitura pública e manutenção local.

O link foi registrado em STATES. A coleção contém 191 requisições; o manual
GraphQL indicado por ela contém 180 anchors. Índices sanitizados e limites em
[esl-documentacao](../../catalogos/esl-documentacao/README.md).
As rotas financeiras distinguem fatura/parcelas/itens; há uma consulta de
ocorrências e definições GraphQL pertinentes. Não foi comprovado o vínculo desses
objetos com os quatro templates personalizados. Nenhum aceite novo.

Antes da edição foram inventariados 1.422 arquivos em
`target/esl-documentacao-postman/initial-inventory.json`; cinco revisões anteriores
ficam em `historico/bloco56-raster`. Inventário inicial SHA-256:
`2d80283862f53fb5d89e6310740295df74266c9aadc95e55602803a413973247`.
Os manifests anteriores são imutáveis; o validator Raster passa a conferir sua
revisão histórica e compor somente os cinco deltas verificados desta manutenção.

Leituras: HTML Postman, coleção pública e HTML GraphQL via GET sem credencial,
timeout de 30 segundos, sem redirect. Todas as três aquisições terminaram HTTP
200. O leitor web não extraiu os corpos; os recursos públicos foram lidos pelo
cliente HTTP local. Um auxiliar de busca local lento foi interrompido sem efeito
externo; extração posterior usou buscas limitadas por índice. Índice inicial com
projeção incorreta de dicionários foi preservado no target; a versão final usa
objetos tipados. Erros de sintaxe de auxiliares não executaram efeitos.

Zero chamadas de dados ESL/Raster, SQL, campanha runtime, instalação, grants,
saldo consumido/transferido ou rotação. Nada de commit/push. Não há resultado de
efeito externo desconhecido. Os testes Java B55 continuam históricos.
Logs/checks/diff desta manutenção: `target/esl-documentacao-postman/final/`;
o recibo final só é prova quando presente e seus hashes forem conferidos.

Até três próximas ações:

1. Usar os índices e a documentação principal ao examinar qualquer lacuna ESL;
   distinguir endpoint publicado, exemplo, declaração e observação da conta.
2. Conferir o crosswalk específico de 8636/4924/10633/6392 e os critérios originais
   P08–P11; implementar a vertical local somente após prova integral. Fonte nova
   exige pacote próprio delimitado; não repetir as onze consultas anteriores.
3. Prosseguir na preparação Q-COT-01 com o contrato geral agora referenciado,
   conservando a falta de caracterização/oráculo real e as decisões operacionais.

Recuperação: somente os deltas documentais/de validação próprios, após comparar
com edições posteriores. Não limpar target, restaurar banco ou alterar históricos.
