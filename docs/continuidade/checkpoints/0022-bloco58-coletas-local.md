# Checkpoint 0022 — Bloco 58, Coletas testada localmente
Data: 2026-09-09. Objetivo A–D adotado em target/preparacao-bloco58/PROMPT-BLOCO-58.md.
Anterior: 0021-bloco58-inicio-e-reproducao.md, SHA-256 f1b60f0957bc44677fdd6eee4f37f0ced176928f3793e50890c97fdcb72373c2.

Estado: TESTADO_NA_CAMADA para A; B/C/D EM_EXECUCAO. Autorização expressa do
usuário: “Execute o Bloco 58 local”, sem encerrar só Coletas. Alvo é este V2.
Nenhuma aprovação pendente. Somente src/test, correção comprovada e documentação.
Sem API real, SQL, runtime físico, instalação, credencial, agenda, cutover,
commit/push ou consumo/renovação de orçamento. Ledger físico não se aplica.
Limites: bytes65536, linhas1000/caso, páginas100, depth16, paths256, nodes4096;
uma página/lote por vez. GraphQL continuará máximo20.

Inventário anterior e recuperação: target/bloco58-local/initial-inventory.json e
initial/ (1488 arquivos), baseline.json e ACAO.md. Somente diferenças próprias.
Perfis, manifests/receipts B57 e demais fontes históricas não foram alterados.

Correção mínima comprovada: três formatters com espaço do mapper Coletas usam
uuuu e ResolverStyle.STRICT. RED red-temporal-01: três falhas, datas impossíveis
ganhavam frescor; GREEN focused-a-03: três casos passam com bruto preservado e
fallback válido. Sem política DST nova nem alteração de decisão COL/ADR0023.

Consumidor Coletas test-only possui 48 expectativas independentes vinculadas ao
hash da decisão. Compara parser/mapper, first-wave canônico e schema declarativo
sintético de domínio, explicitamente separado do contrato do fornecedor.
Recusa canônica de campo válido no domínio aparece DIVERGED_BEFORE_STAGING, nunca
como sucesso stageado. Contraprova detecta validade indevidamente recusada.
Paginação cobre expansão30+1, curta não terminal, vazio final, caps, cancelamento
e staging falho. Limites de ingresso têm contraprovas antes de materialização.
Nenhuma relação, dedupe, persistência SQL, completude ou snapshot foi provada.

| Execução | Observado | Evidência |
| --- | --- | --- |
| focused-a-01 | falha; quatro expectativas de camada incorretas | log/reports preservados |
| focused-a-02 | exit1, Checkstyle recusou import curinga no teste novo | log preservado |
| focused-a-03 | exit0, 98 testes, zero falhas/erros/skips | result.json, XML e reports |
| contracts-unit-a | seis scripts estáticos exit0 | results.json e seis logs |

Os quatro casos com id ausente/nulo/estruturado são PAGE_LIMIT antes da comparação
contratual, conforme DataExportPageEntityLimitValidator. Tipos escalares inválidos
continuam CONTRACT. Mudou somente a expectativa nova, não o comportamento.
Todos os Maven são offline Java17/heap512, POM equivalente salvo, sem clean/perfis.
Log GREEN SHA-256 5f92e59149855ca266afd0264730b8b2b0df27eab1c985df4557f19e69f667ac.
Result GREEN SHA-256 a5168700e71eb929b316f95ee1c4c81b8b1c5ade6cdd9840b069c2847b6de053.
Nenhum processo ativo ou efeito de resultado desconhecido.

Próximas ações:
1. Usuários: ponte mínima ao parser/gate GraphQL e comparação independente de
   registros stageados; ADR0019 e perfil lidos; prova esperada em testes focados.
2. Regressão C: preservar 109 casos B57 e caminhos LOC; suíte final offline isolada.
3. Fechar D: STATES → trilha → sucessão exata/guards, diff reversível e receipt.

67/115, 48 pendentes,191 rotas abertas; zero aceite novo. Q-COL/Q-USR e V2-012a/b/c
aguardam fonte-oráculo representativa e garantias específicas fornecidas pelos
papéis ESL/domínio/Segurança. Staging simulado não substitui SQL ou oráculo real.
