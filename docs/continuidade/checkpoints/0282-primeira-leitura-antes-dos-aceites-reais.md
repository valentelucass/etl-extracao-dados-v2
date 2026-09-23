# Checkpoint 0282 — próxima leitura antes dos aceites reais — 22/09/2026

## Identificação e objetivo

- Anterior: `0281-smoke-offline-jar-v2.md`, SHA-256 `5d2b10c2a561e015c3c956cae20a16c6b3ba34098c392b7f500b615f3ad30074`.
- Objetivo: identificar o próximo passo material sem cobrar qualificação de dados reais antes de obtê-los.
- Estado: rota inicial definida; execução externa `BLOQUEADO_POR_INPUT` em V2-041/G01.
- Critérios: `AGENTS.md` allowlist e V2-041, `STATES.md` P17–P29, `BLOCOS_ETAPA_2.md`.

## Autorização e limites

- Pedido efetivo: verificar o próximo passo porque a qualificação parece impedir testes sem dados reais.
- Conferência documental apenas; nenhuma autorização adicional para rede, segredo, Java externo, banco ou produção foi inferida.
- A rota futura de Coletas 6908 usa somente a sonda `curl` permitida por `AGENTS.md`, após G01 e reconfirmação de janela/teto/alvo. Java exige autorização própria.
- Nenhum ledger físico, chamada ou orçamento consumido nesta unidade.

## Alterações e decisões

- `STATES.md` explicita o primeiro gate e o momento dos aceites reais; `BLOCOS_ETAPA_2.md` distingue primeira observação 6908 de aceite posterior de Cotações.
- A matriz P07–P33 registra zero trabalho local ainda elegível; P07/P08 e B16 interno conservam provas anteriores. P17–P29 continuam abertos para a campanha real aplicável.
- A rota Cotações 6906 do guia não está na allowlist atual de `AGENTS.md`; suas sondas históricas não autorizam nova chamada.
- Nenhuma fixture ou amostra anterior foi promovida a contrato, paridade ou autorização.

## Execução e evidência

| Passo | Camada | Limite | Esperado | Observado | Evidência |
| --- | --- | --- | --- | --- | --- |
| Reconciliar gates | documental | `AGENTS.md`, `STATES.md`, matriz P07–P33 e guia B16–B29 | distinguir pré-leitura de aceite | somente G01 e escopo da sonda antes da leitura; P17–P29 na campanha | cabeçalho de `STATES.md` |
| Conferir intake | filesystem, presença apenas | caminho sanitizado V2-041 | evidência operacional | ausente | checkpoint 0281; `STATES.md` |

- Testes: não executados porque nenhum código/contrato técnico mudou; os testes locais anteriores permanecem históricos.
- Efeitos possíveis sem confirmação: nenhum. Nenhum segredo, `.env`, ESL, GraphQL, SQL, Raster ou 4924 foi acessado.
- Aceites fechados: nenhum; nenhuma caixa ou contador alterado.

## Retomada imediata

1. Segurança/Operações entrega atestado V2-041/G01 autenticado e sanitizado, com continuidade do writer legado; validar estrutura e origem.
2. Owner da fonte confirma janela fechada, `per`, teto serial e alvo de sombra para 6908; executar sonda allowlisted, parar no primeiro erro e registrar somente perfil sanitizado.
3. Com amostra observada, qualificar identidade/completude e buscar oráculo para P17/P20; para leitura via JAR, obter escopo e autorização separados.

Condição de parada: G01 ausente, janela/teto não confirmados, contrato/limite não verificável ou erro HTTP. Nenhuma qualificação de dado real será marcada antes de evidência executada.
