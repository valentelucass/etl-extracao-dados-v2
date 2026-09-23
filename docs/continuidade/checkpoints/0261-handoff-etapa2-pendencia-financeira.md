# Checkpoint 0261 — handoff para etapa 2 com pendência financeira

Data: 22/09/2026. Sucede o checkpoint
`docs/continuidade/checkpoints/0260-sonda-financeira-etl-pausado-non2xx.md`
(SHA-256 `bccfd28eeb6c1b55498980bafdddca3317cadbb12fa7baa9562d54fea191ac76`).

## Objetivo e seleção

O usuário solicitou mover a continuidade para outro chat. A seleção vigente,
conferida contra `STATES.md`, `TRILHA_CONCLUSAO_POR_MODELO.md` e
`docs/continuidade/tres-etapas/handoff/ENCAMINHAMENTO.md`, é a **etapa 2
(P16–P29)**. O modelo recomendado é **Terra / High**: é o menor nível indicado
para o trabalho técnico de adequação, paridade e integração que pode surgir nas
parcelas da etapa. Esta passagem não executa o macrobloco nem cria autorização.

P09–P15 esgotaram o trabalho local elegível identificado; seus critérios
externos continuam abertos. A etapa 2 deve conferir primeiro o delta concreto
de código, contrato, evidência ou input recebido e não repetir auditoria, Maven,
qualificação física ou documentação na ausência de delta. A matriz vigente
registra `localWorkStillEligible` vazio.

## Estado financeiro que deve ser preservado

A rodada externa serial de 02/09, autorizada após o usuário pausar o ETL de
produção, usou 8 das 35 chamadas e parou no primeiro `HTTP_NON_2XX`. Coletas
foi terminal; Fretes tinha três páginas válidas não terminais; 4924 e GraphQL
não foram chamados. Não existe relação financeira, receita, CT-e, fatura ou
paridade aceita a partir dessa rodada. A pausa do ETL descarta apenas esse
consumidor conhecido como explicação suficiente; não atribui causa à ESL.

Depois da parada, a sonda financeira passou a publicar somente
`last_http_status` e `processing_stage` no resumo sanitizado. Parser e autoteste
passaram sem rede; não houve nova chamada externa. A recusa anterior não possui
o código HTTP numérico porque a versão que a executou ainda não o registrava.

## Autorização, limites e proibições

O alcance remoto continua estritamente read-only e somente pelos scripts
allowlisted em `AGENTS.md`. Para uma futura condição nova, a sonda financeira
usa apenas Data Export 6908, 6389 e 4924 por `GET_WITH_QUERY` e GraphQL somente
como auditoria de query estática. O teto é `per=100`, até nove páginas por fonte,
35 chamadas, três segundos entre chamadas, timeout de 30 segundos e 10 MiB por
resposta. A rodada é serial, com a trava interprocessos V2, e para no primeiro
não-2xx, `429`, falha de contrato/identidade, limite de entidade não verificável
ou excedido, página não terminal no teto ou orçamento atingido.

Não fazer retry/manual fallback, mudar endpoint/método/transporte, usar outro
IP, FTP, V1, banco, escrita, DDL/DML, agenda, deploy, corte, credenciais ou
processos de produção. O V2 não inicia, para nem reativa o ETL de produção.
Uma nova chamada requer condição externa nova e identificável, ordem prévia e
registro sanitizado; a ordem encerrada não é repetível por rotina.

## Alterações desta unidade

| Arquivo | Alteração | Camada |
| --- | --- | --- |
| `STATES.md` | cabeçalho de handoff com macrobloco, limites e pendência | documentação de estado |
| este checkpoint | fotografia imutável para o próximo chat | continuidade |
| `docs/continuidade/RETOMADA.md` | ponteiro curto para esta retomada | índice de continuidade |

Não houve alteração de Java, SQL, schema, migration, dependência, segredo,
configuração operacional ou fonte remota.

## Validação e retomada

`git diff --check` passou sem erro de whitespace para a alteração documental.
`Test-ContinuidadeAgentes.ps1` repetiu `HANDOFF_PIN` (exit 1); não alterar
manifestos ou ledgers históricos para convertê-lo artificialmente em sucesso.
Os testes do parser, autoteste financeiro e contenção local que suportam a
sonda permanecem a evidência da unidade anterior, não uma nova prova de rede.

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa independente autorizada |
| --- | --- | --- | --- | --- |
| 1 | Conferir o delta e as dependências de P16–P29 | leitura dos arquivos canônicos e evidência pertinente | fatia concreta elegível ou ausência justificada | encerrar sem repetir auditoria |
| 2 | Executar as parcelas locais independentes da etapa 2, se houver delta | autorização e insumos da parcela | testes proporcionais e estado atualizado | registrar o input externo faltante por fatia |
| 3 | Reabrir a sonda financeira somente sob condição nova registrada | condição identificável e ordem serial própria | status/etapa sanitizados ou parada segura | manter a pendência financeira aberta |

O bloqueio remoto é específico da fonte financeira: limite/recusa ESL ainda sem
causa suficiente. A condição de conclusão da etapa não é duração ou percentual;
cada P exige seus critérios e evidências originais. Construção 39/45 e aceites
67/115 permanecem inalterados.
