# Ponto de retomada do projeto

Atualizado em 08/09/2026. **CONTINUIDADE_DOCUMENTADA_B56_NAO_INICIADO**.
Este índice não substitui o STATES, o ledger ou a instrução efetiva do usuário.

## Objetivo vigente

O owner pediu documentação para agentes continuarem corretamente em sessões
longas e após compressão. Também perguntou sobre um próximo bloco amplo capaz
de superar 60%. Está autorizada esta preparação documental; **B56 é PROPOSTO**.
Não foi adotada execução física de B56 nem fornecida nova evidência de identidade.

## Leitura mínima

1. [AGENTS](../../AGENTS.md), [STATES](../../STATES.md), `../CONTEXTO_GLOBAL.md`.
2. [Protocolo](../runbooks/continuidade-agentes.md) e
   [checkpoint 0001](checkpoints/0001-pos-bloco55.md).
3. [Proposta B56](../runbooks/prompt-bloco-56-identidades-e-verticais-condicionais.md).
4. Somente os contratos e critérios da próxima frente efetivamente elegível.

## Base comprovada e preservada

- B55: `LOCAL_A_J_COMPLETE`; cinco verticais pelo JAR protegido no laboratório.
- Roadmap: **65/115, 56,5%; 50 pendências, 193 rotas abertas, zero AGORA**.
- Java 17: 1138 testes, zero falhas/erros, quatro skips preexistentes. Esta é a
  execução B55; manutenção documental não é uma nova execução Maven.
- SQL: V001–V023; catálogo B55 e alvo `localhost/ETL_SISTEMA_V2_SHADOW` nos receipts.
- B55: 259 reservas, 11 campanhas encerradas; 125 unidades não transferidas a B56.
- B54: 302 reservas, 30 campanhas encerradas; 18 unidades não reutilizadas.
- Vigência original: `2026-10-07T22:34:30.615Z`; não renovar por retomada.
- [Manifest B55](../../database/manifest/runtime-bloco55.json),
  [aceites](../../database/manifest/runtime-bloco55-acceptances.json),
  [recibo final privado](../../target/bloco55/completion-receipt.json),
  [runbook B55](../runbooks/v2-022-bloco55-cinco-verticais-local.md).

## Próximas ações — ordem concreta

| Ordem | Ação | Pré-condição e limite |
| --- | --- | --- |
| 1 | Conferir checkpoint, manifests e manutenção documental com o validator abaixo | Somente arquivos locais; arquivo ausente é prova indisponível, não licença para recriar resultado |
| 2 | Confrontar a instrução efetiva de continuação com a proposta B56 | Sem adoção de escopo físico, não executar fonte, banco, grants ou instalação B56 |
| 3 | Triar evidência nova por P08/P09/P10/P11 e preparar a frente que ela realmente liberar | Sem evidência nova, manter hold e entregar a lista exata do input; não repetir a auditoria antiga |

## Impedimentos concretos de B56

| Rota | O que falta | Fonte de verdade |
| --- | --- | --- |
| P09 / 8636 | ID/tipo da raiz e grão raiz–parcela–rateio; parcela não é chave da linha física | `docs/catalogos/identidade-contas-a-pagar/manifesto.json` |
| P10 / 4924 | Título lógico e vínculos fiscais/Frete; ID aceito somente para a linha | `docs/catalogos/identidade-faturas-por-cliente/manifesto.json` |
| P08 / 10633 | Identidade da raiz, papel Frete/minuta e shape/cardinalidade de invoices | `docs/catalogos/identidade-inventario/manifesto.json` |
| P11 / 6392 | Grão da raiz versus composição legada, papéis e cardinalidades | `docs/catalogos/identidade-sinistros/manifesto.json` |

Consultar o pacote versionado ou evidência representativa independente que
responda à lacuna. Uma fixture nova não comprova o fornecedor. Fonte real mantém
V2-041, escopo/janela/teto e autorização próprios; não foi executada nesta manutenção.

## Não repetir nem inferir

- Não executar B55 novamente, reinstalar baseline, reeditar migration aplicada,
  limpar target ou corrigir estado antigo por stale recovery global.
- Não usar 60% como requisito funcional. Cinco aceites existentes adicionais
  dariam 70/115; isso é cálculo condicionado, não entrega aprovada.
- Não converter falhas antigas em PASS: SMOKE_01/02 excluídos; três falsos
  negativos temporais e quatro SQL têm reconciliações independentes próprias.
- Não tratar o tempo disponível como orçamento físico. B56 precisa de pacote
  concreto e adoção dos efeitos que excederem o escopo vigente.
- Não executar fonte real, ETL_SISTEMA, produção, agenda, rotação, commit ou push
  com base no pedido de documentação. Autorizações anteriores exatas não precisam
  ser perguntadas de novo; restrições explícitas continuam valendo.

## Verificação offline e continuidade

```powershell
& scripts/validation/Test-ContinuidadeAgentes.ps1
& scripts/validation/Test-Bloco55Integrated.ps1 -IncludePrivateEvidence -RequireComplete
& scripts/validation/Test-Gpt56ChatTrail.ps1
```

O segundo comando lê evidências locais; não abre SQL nem HTTP. Após esta manutenção
ele distingue a prova histórica B55 do delta documental exato. O
[manifesto da manutenção](manifesto-pos-bloco55.json) não concede direito operacional.

Próximo checkpoint: copiar o [modelo](checkpoint-modelo.md), numerar 0002 e
registrar a instrução/evidência nova. Preservar 0001; atualizar este ponteiro só
depois de conferir o novo arquivo e qualificar a sucessão dos hashes referenciados.
