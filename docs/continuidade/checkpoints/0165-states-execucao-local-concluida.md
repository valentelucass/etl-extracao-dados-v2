# 0165 — Execução do STATES: correções locais

## Resultado e alcance

**STATES_EXECUCAO_LOCAL_CONCLUIDA**, condicionado ao fechamento M/N pelo selo externo/readback selecionados no FINAL-DELIVERY.json desta rodada. Sem selo/readback PASS, continuar o fechamento nesta mesma execução. A ordem adotada revoga somente a antiga condição de admissão/parada; não cria regra de negócio ou autorização externa.

Base0162/schema102 preservada. Cinco divergências corrigidas em cinco arquivos existentes: decimal Raster, decimal das quatro expansões, escala na admissão, memória/cancelamento por linha dos suplementos e limites da tupla fiscal. Onze famílias, cinco fatos/19SQL e33responsabilidades de preview mantidos. Nenhuma classe de produção nova, migration, alteração de grão/identidade de negócio, fallback de fixture ou apply integral habilitado.

## Evidências e revisão

- full-verify-03: 2115unit/238classes,483IT/103classes, zero falhas/erros;4skips históricos. Identidades e multiplicidades preservam2080unit/481IT anteriores;35unit/2IT novos. Formatter, arquitetura, estática, cobertura e SQLrollback passaram.
- Dois conjuntos completos A2/B24: números JSON e valores alternativos autorados antes de SQL; cinco fatos/19saídas conferidos, contraprovas independentes SQL13 decimal e SQL01 série detectadas em ambos. Tuplas fiscais com separadores conservam raízes/parcelas distintas e série por raiz. Km Raster conferido diretamente no core. Seis processos do JAR do novo pacote extraído: sucesso A/B e recusas de USER ausente, decimal SQL13 errado, série SQL01 errada e alvoLOC inexistente com exits esperados.
- Campanha ARTIFACT distribuída: inspect/plan/run/status/resume/compare PASS. Preview33, rollback confirmado, sem SQL concorrente ou efeito desconhecido pendente no fechamento funcional.
- Cancelamento/deadline/lease, replay/revisão posterior, escopos/frescor/reordenação/duplicatas/páginas, referência/materialização/rollback exercitados nos testes ligados aos critérios. V09916regressões preservadas; V099–V102 não reaplicadas.
- Suplementos2/8/32/128páginas medidos. O primeiro algoritmo limitado levou156570ms para128páginas; filtro fixo128KiB+64chaves/lote reduziu a releitura, com colisão de hash confrontada por igualdade exata. Pior caso adversarial continua limitado/quadrático; não é SLO/platô de heap produtivo.
-646arquivos Java de produção/490de teste;46candidatos revisados. Zero tipo novo, nove remoções e três movimentos anteriores preservados. Bibliotecas puras sem consumidor operacional são identificadas, sem uso artificial. V1 somente por leitura.
- Matriz45/A–N,75regras e2437registros de campos confrontados; classificações opacas/condicionadas preservadas. **39/45 e67/115**, zero aceite nominal novo e nenhuma dupla contagem.
- REDs preservados. support-green-02 falhou ao iniciar JVM, sem teste/SQL; reconciliado e corrigido-Xms64m com teto512MiB. support-green-03 passou42testes. full-verify-01 e full-verify-02 encerrados por revisões superadas com PID/start/rollback reconciliados, sem alegação de PASS ou timeout. Erro de sintaxe do gerador privado corrigido antes de sua execução, snapshot falho preservado. A reserva measurement incorreta da prova física dirigida foi corrigida no WORKLOG; o gate final tem a propriedade e os recibos exigidos.

## Autoridade e recuperação

Rodada: target/execucao-states-20260915-01; progresso detalhado WORKLOG.md.
Catálogo: docs/catalogos/execucao-states/RELATORIO.md e provas.json.
JAR SHA256 fa74c0c0ccbdb7321b7ed2409a57cb500c840532cd98fa7b972faccc0880a9fc.
Runtime ZIP SHA256 6fa145e6815c799cd5721e450faeb240a94ffdac6a35696c5546ae952caa0be5.
Predecessor0162 SHA2562c38ffc74ab401981b521963cbb734c6ba9bcbe03d2c73f71f3b237ae3027b37.
Progresso0164 SHA256d94a6dffd84867a1ac2c97b1c5549ad10d191dd83790926e21cd26f2783ac7ce.

Manifesto novo conserva snapshots da base3465, manifests/pacote/selo anteriores e V099. O selo final deve vincular gates, diff aplicado/revertido/reaplicado, overlay aplicado, scanner integral com índice privado e leitura dos membros ZIP. O ensaio intermediário não é a aplicação da revisão final. As falhas/candidatos anteriores são preservados.

## Continuidade — até três ações

1. Concluir/verificar manifesto, consumidores de sucessão e gates; conferir processos/recibos antes de repetir qualquer efeito.
2. Concluir/verificar diff/overlay final em cópias, scanner integral, pacote/selo/readback e FINAL-DELIVERY.json desta rodada. Não encerrar antes do PASS.
3. Com seleção íntegra, escopo local entregue; preservar somente parcelas externas G01–G08 em ENTRADAS-E-EFEITOS-EXTERNOS.md. Não repetir provas concluídas sem nova causa.

V2-041: sem segredos/.env, API autenticada, V1 executada, produção, serviços/agendamentos, deploy/cutover, commit/push ou índice Git real alterado. SQL somente localhost/ETL_SISTEMA_V2_SHADOW, Windows existente, duas travas Maven e receipt, commit de domínio bloqueado/rollback. Nada aqui comprova COMMIT/crash/restore ou ratificação nominal. Sem subagentes/perguntas.
