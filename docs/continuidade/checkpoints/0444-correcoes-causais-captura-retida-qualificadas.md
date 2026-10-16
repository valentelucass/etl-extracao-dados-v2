# Checkpoint0444 — correções causais da captura retida qualificadas

## Identificação e objetivo

- Data:05/10/2026. Estado: TESTADO_NA_CAMADA_OFFLINE; qualificação agregada final pendente.
- Anterior:0443, SHA256 `C9344CC28154FB439E316FF1A3802CAAF92428BFBAE5F95AD07B5ED85C832345`, preservado.
- Objetivo autorizado: preparar shadow e extrair onze entidades para testes, com retenção. Primeiro marco: Coletas parcial em ctl/stg, commit conhecido e readback independente.
- Autoridade: usuário/AGENTS/STATES/ADR0060, `USER_20261005_E3_TEST_RETAINED`. Pin E3 manual exclusivo; SQL16.0.1000.6, contas/permissões/serviço/listeners/trust global/produção preservados.

## Critério e escopo

Unidade `target/test-database-all-entities-20261005/`. Banco é único executor SQL; Runtime qualifica pacote/controladores; Fontes cuida de transporte; Root documentos/CI. Nenhum despacho ou efeito SQL/HTTP desta unidade até o checkpoint. Preflight de versão/hash deve ser atual em cada operação.

Diagnóstico futuro:1conexão/10SELECTs planejados/cap12/10s/deadline60s. Captura distinta:1conexão/até1200operaçõesSQL/10s/60s, Coletas6908 página1/per1/até1000linhas/2HTTP/30s/10MiB, sem retry/redirect/fallback, intervalo mínimo2s. Readback distinto após fechamento:1conexão/5SELECT/10s/30s. Não declarar sucesso integral, DQ, core/pub, terminalidade ou cutover.

## Resultado causal

| Evidência | Camada | Resultado |
| --- | --- | --- |
| Agregado01 | Offline | FAIL9arquivos no formatter, antes de compilar; preservado |
| Agregado02 | Offline | SF2655/2falhas/0erros/5skips, FS0; duas regras de arquitetura; preservado |
| Banco causal04 | JDBC fake/TLS offline |46testes PASS, overflow11colunas e17ºhandle/reuso cobertos |
| Banco rename05 | AST/formatter/checkstyle | Equivalência normalizando somente nomes do método/parâmetro; sem repetição46testes |
| Runtime architecture04 | Offline |17Coletas PASS;3falhas de reconhecimento nas19Architecture, preservadas |
| Runtime architecture05 | Offline |19Architecture PASS após nomes corrigidos; nenhum regex relaxado |
| Pacing | Governor/ticker sintético |2s entre tentativas e terceira chamada recusada; sem rede/espera real |
| Controladores | Offline | Catalogal42checks; captura17estruturais+13política de fechamento |
| Readback independente | Offline |87Java+20provider/IPC+14POST/close+5guards finais PASS |
| Scanner | Offline |42contraprovas e scanner global4288candidatos/0achados PASS |

Root conferiu111membros do freezeBanco04 SHA256 `416bed9edaf2ef70ba0e91516c70908567fe3128a6dd238ed643b077a71dfb06`, semdrift. Architecture final `fc95b1d26b9efc35a227fb5d6d0e18af560f705e2055b605a7dd1d377d799b51`. CI mixed-class-scope `cbb612e43472ec5f564b980aaa2e257acf92224b4d56188f12c9c6718cdc9f54`: quatro classes UNIT_INCLUDED, somente hash Source atualizado para pacing; sem alterar limiares/exclusões. Agregado03 ainda precisa passar antes de qualquer SQL; helpers físicos serão congelados contra seu JAR final.

As exceções arquiteturais são exatas: array de emissores0, constantes8calls/12tabelas e registry16handles. SQLException é cadeia de erro, mas List<SQLException> e Iterable<Throwable> continuam coleções detectadas. Os testes com campos homônimos fora da classe autorizada continuam recusados. Readback valida teto de colunas antes da alocação.

## Demais entidades

Raster conserva34testes e freeze final anterior. DE observacional02 passou54testes; Root44membros semdrift, manifesto `79f09f07c11009e076214426ba5ad9639f0963220476be8048a58cc6342c22a5`; intervalo2s aplicado e01 preservado. Users first1 tem10testes iniciais e qualificação final em andamento. Essas dez classes/testes novos ficam fora do snapshot específico Coletas, sem exclusão de diretórios ou afirmação de qualificação da árvore inteira.

Sete templates MAN/COT/LOC/CAP/FAT/INV/SIN conservam INFO-only para a próxima unidade: a documentação primária recuperada define per para listar templates, não para as linhas/raízes em /data. A pergunta ao usuário busca outra referência, não aprovação genérica repetida. FRE/Users/Raster continuam frentes independentes, sujeitas a destino/transação próprios. Os pais SQL de expansão/Raster existentes são synthetic-only; nenhuma ausência instalada ou necessidade de DDL foi provada, nenhuma migration aplicada.

## Próximas ações

1. Concluir agregado03, revisar JAR/controlador final e emitir despacho novo para Banco executar o diagnóstico catalogal único.
2. Após contrato físico/proteção, executar captura Coletas, fechar recursos e despachar a segunda conexão de readback; resultado incerto exige reconciliação, nunca replay ou purge.
3. Concluir qualificação das fontes restantes e seus destinos elegíveis; obter referência per para as sete fontes ainda sem limite remoto comprovado.

Falhas/ledgers/manifests anteriores são imutáveis. Nenhum checkbox ou aceite físico novo. CONTEXTO_GLOBAL continua ausente, não recriado; lacuna histórica HANDOFF_PATH permanece sem fabricar recibo. V1 deixado parado pelo usuário não recebeu ação PM2.
