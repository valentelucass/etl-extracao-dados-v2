# Checkpoint 0272 — B16 interno das onze entidades em sombra

- Data: 2026-09-22T19:43:49Z.
- Anterior: `0271-b16-usuarios-aceite-interno-sombra.md`, SHA-256 `722613d9577bc6f63d170e1cdcd5ee9a69fd7c649ff7eb2c5ae6017ab71083ce`.
- Objetivo do usuário: concluir conjuntamente tudo que já seja comprovável nos blocos da etapa 2.
- Estado: linha agregadora B16 concluída apenas como aceites internos de sombra; P16 canônico e B17–B29 mantêm critérios reais abertos.

## Autorização e inventário

A decisão explícita do owner de 22/09/2026 dispensou release oficial somente
como pré-requisito genérico do aceite interno B16. A instrução atual pediu a
execução conjunta das parcelas elegíveis. O inventário encontrou três
subaceites existentes (Cotações, CAP e Usuários) e oito entidades restantes
com implementação local e testes próprios rastreados na matriz de qualificação.
O working tree já estava sujo; as alterações preexistentes foram preservadas.
Alvo: somente testes offline e documentos V2. Nenhuma fonte, credencial, banco,
DDL/DML, deploy, agenda, controle do ETL legado ou cutover foi tocado.

## Execução e evidência

| Passo | Camada | Observado |
| --- | --- | --- |
| Tentativa de oito ITs físicos sem perfil opt-in | guarda local | 72 erros `COL_LAB_OPT_IN_REQUIRED` antes da validação funcional; falha preservada, nenhuma conexão |
| Mappers Manifestos/Coletas/Fretes/Localização | offline/JDK 17 | 3 + 27 + 10 + 10 testes passaram |
| Contrato sintético de expansão FAT/INV/SIN | offline/JDK 17 | `ExpansionLaboratoryContractTest` passou 15 testes no total |
| Raster parser/transporte/artefato | offline/JDK 17 | 18 + 2 + 9 testes passaram |
| Gates Maven da bateria dirigida | offline/JDK 17 | 94 testes; zero falhas, erros e ignorados; Enforcer, Spotless, Checkstyle e BUILD SUCCESS |
| Integridade documental | working tree | `git diff --check` passou; aviso CRLF/LF preexistente em `STATES.md` |

Não houve alteração de código ou schema: nenhum defeito foi demonstrado. A
prova local histórica de 2.263 testes está preservada no guia; a execução
atual confirma apenas os 94 casos listados. O aceite cobre dez sublinhas das
demais entidades mais Cotações já concluída, totalizando onze. A condição
`MANTER` de Raster vale só para a responsabilidade local, não para canal ou
identidade reais. A linha agregadora B16 foi marcada depois do registro em
`STATES.md`; nenhum P17–P29 foi marcado.

## Retomada — até três ações

1. Cotações/P17: fornecedor, owner de tarifas e Negócio entregam release 6906,
   tarifa aprovada, janela/oráculo independente e autorização de fonte/alvo;
   executar caracterização e comparação até aceite ou divergência.
2. Outras entidades/P17–P20: owners de dados/Negócio fornecem oráculos e
   janelas; DBA/Operações fornecem T0/Tcut, fonte histórica e orçamento quando
   aplicável; owners MC/CF fornecem crosswalk/cardinalidade/SLA apenas às
   relações usadas.
3. SQL-10/P25: responsável técnico entrega manifesto interno aprovado; os
   responsáveis das 18 saídas externas entregam seus manifestos/versionamento.

A sonda financeira 4924 permanece em `HTTP_NON_2XX` sem condição externa nova
registrada; não repetir. Não há efeito desconhecido desta unidade. A condição
de parada para a próxima rota real é a falta do artefato ou autorização nominal
acima; fixture, teste sintético ou hash não substitui esse input.
