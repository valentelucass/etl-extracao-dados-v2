# Auditoria e correções locais da construção

**AUDITORIA_E_CORRECOES_LOCAIS_CONCLUIDAS**, restrito ao escopo local A–N e condicionado à existência/validade de `target/macrobloco-fechamento-construcao-20260913-01/final-seal.json`. Construção **39/45 →39/45**; aceites históricos **67/115**, sem mudança da medida ou novo aceite. Isso não significa que todo o código ou a operação real estejam prontos.

## Mudança funcional e provas

Foram corrigidos dois defeitos de retomada em `QualificationSupervisor`:

1. O controlador reconhecia proprietário anterior ainda vivo e admitia um segundo caso. Agora verifica a cadeia e todos os proprietários pendentes antes de nova admissão; reserva sem evidência suficiente também mantém a admissão fechada. A reprodução demonstrou a nova reserva indevida, não corrupção de dados observada.
2. A interrupção após `EVIDENCE` ou `ROLLBACK` deixava um resultado selado indefinidamente desconhecido. Agora a retomada acrescenta somente os eventos finais faltantes a partir do selo íntegro, sem repetir worker. Reconciliação sem selo, bytes alterados ou exit não observado não viram sucesso.

REDs preservados: `resume-admission-red-01` (1IT/1falha) e `resume-sealed-red-01` (5IT/3falhas); ambos com rollback confirmado. GREEN:16IT, sendo6novas e10de composição anterior, sem falha/erro/skip. O pacote final exerceu os prefixos EVIDENCE/ROLLBACK e as recusas de selo ausente/log alterado em4casos e12comandos reais do JAR extraído. Não se alega crash de processo/COMMIT material por essas cópias controladas de journal.

## Verificação da revisão

| Prova atual | Resultado |
|---|---|
| `verify-final-01` offline, Java17, perfil físico, heap512MiB |1976unitários com4skips históricos;423IT/81classes/0falha/0erro/0skip;coverage e rollback aprovados |
| Identidades de teste |417IT anteriores preservadas exatamente+6novas; gates line0,80/branch0,60 inalterados |
| Artefato/rebuild | Dois ZIP/JAR byte-idênticos;1997inputs e1015classes/recursos conferidos; mutante de fonte recusado |
| Conteúdo do pacote |172arquivos declarados+2arquivos de manifesto=174entradas ZIP;9dependências, sem assinatura/CI/publicação nominal |
| Smokes atuais |5cenários/30comandos: exemplo empacotado, admissão, ondas, cancelamento BEFORE_SQL e perda de recibo BEFORE_RECEIPT |
| Recusas atuais |21entradas inválidas extraídas e8adulterações de controle, mais4casos específicos de retomada |
| Histórico reutilizado |17smokes,escalas4/16/32/16 da entrega0141 continuam evidência daquela revisão. O worker/captura/queries medidos não mudaram; isso não prova o novo comportamento de resume |
| Fechamento documental/segurança | Scanner/contraprovas,25guards de envelope atuais, runtime, sucessão/CheckGuidance e auditoria final devem constar aprovados no selo desta revisão |

Os quatro skips atuais, iguais aos históricos:

- `br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationDeterminismTest/refusesAReparsePointInsideTargetWhenThePlatformSupportsLinks`: org.opentest4j.TestAbortedException: Assumption failed: O filesystem não permite symlink de teste.
- `br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02DeterminismTest/refusesSymbolicLinkInsideReceiptRoot`: org.opentest4j.TestAbortedException: Assumption failed: Filesystem sem symlink para esta prova.
- `br.com.esl.etl.v2.contratos.mapping.CotacoesSourceCommandTest/command`: System property [bloco57.cotacoes.command] does not exist
- `br.com.esl.etl.v2.contratos.medicao.MeasurementReceiptWriterTest/refusesASymbolicLinkInsideTheMeasurementRootWhenSupported`: org.opentest4j.TestAbortedException: Assumption failed: Filesystem sem symlink para esta prova.

Nenhuma DDL/migration, COMMIT de domínio, coleta real, mudança remota, produção, serviço, agenda, grant/restore, V1/dashboard ou feed/NVD foi executado. As provas físicas usam exclusivamente localhost/ETL_SISTEMA_V2_SHADOW, Windows integrado e as duas travas. Agregados iguais e rollback não provam COMMIT/crash/restore durável, RTO/RPO ou paridade real.

## Auditoria das45unidades e portabilidade

As45unidades conservam as seções originais e seus checkboxes, consumidores e casos com hashes, subescopo local, falta concreta e gate. A profundidade nova das unidades já contadas não soma unidade. V2-016/017 não são contadas por documentos, V2-041 por parser, V2-048 por simulação, nem V2-014/040 por planejamento.

Foram conferidos401artefatos,2437linhas de campos e75regras contra o catálogo regenerado. Classificação das linhas de auditoria, sem usar esses totais como progresso funcional:

- IMPLEMENTADO_INTEGRADO_VERIFICADO_LOCAL: 786
- MAPEAMENTO_LOCAL_DECLARADO: 361
- VINCULO_INDIVIDUAL_CONDICIONADO: 595
- OPACO_PRESERVADO_BLOQUEADO: 442
- TRANSITORIO_COM_SAIDA_GOVERNADA: 19
- RETIRADA_PRESERVADA: 20
- RASTREADO_POR_EXPRESSAO_SQL_LOCAL: 214

Os595vínculos individuais condicionados têm o path/grão/destino original e a falta registrada. Fatos têm mecanismos substitutos próprios; não se associa um fato a uma view de fonte apenas pelo nome da coluna. As442linhas /info continuam opacas. Retiradas são preservadas. As673colunas pertencem às19saídas;971são entradas do oráculo de metadados físicos, e35escopos são11entradas+5fatos+19contratos. Seis dimensões são dependências; esses números não equivalem à cobertura integral do legado.

Permanecem partes funcionais condicionadas, além de dados/configuração: composição operacional das quatro expansões e Raster; adaptação/binding da paridade real por entidade/saída; habilitação de sweep por responsabilidade/filho; integração material de runtime/retenção/recuperação no alvo aprovado. Os mecanismos de laboratório existentes e os gaps nominais são separados em [composição](COMPOSICAO-E-LIMITES.md) e [matriz45](MATRIZ-45-UNIDADES.md). Não há lacuna local demonstrada pela presente auditoria deixada com teste obrigatório vermelho.

## Gates externos e continuidade

G01–G08 permanecem abertos: rotação autenticada, governança de repositório, contratos/identidades/oráculos, decisões de negócio/consumidores, ambiente e recuperação material, ensaio, corte real e retirada. O [plano concreto](ENTRADAS-E-EFEITOS-EXTERNOS.md) informa entradas, papéis, alvos faltantes, efeitos, provas negativas, abort e recuperação. Estado material: **PLANO_PRONTO_ENSAIO_NAO_EXECUTADO**, CUTOVER-DB-01/DATABASE_WIDE, banco V2 dedicado e duas recuperações. Depois do PNR real, somente roll-forward V2.

Baseline:3158arquivos e195pins conferidos. Manifests históricos não foram regravados. O último guard da primeira tentativa de baseline recusou o arquivo de teste criado enquanto o check corria; a sucessão exata corrigiu o vínculo e os7checks candidatos passaram. O validador próprio detectou a omissão do critério MAT-02 pelo filtro de formatação; o autor passou a conservar as seções integrais, e a falha foi preservada. A primeira tentativa da prova adicional extraída falhou na construção numérica do nome de log, antes de iniciar processo/SQL. Foi reconciliada, preservada e corrigida; resume-package-02 executa a prova em diretório novo. Revisão do agente não equivale à revisão humana.

Pacote: `target/macrobloco-fechamento-construcao-20260913-01/qualification-final-01/qualification.zip`, SHA256 **b1ae469e21a08937592496a72a252173af6a7d98e935b4c79c63135328364403**.
Manifesto do pacote: **8aa50f6704007da5b3c1264d6a4269a89918a89567b7e71cec50990a25478c52**. Revisão de fontes: **bad77e34686dda2cded2fe2860ea7249d3bd115b32a0dc81e910aff99a410038**.

Artefatos: [A–N](MATRIZ-A-N.md), [45unidades](matriz-45-unidades.json), [resumo](verification-summary.json), [comandos](COMANDOS.md), [revisão](REVISAO.md). Diffs completos/revisão, inventários e selo estão na rodada. A entrega exige todos os checks finais do selo aprovados; sua ausência exige continuar o fechamento, sem nova entrada do usuário.

## Correções da conferência final de arquivos

O scanner recusou a matriz de campos de 6.756.614 bytes pelo teto existente de 5 MiB. A matriz agora é um índice com três partes e hashes, preservando exatamente as 2.437 linhas e todos os campos originais. O teto, as regras e as contraprovas do scanner permanecem iguais. O validador confere as partes e reconcilia os mesmos dados contra o CSV original. A recusa e os bytes anteriores estão preservados na rodada.

O Git da prova de aplicação não acessou um caminho longo nas cópias privadas; a execução passou a usar core.longpaths=true somente no comando. O diff falho e o exit da recusa permanecem em diff-failed-01. A entrega exige diff completo aplicado em cópia nova da baseline e igualdade dos bytes finais. Erros de contrato/formato nos scripts privados foram corrigidos após reconciliação: os resultados dos guards existentes usam PASS_LOCAL e recusas exit 2; o runner foi normalizado para uma lista mesmo quando há somente um check. Não houve efeito Java/SQL repetido por essas correções.
