# 0424 — SAMPLE qualificado offline; prova real pendente

Data02/10/2026. Anterior: [0423](0423-sample-gate-integral-fail-causas-offline.md), SHA `8E4502E7CEC0CC9C34CE636979C2696ACC882238EDC836DC0E5EB8C25ADCB7BC`.

## Objetivo, autoridade e estado

Objetivo efetivo do usuário: tentar uma extração completa com poucos dados usando agentes Maestri, com curl para diagnóstico. **CONCLUÍDO_LOCALMENTE somente para implementação e qualificação offline do SAMPLE. Prova real ponta a ponta continua BLOQUEADO_POR_INPUT.** Não foi extraído/carregado dado real pelo ETL/JDBC nesta frente.

Escopo/owners em `target/pilot-etl-20261002/supervisor-scope.json`: Coletas6908 provisionado, janela recente delegada, shadow exato `localhost/ETL_SISTEMA_V2_SHADOW`; Banco único executor SQL, Runtime CLI/bootstrap/testes, Fontes adaptadores/contratos, Regras domínio, Codex documentos/CI/integração. Sem produção, DDL/Flyway, login/grants/serviço/TLS/credenciais, agendamento ou cutover.

Teto original10 HTTP; três reservadas (Fontes2/curl1), duas tentativas efetivas. Saldo teórico7 congelado depois de HTTP429; cap SAMPLE2 não cria rodada/vigência/autorização novas. Zero HTTP/SQL real nos gates offline. Não testar liberação da quota com outra chamada.

## Implementação, correções e preservação

CLI SAMPLE com nove argumentos, três opt-ins/flags JVM sem retry interno, preflight Banco fresco vinculado a três arquivos/binding. Composição operacional mantém guard/mapper/staging existentes, uma página populada≤5 entidades/1000 linhas; expansão usa batches≤100. Readback Banco compara binding real, página auditada, conjuntos/multiplicidade e batch-página em uma sessão/transação. Recibo positivo só após `close`/rollback; sem página vazia fictícia, terminalidade, permit, promoção, watermark, sweep ou completude de janela/filhos. ADR0056/COL-SAMPLE-01 e catálogo CI integram a decisão; catálogo02 idêntico ao atual, sem nova alteração.

FAIL01 e causalFAIL preservados. A causa do parse NOTE foi demonstrada por comparação real do pacote: JDK_JAVA_OPTIONS vazia imprime nota JVM e JSON estrito recusa; remoção real preserva JSON. Launcher privado corrigido por remoção de variáveis ausentes, sem afrouxar parser. Três novos testes SAMPLE cobrem recusas antes de JDBC, CLI/preflight/JSON sanitizado e extração sem fronteira; teste adicional de pacote cobre a comparação ambiental. Seis Java de produção são idênticos ao freeze01; Main padrão/POM, três bytes Banco e limites/exclusões de cobertura preservados. Failsafe causal direto com pacote explodido falhou; preparação do JAR real resolveu o binding, sem relaxá-lo.

Runtime02 iniciou uma suíte completa nova justificada pelas correções, não repetição idêntica. Codex congelou Java/docs/CI/grafo durante a suíte. Após aviso de término, conferiu os recibos e **4210 inputs root/snapshot, zero drift**, antes de atualizar documentos/grafo. Não repinar manifests históricos para incluir mudanças atuais posteriores ao gate. Readback PS7.6.6 superou falha de resolução de caminho longo do shell PS5; duas tentativas de ferramenta falhas preservadas no histórico do turno, sem mudanças de produto.

## Execução e evidência

Prefixo privado: `target/pilot-etl-20261002/`.

| Critério/camada | Observado | Evidência |
| --- | --- | --- |
| Maven completo offline | `mvn --offline clean verify`, exit0/BUILD SUCCESS | `runtime/final-build-02`, `runtime/final-clean-verify-02.log`, gate02 abaixo. |
| Surefire/Failsafe | 2456/0/0/5 e 7/0/0/0 | XMLs completos no snapshot02; SAMPLE15 e trial Banco21 PASS. Codex recalculou os totais. |
| Qualidade | 18 gates PASS | Enforcer, Spotless, Checkstyle, warnings-as-errors, JaCoCo original, CI scope, integridade pacote, ausência drift e demais checks no quality02. |
| Freeze e inputs | Runtime8/Banco3 matching; 4210 arquivos root/snapshot matching | Manifest02, owned-freeze02, quality02 e readback independente Codex. Inventory0 no fechamento Runtime. |
| Scanner auxiliar | 4211 candidatos/4210 textos/1binário, zero achados | `runtime/final-root-secret-scan-02.log`; não substitui Gitleaks/histórico. |
| Integração de docs | Trilha/UTF-8/diff PASS | `supervisor-trilha-0424.txt`; contadores canônicos intactos. |
| Graphify AST | exit0; 38969 nós/92624 arestas/2205 comunidades | `supervisor-graph-update-02.txt`; sem LLM/API. Rótulos históricos possíveis. |

Hashes de evidência (integridade, não aprovação externa):

- Gate02 `runtime/final-gate-receipt-02.json`: `595D327DACAD160ADCD36A05BAB3AA5C978D27451F129AEEBF1C6BFF80AA35AC`.
- Quality02 `runtime/final-quality-receipt-02.json`: `4408C88EE804543691FBA710D6BE72F086B424D52FBD1C4FB94992DE006A013C`.
- Manifest02 `runtime/final-input-manifest-02.json`: `B611BF09C97188692ED7EADC067BB3B9504B458E41416052348369C7CB880E32`.
- Runtime freeze8 `runtime/owned-freeze-02.json`: `CBFA2380BFD10FB8F635FE920D2B8B7D48144DA32CCB09F8F4F8997ED14592AB`.
- Readback Codex `supervisor-final-review-02.json`: `E8137F73915F19BF7EA0999DF3F6AEB9B121C332A6B99C538853AC751E1C75F7`.

Cinco skips: três testes de links/reparse condicionais à plataforma, comando Cotacoes opt-in ausente, escritor de medições governadas não habilitado. Não inferir sucesso dessas operações. Nenhum aceite físico/P08/G01/paridade/cutover, checkbox ou contador histórico fechado. Fonte Java original SOURCE_UNAVAILABLE não reteve causa; HTTP429 posterior é evidência independente, não atribuição retroativa. Read-only sa/loopback anterior não satisfaz JDBC Windows/listeners/TLS/contrato físico. CONTEXTO_GLOBAL ausente e HANDOFF_PATH histórico aberto, sem recibo fabricado.

## Retomada — até três ações

1. Owner da origem comprova mudança concreta da quota/bloqueio429 antes de qualquer nova chamada. Não renovar teto/saldo nem sondar para descobrir se liberou.
2. Banco qualifica sessão Windows, listeners loopback, TLS JDBC e objetos SQL usados, com autoridade/gates/reserva próprios. Não alterar login/serviço/schema automaticamente nem inferir prontidão por acesso sa diagnóstico.
3. Somente com esses inputs, coordenar trial SAMPLE real pequeno com guard/mapper/staging/readback/rollback, reserva e PRE/POST independentes, incluindo impacto IDENTITY. Receber prova física antes de declarar objetivo concluído.

Nenhum novo Maven é necessário sem mudança/falha que o justifique. Runtime encerrou por handoff, Codex encerra a integração após validadores; nenhum processo próprio ou terminal em espera/polling. Condição final do objetivo: uma página real processada e staging/readback/rollback provados fisicamente nos limites. Qualificação offline concluída não satisfaz esse critério.
