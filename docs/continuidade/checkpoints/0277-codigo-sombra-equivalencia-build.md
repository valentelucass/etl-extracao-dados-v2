# Checkpoint 0277 — código de sombra por equivalência de bytes

- Data: 22/09/2026 (America/Sao_Paulo).
- Anterior: `0276-qualificacao-tecnica-sombra-validador-manifestos.md`, SHA-256 `c2f6909f9dbc686ca791009941b931b015ecba7e576544e9857c706708afada9`.
- Objetivo do usuário: avançar materialmente depois do `verify` padrão vermelho.
- Estado: código Java e SQL de runtime cobertos pela prova integral anterior,
  por identidade de bytes; delta do validador Manifestos aprovado no shadow.
  `verify` fresco desta tentativa não terminou. P17–P29 sem novo aceite.

## Autorização, efeito e recuperação

A execução ficou em `localhost/ETL_SISTEMA_V2_SHADOW`, Windows integrado,
JDK 17, heap 512 MiB, perfil e trava opt-in; `master` confirmou o alvo.
Teto registrado: 45 minutos. Nenhum serviço ESL/Raster/GraphQL, banco
produtivo, deploy, agenda, credencial ou cutover foi acionado. Ao atingir o
teto, apenas a árvore de processos Maven/Failsafe desta tentativa foi
interrompida. O readback exato das contagens das 246 tabelas igualou a
fotografia inicial, 10.776 linhas agregadas em ambos os lados. Nenhum
processo próprio permaneceu ativo.

## Prova e limites

| Camada | Resultado observado | Evidência |
| --- | --- | --- |
| Maven fresco | 2.263 unitários, zero falha/erro, quatro skips; 91 classes de IT concluídas sem erro antes do teto; JaCoCo final não executado | `target/shadow-build-gate-20260922-01/verify.log` |
| Banco local | 246 tabelas com contagens idênticas antes/depois | `target/shadow-build-gate-20260922-01/counts-{before,after}.log` |
| Bytes atuais | 1.281 arquivos `src/main`/`src/test` e `pom.xml` idênticos à cópia histórica | comparação SHA-256 nesta unidade |
| SQL atual | 172 arquivos comparados; único delta é `042_validate_manifestos_shadow_vertical.sql`, já aprovado contra V022 instalada | `target/shadow-technical-delivery-20260922-01/042_validate_manifestos_shadow_vertical.corrected.log` |
| Prova histórica reaproveitada | `pos0236-p07-verify-01`: exit 0, JaCoCo PASS, 2.263 unitários, 492 ITs, rollback confirmado | `target/macrobloco-qualificacao-pacote-20260913-01/pos0236-p07-verify-01/{result.json,stdout.log}` |

O resultado histórico aplica-se aos bytes Java/POM/SQL de runtime atuais,
pois são idênticos; o delta SQL é um validador read-only e tem prova atual.
Essa equivalência não transforma a tentativa fresca interrompida em PASS,
nem valida dados reais, contratos do fornecedor, tarifas, oráculos, consumidor
ou produção. Não repetir a mesma suíte por continuidade automática: só com
delta causal, orçamento próprio e alcance autorizado.

## Retomada imediata

1. P17 de Cotações: fornecedor entrega release 6906 e semântica de
   paginação/terminal; owner de tarifas entrega referência aprovada.
2. Negócio/responsável de dados entrega janela fechada, oráculo independente,
   tolerâncias e aceite para a entidade.
3. Financeiro/fornecedor fornece condição externa nova antes de qualquer
   eventual sonda 4924; sem isso, a frente financeira fica parada.

Não marcar B17–B29 por prova local. A camada de código de sombra tem prova
por bytes iguais e correção dirigida; a etapa 2 real continua aguardando os
inputs acima.
