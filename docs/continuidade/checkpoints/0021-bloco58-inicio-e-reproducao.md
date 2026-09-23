# Checkpoint 0021 — Bloco 58 adotado; reprodução temporal

Data: 2026-09-09. Anterior: 0020-bloco57-complemento-fechamento.md,
SHA-256 b1d511bb3feb45a051c0ce7ea77733812851cf417167adecc2ab5b2464a936b5.
Objetivo: frentes A/B/C/D de target/preparacao-bloco58/PROMPT-BLOCO-58.md.
Usuário: “Execute o Bloco 58 local”, incluindo implementação, correções comprovadas,
testes e continuidade. Estado: EM_EXECUCAO. Nenhuma aprovação adicional pendente.

## Pré-condições, inventário e limites

Leitura obrigatória e critérios V2-010, V2-033, V2-009a, V2-017a,
V2-012a/b/c, DoD e seletor conferidos. Recibo B57 passed=true, 55 hashes
de artefatos e 1.488 hashes atuais íntegros; nenhum delta anterior.
target/bloco58-local/ACAO.md registra ação antes da edição. Inventário/cópias
em initial-inventory.json e initial/; status Git completo preservado.
HEAD preexistente 0b910432f12d81a306072e24aa44885da94c62a1, sem novo commit.
Primeira escrita dos JSONs incluiu sufixo literal inválido; bytes conservados
nos arquivos .first-write e corrigidos antes do consumo. Inventário validado: 1.488.

Zero API real, SQL inclusive leitura, runtime físico, instalação, credencial,
agenda, deploy/cutover, commit/push ou renovação/consumo de orçamento.
Ledger físico não se aplica. Java17/heap512/offline, sem clean canônico.
Limites de teste: 65536 bytes, 1000 linhas/caso, 100 páginas, profundidade16,
256 paths, 4096 nós/envelope; GraphQL20 nodes/página, uma página/lote em voo.
Raízes Q-FND-01/02 e perfis/manifests antigos permanecem congelados.

## Decisões e evidência

Coletas: perfil histórico exige sequence_code/updated_at; domínio permite alias
ausente e não usa updated_at como frescor. São diferenças entre versões/camadas,
não defeito comprovado do fornecedor. Expectativas novas serão aditivas.
COL-02/ADR0023 proíbe reinterpretar data inválida. Três datas impossíveis nos
formatos locais foram aceitas como STATUS_UPDATED_AT, em vez do fallback válido.

| Passo | Camada | Comando | Esperado/observado | Evidência |
| --- | --- | --- | --- | --- |
| Formatação | Java local | Invoke-Maven.ps1 -Label format-a-01 -Goal format | exit0/0 | target/bloco58-local/format-a-01/result.json |
| Reprodução COL-02 | parser/mapper atual | Invoke-Maven.ps1 -Label red-temporal-01 -Tests ColetasTemporalCharacterizationTest | RED/exit1; 3 testes, 3 falhas, 0 erros/skips | target/bloco58-local/red-temporal-01/result.json e maven.log |

Correção mínima aplicada aos três formatadores: ano uuuu e ResolverStyle.STRICT.
Estado da correção: IMPLEMENTADO_NAO_QUALIFICADO; GREEN ainda não executado.
Nenhum resultado Java histórico conta como B58. Nenhum aceite fechado (67/115).
Nenhum efeito desconhecido; processo RED terminou, POM temporário removido com hash.

## Retomada — até três ações

1. Concluir consumidor Coletas, matriz e GREEN ligado ao staging por camada.
2. Implementar consumidor GraphQL Usuários com expectativas independentes.
3. Regressão COT/LOC/FRE/MAN, verify final, sucessão e recibo/diff/recuperação.

Gates reais requerem oráculos/garantias/scopes e adoção específicos; papéis no
prompt/ADRs/runbooks, sem responsável nominal inventado. Continuar trabalho local.
Recuperação apenas dos deltas próprios contra initial; nunca restaurar sobre
edição posterior, apagar falhas ou alterar manifests históricos.
