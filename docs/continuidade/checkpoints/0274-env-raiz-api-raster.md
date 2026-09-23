# Checkpoint 0274 — `.env` único na raiz com API e Raster

- Data: 2026-09-22T20:18:35Z.
- Anterior: `0273-configuracao-local-api-raster.md`, SHA-256 `4731d936eeca0ef4d936ffb19a47069d061e3eda3fc0e85275a5a30e70e70cad`.
- Objetivo do usuário: manter o `.env` do V2 na raiz, com as informações API e Raster enviadas.
- Estado: configuração local concluída; B17–B20 sem aceite novo.

## Autorização e limites

O usuário determinou a localização na raiz. O único arquivo V2 existente
estava em `.codex-local/.env`; `.env` da raiz não existia. Ambos os caminhos
foram resolvidos e verificados dentro do workspace antes do movimento. O V2
segue em sombra. Nenhum banco, serviço Raster, API, deploy, agenda ou cutover
foi chamado. O working tree preexistente foi preservado. O legado não foi
editado. A sonda financeira 4924 continua sem condição externa nova.

## Execução e evidência

| Passo | Camada | Observado |
| --- | --- | --- |
| Mover arquivo privado | filesystem local | `.env` da raiz presente, `.codex-local/.env` ausente; doze chaves únicas, 12/12 iguais às últimas definições não vazias do legado, sem imprimir valores |
| Adaptar quatro sondas e exemplo | PowerShell/offline | scripts procuram `.env` da raiz com fallback legado; parser PowerShell com zero erros; `.env.example` sem segredo |
| Checagem de Git | local | `git check-ignore .env` confirmou ignore; `git diff --check` passou com aviso CRLF/LF de `STATES.md` |
| Scanner antes da adaptação | offline | FAIL, um finding `SENSITIVE_FILE path=.env`; preservado em `STATES.md` |
| Scanner após regra exata | offline | PASS, 20 autotestes; varredura integral com 3.962 candidatos, 3.953 textos e zero findings |

A regra do scanner só aceita `.env` exato da raiz quando simultaneamente
ignorado e não rastreado pelo Git. Os autotestes confirmam que `.env` rastreado
e `.envrc` ignorado seguem como findings. O arquivo contém as quatro chaves
API e oito Raster já presentes no checkpoint anterior; token REST, credenciais
do banco produtivo, frontend e templates adicionais não foram copiados para o
V2. A presença das credenciais não autoriza uso externo, Raster produtivo ou
aceite B17–B20. As credenciais expostas na conversa precisam de rotação pelo
owner antes de novo uso externo.

## Retomada — até três ações

1. Owner da API/segurança: rotacionar as credenciais divulgadas e provisionar
   novos valores locais fora do Git antes de outra chamada externa.
2. Cotações/P17: fornecedor, owner de tarifas e Negócio entregam release 6906,
   tarifa aprovada e janela/oráculo independente para comparação real.
3. Demais B17–B20: owners de dados/Negócio entregam oráculos e janelas;
   DBA/Operações fornecem T0/Tcut e fonte histórica quando aplicável.

Não repetir 4924 sem condição externa nova. Nenhum efeito desconhecido.
