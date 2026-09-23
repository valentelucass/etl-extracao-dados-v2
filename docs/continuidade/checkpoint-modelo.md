# Modelo de checkpoint — copiar, preencher e preservar a versão anterior

MODELO_NAO_E_EVIDENCIA. Nenhum placeholder concede autorização ou prova execução.
Salvar uma nova revisão a cada unidade coerente. Referenciar o checkpoint anterior
por caminho/hash, sem sobrescrevê-lo. Campos não aplicáveis devem explicar o motivo.

## Identificação e objetivo

- Checkpoint: `<ID crescente; data/hora UTC; bloco ou manutenção>`
- Anterior: `<caminho e SHA-256, ou primeiro checkpoint>`
- Objetivo do usuário: `<resultado final, sem substituir por meta intermediária>`
- Estado da frente: `<vocabulário do protocolo>`
- Prompt adotado / critérios: `<arquivos e IDs exatos>`

## Autorização e limites

- Instrução efetiva: `<data/mensagem do usuário; trecho suficiente; não "autorizado" solto>`
- Ações cobertas / proibidas: `<escopo concreto; separar proposta de adoção>`
- Alvo / contas / revisão: `<referências não secretas; nunca credencial>`
- Vigência / orçamento / ledger: `<teto, gasto conferido, saldo e campanha vigente>`
- Ação pendente de aprovação, se houver: `<pacote pronto e pergunta já feita; não duplicar>`

## Alterações e decisões

- Inventário anterior / alterações preexistentes: `<referência/hash>`
- Arquivos alterados nesta unidade: `<paths e finalidade>`
- Decisão sustentada: `<contrato/ADR/evidência; efeito sobre a próxima ação>`
- Hipótese não comprovada: `<não converter em chave, regra, identidade ou requisito>`
- Abordagem rejeitada: `<motivo e prova; quando faria sentido reavaliar>`

## Execução e evidência

| Passo/critério | Camada | Comando sanitizado e limites | Esperado | Observado | Evidência/hash |
| --- | --- | --- | --- | --- | --- |
| `<ID>` | `<offline/JAR/SQL/fonte real>` | `<sem segredos>` | `<efeito/exit>` | `<resultado ou desconhecido>` | `<arquivo>` |

- Efeitos possíveis sem confirmação: `<ação/ocorrência técnica e leitura para reconciliar>`
- Processo/campanha próprios ainda ativos: `<referência privada; prazo; como observar>`
- Preservação conferida: `<dados/ledgers/migrations/artefatos anteriores>`
- Aceites realmente fechados: `<IDs, critério e prova; nenhum se só preparação>`

## Retomada imediata — até três ações

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa independente autorizada |
| --- | --- | --- | --- | --- |
| 1 | `<ação>` | `<check>` | `<receipt/teste>` | `<ação ou nenhuma>` |

- Bloqueio externo: `<o que falta; origem do requisito; quem pode fornecer; artefato esperado>`
- Condição de parada: `<limite/deriva/risco de integridade>`
- Condição de conclusão: `<todos os critérios; não duração nem percentual>`

Só atualizar RETOMADA depois de salvar e conferir este checkpoint.
