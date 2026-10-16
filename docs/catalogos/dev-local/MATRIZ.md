# Matriz DEV LOCAL P01–P33

Escopo SYNTHETIC, sem SQL/fonte/segredo real. **23recortes de fase verificados/10fases excluídas;35unidades com recorte local verificado/13excluídas;48únicas/60ocorrências.** Nenhum novo aceite integral. Status por fase não concede canonicalExit produtivo/nominal.

| Fase | Recorte DEV | Critério local ou causa de exclusão | Prova |
|---|---|---|---|
|P01|VERIFICADO_LOCAL|Ocorrências STARTED/terminais e artefatos retidos reconciliados por readback; replay consulta resultado conhecido e não repete efeito incerto.|BANK_FILESYSTEM_FINAL, CLI_PACKAGED|
|P02|VERIFICADO_LOCAL|Falhas locais demonstradas em replay, deadline, roundtrip JSON e enumeração limitada recebem correção causal e contraprova; tentativas falhas preservadas.|BANK_FILESYSTEM_FINAL, ENGINE_FINAL, CLI_PACKAGED, CI_EXACT_GUARDS|
|P03|VERIFICADO_LOCAL|Composição sintética usa mappers/regras existentes com tipos, filtros, presença, frescor, ledger e outputs integrados; critérios JDBC ficam fora do DEV.|SOURCE_FIXTURE_FINAL, ENGINE_FINAL, CLI_PACKAGED|
|P04|VERIFICADO_LOCAL|Journal STARTED/eventos/outputs/terminal, cancelamento e 33 previews bloqueados; apply somente em responsabilidade explicitamente SYNTHETIC nos cenários fechados.|BANK_FILESYSTEM_FINAL, ENGINE_FINAL, CLI_PACKAGED|
|P05|VERIFICADO_LOCAL|Quatro execuções reais em JVM separada, escalas2/4/8/16, heap observado, bytes, lotes, retenção materializada e duração; SQL/IO não aplicáveis.|CLI_PACKAGED|
|P06|VERIFICADO_LOCAL|Achados de integração e negócio revalidados pelos owners; guards de CI exatos com contraprovas, sem bypass de pacote nem revisão humana alegada.|CI_EXACT_GUARDS, ENGINE_FINAL, BANK_FILESYSTEM_FINAL|
|P07|VERIFICADO_LOCAL|Agregado offline corrente com JDK17, formatter, Checkstyle, arquitetura, cobertura e scanner; XML nativo e cinco skips explicados.|AGGREGATE_CURRENT, SCANNER_WHOLE, SCANNER_SELF58|
|P08|VERIFICADO_LOCAL|Pacote DEV atual com dependências locais, pins de fonte/classes/JAR, comandos reais e leitura/replay dos outputs; não concede despachos SQL históricos.|CLI_PACKAGED, AGGREGATE_CURRENT, SCANNER_WHOLE|
|P09|EXCLUIDO_DEV_LOCAL|Rotação/invalidação/continuidade de credenciais reais exige Segurança/Operações G01; DEV não usa credenciais.||
|P10|EXCLUIDO_DEV_LOCAL|Governança, proteção, publicação e CI remotos exigem provider/remote/owners G02; nenhuma publicação autorizada.||
|P11|EXCLUIDO_DEV_LOCAL|Feed e aceite real de vulnerabilidades não disponíveis nesta unidade sem download/autoridade; política offline preservada.||
|P12|EXCLUIDO_DEV_LOCAL|Ambiente SQL definitivo/TLS/principals/backup/restore e RTO/RPO materiais G05 excluídos; DEV usa arquivos sintéticos locais.||
|P13|VERIFICADO_LOCAL|Onze datasets/contratos sintéticos limitados com mapper/parser real e oráculo independente; vínculos MC/CF declarados e endpoints escopados. A rodada cURL025d é excluída separadamente.|SOURCE_FIXTURE_FINAL, CLI_PACKAGED|
|P14|VERIFICADO_LOCAL|Identidade de ocorrência sintética explicitamente separada das candidatas wire; colisões/tipos/raízes ausentes recusados e ambiguidades nominais preservadas.|SOURCE_FIXTURE_FINAL, ENGINE_FINAL, CLI_PACKAGED|
|P15|VERIFICADO_LOCAL|Release de referência sintético explícito e estável em bootstrap/delta/recomposição; mudança não ratificada recusada, sem seeds nominais.|ENGINE_FINAL, CLI_PACKAGED|
|P16|VERIFICADO_LOCAL|Onze entidades locais percorrem DTO/mapper/parser, regras, quarentena/filtro, presença/frescor e persistência de outputs; nenhum staging/promoção SQL alegado.|SOURCE_FIXTURE_FINAL, ENGINE_FINAL, CLI_PACKAGED|
|P17|VERIFICADO_LOCAL|Caracterização confronta fixture com oráculo independente literal, incluindo valores, tipos, regras, contagens, raiz/expansão e vínculos; divergência é recusada.|SOURCE_FIXTURE_FINAL, CLI_PACKAGED|
|P18|VERIFICADO_LOCAL|BOOTSTRAP separado, estado persistido e INCREMENTAL/RECOMPOSE com --previous da ocorrência lida; watermark de fornecedor não inferido.|ENGINE_FINAL, CLI_PACKAGED|
|P19|VERIFICADO_LOCAL|MC precede CF no consumo dos bindings sintéticos declarados; cardinalidade, raiz ausente/filtrada/quarentenada, conflito e scope inválido recusados, replay observado.|SOURCE_FIXTURE_FINAL, ENGINE_FINAL, CLI_PACKAGED|
|P20|VERIFICADO_LOCAL|Estado sintético de core relido sem alterar valores/hashes passa por delta/recomposição e validações independentes; paridade nominal set-based permanece excluída.|ENGINE_FINAL, CLI_PACKAGED|
|P21|VERIFICADO_LOCAL|Seis famílias de bindings de dimensão identificadas e validadas em outputs locais; conteúdos/rekey/vigência/cadastros e publicação SQL não substituídos.|ENGINE_FINAL, CLI_PACKAGED|
|P22|VERIFICADO_LOCAL|As33 políticas canônicas ficam BLOCKED/apply=false; cenário SYNTHETIC separado exige política/owner/completude e mantém recusas explícitas.|ENGINE_FINAL, CLI_PACKAGED|
|P23|VERIFICADO_LOCAL|CLI scenario percorre ausência e reativação sintéticas com estados/outputs próprios; parcial, quarentena e owner inválido recusados sem mutação indevida.|ENGINE_FINAL, CLI_PACKAGED|
|P24|EXCLUIDO_DEV_LOCAL|As cinco materializações MAT-01..05 dependem de loaders set-based SQL e entradas PUBLISHED. A composição local não as chama; aproximações com semântica/schema diferentes foram rejeitadas. Retém somente metadata SQL_ONLY/computed=false/apply=false, sem linhas fictícias. Código SQL, testes e histórico são preservados.||
|P25|VERIFICADO_LOCAL|Catálogo sintético existente de19contratos carregado e fingerprint/schema/metadata preservados nos outputs; não produz19views SQL nem linhas de fatos inventadas.|ENGINE_FINAL, CLI_PACKAGED|
|P26|EXCLUIDO_DEV_LOCAL|Paridade analítica de fatos/views precisa de saídas SQL e oráculo nominal aprovados. Como materializações reais estão fora deste DEV, nenhum cálculo aproximado pode comprovar essa paridade. A exclusão não impede ingestão/regras/ledger/CLI sintéticos.||
|P27|VERIFICADO_LOCAL|Escalas locais medidas por subprocesso e seis medições sintéticas do harness opt-in; relatório declara retenção integral limitada e distingue heap atual/peaks de RSS/SLO.|CLI_PACKAGED, MEASUREMENT_OPTIN|
|P28|VERIFICADO_LOCAL|Comandos startup/run/status/replay/cancel/error/stop/scenario, lock entre JVMs, crash local conhecido e recovery explícito exercitados; saída terminal e bytes preservados.|BANK_FILESYSTEM_FINAL, ENGINE_FINAL, CLI_PACKAGED, AGGREGATE_CURRENT|
|P29|VERIFICADO_LOCAL|Pacote e configuração DEV congelados, checksums/proveniência local e smoke do launcher no Windows corrente; RC, SBOM/CI/SO e Segurança produtivos continuam em trilha própria.|CLI_PACKAGED, AGGREGATE_CURRENT, SCANNER_WHOLE|
|P30|EXCLUIDO_DEV_LOCAL|Revisão nominal CUTOVER-DB-01/Tcut/writer/fences reais exige topologia e owners produtivos; não necessária ao DEV sintético.||
|P31|EXCLUIDO_DEV_LOCAL|Ensaio material de cutover/restore/RTO/RPO G06 não autorizado; DEV não troca writer nem banco.||
|P32|EXCLUIDO_DEV_LOCAL|Corte/publicação produtiva G07 expressamente fora deste pedido.||
|P33|EXCLUIDO_DEV_LOCAL|Retirada do legado/consumidores/credenciais/jobs G08 expressamente fora deste pedido; nenhum código histórico removido.||

Cada exclusão, seu impacto e trilha futura, unidades deduplicadas, facets físicas restantes e pins atuais estão em [matriz-p01-p33.json](matriz-p01-p33.json). Os IDs de prova referem-se à seção proofs nesse arquivo; os artefatos em target são preservados, não publicados como dados reais.

Validator: `pwsh -NoProfile -File scripts/validation/Test-DevLocalMatrix.ps1`. Ele recompõe33fases/48unidades/60ocorrências da fonte canônica, confere status, vínculos, provas CLI e hashes atuais. Não autoriza nenhum efeito físico.
