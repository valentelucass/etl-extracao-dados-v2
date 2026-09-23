# Checkpoint 0246 — sonda Data Export em sombra interrompida

- Checkpoint: 0246; 22/09/2026 15:19 UTC; validação externa controlada.
- Anterior: `docs/continuidade/checkpoints/0245-encaminhamento-etapas-sem-retorno-automatico.md`, SHA-256 `1122d0e474e053b4b4ce219c0499ccf4b6fd342ddfc1dcf92e811fc419cd3c91`.
- Objetivo do usuário: testar as fontes permitidas em modo sombra, sem escrita.
- Estado da frente: `BLOQUEADO_POR_INPUT`; o pedido de dados recebeu HTTP 422 e
  a regra de parada impede novas chamadas nesta rodada.
- Critérios: `AGENTS.md`, contrato das três sondas versionadas e P13/P14 da
  matriz vigente.

## Autorização e limites

- Instrução efetiva: usuário, 22/09/2026, “pode ja testar tudo sim”.
- Coberto: consultas estritamente read-only para 6908, 6389 e 4924 e GraphQL
  estático permitido; proibidos escrita, banco produtivo, DDL/DML, agenda,
  deploy e cutover.
- Alvo: fonte Data Export/GraphQL configurada fora do Git; nenhuma credencial,
  URL, payload, cursor ou identificador foi registrado.
- Ordem: janela fechada 2026-01-02, teto agregado 31 chamadas. A sonda de
  contrato tinha teto 7 e o consumiu integralmente; não há campanha ativa nem
  saldo reutilizável.

## Execução e evidência

| Passo/critério | Camada | Limite | Observado | Evidência |
| --- | --- | --- | --- | --- |
| Perfil/paginação 6908/6389 | fonte real, somente leitura | 7 chamadas, 30 s, 10 MiB | parada `HTTP_NON_2XX`; metadado 6908 HTTP 200 e pedido de dados HTTP 422 | `docs/continuidade/probes/2026-09-22-dataexport-shadow-stop.md`, SHA-256 `cf3ef830adb44ce3ad978c13c80deb4f12b09944b1e7c80a46c9add7f91f2030` |

A tentativa inicial de passar a lista de templates falhou localmente antes de
`curl.exe`, sem efeito externo. A execução corrigida não permitiu verificar
entidades ou paginação. Por parada obrigatória, identidade GraphQL e financeiro
4924 não foram chamados. Não houve aceite, alteração de construção ou efeito
em banco, produção, pacote, deploy ou corte.

## Retomada imediata

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa independente |
| --- | --- | --- | --- | --- |
| 1 | Diagnosticar a causa da recusa 422 sem repetir a fonte. | Evidência sanitizada e contrato local. | Correção ou decisão de parâmetro autorizada. | Nenhuma chamada externa. |
| 2 | Executar nova rodada serial somente se houver ordem própria. | Causa resolvida e novo teto registrado. | Recibo sanitizado de contrato. | Continuar trabalho local independente. |

- Bloqueio: pedido de dados recusado com HTTP 422; a origem técnica precisa
  fornecer o formato/filtro aceito ou autorizar a correção contratual.
- Condição de parada: novo HTTP não-2xx, 429, contrato inválido, limite não
  verificável ou teto atingido.
