# Checkpoint 0273 — cURL 6906 e configuração privada API/Raster

- Data: 2026-09-22T20:09:19Z.
- Anterior: `0272-b16-cobertura-interna-onze-entidades.md`, SHA-256 `adf2998daf4358965815ea49ea7cbfae718c7a93805a042f221e7c3579311eb9`.
- Objetivo do usuário: avançar os blocos B17–B20 com cURL no escopo autorizado e preparar a configuração local da API e do Raster no V2.
- Estado: configuração local concluída; B17–B20 sem novo aceite real.

## Autorização e limites

O usuário pediu cURL para investigar a fonte, forneceu as variáveis locais e
depois pediu incluir Raster. A ordem cURL foi registrada previamente no
`STATES.md`: somente 6906, uma janela fechada, `/info` e página 1, até duas
chamadas seriais, sem retry. A configuração foi preparada sem efetuar chamada
Raster, banco, deploy ou mudança de credencial. O V2 continua em sombra. O
working tree já tinha muitas alterações; foram preservadas. A sonda financeira
4924 não foi repetida, pois não há condição externa nova registrada.

## Execução e evidência

| Passo | Camada | Observado |
| --- | --- | --- |
| Sonda 6906: `/info` + página 1 | fonte real/read-only | HTTP 200 em duas chamadas; 37 campos, seis filtros; 101 linhas físicas, 100 `sequence_code` distintos válidos; parada por teto não terminal, sem página 2 ou retry |
| Configuração local | arquivo privado | `.codex-local/.env` ignorado pelo Git, doze chaves únicas, 12/12 coincidem com a última definição não vazia do legado; nenhum valor impresso |
| Exemplo versionável | offline | `.env.example` com quatro entradas de API e oito de Raster, sem valores sensíveis |
| Sondas | offline | quatro scripts preferem `.codex-local/.env` e mantêm fallback legado; parser PowerShell aceitou os quatro; autotestes 6906 e financeiro passaram sem rede |
| Scanner | offline | primeiro finding `SENSITIVE_FILE` para `.env` na raiz; arquivo privado movido para `.codex-local/.env`; repetição passou com 3.961 candidatos, 3.952 textos e zero findings |
| Integridade | working tree | `git diff --check` passou com aviso CRLF/LF de `STATES.md`; `.env` da raiz ausente; `git check-ignore` cobre o arquivo privado |

O teste isolado ad hoc do resolver de ambiente não foi conclusivo porque
`$PSScriptRoot` estava vazio no harness, sem efeito de rede ou arquivo.
`STATES.md` preserva essa falha e o finding inicial do scanner. A configuração
não habilita execução Raster nem constitui contrato de fornecedor, tarifa,
oráculo, completude ou paridade. Nenhuma caixa B17–B20 foi marcada. As
credenciais copiadas foram expostas na conversa; o owner deve rotacioná-las
antes de novo uso externo. O V2 não altera credenciais.

## Retomada — até três ações

1. Owner da API/segurança: rotacionar as credenciais divulgadas na conversa e
   provisionar os valores novos fora do Git, antes de outra chamada externa.
2. Cotações/P17: fornecedor, owner de tarifas e Negócio entregam release 6906,
   tarifa aprovada e janela/oráculo independente; caracterizar e comparar no
   orçamento autorizado, registrando completude ou divergência.
3. Demais B17–B20: owners de dados/Negócio fornecem oráculos e janelas;
   DBA/Operações fornecem T0/Tcut e fonte histórica quando aplicável; owners
   MC/CF fornecem crosswalk e cardinalidade para relações usadas.

Sem novo input externo, não repetir 4924 nem inferir aceite de fonte a partir
de amostra ou configuração. Não há efeito desconhecido desta unidade.
