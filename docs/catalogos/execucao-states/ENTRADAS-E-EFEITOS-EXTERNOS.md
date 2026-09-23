# G01–G08: parcelas externas preservadas

Nenhuma entrada externa autenticada foi recebida nesta rodada. As correções e provas
com arquivos sintéticos verificam o alcance local; não ratificam uma origem produtiva. Os mesmos
**39/45** de construção e **67/115** de aceites históricos permanecem, sem reclassificação.

Este quadro complementa o [catálogo nominal vigente](../macrobloco-integracao-funcional/ENTRADAS-E-EFEITOS-EXTERNOS.md).
Os critérios originais e suas relações com cada uma das45 unidades permanecem integralmente
na [matriz desta rodada](matriz-45-unidades.json). Não cria nova aprovação nem autoriza efeitos.

| Gate | Capacidade que depende da entrada | Entrada/evidência ainda faltante | Capacidade local e limite |
|---|---|---|---|
| G01 — rotação/credenciais | Qualquer uso autenticado de fonte, operação/release no alcance de V2-041 | Atestados verificáveis de rotação/invalidação das seis classes, consumidores, autoria/validade e continuidade do writer; secret store e jobs reais enumerados por Segurança/Operações | Scanner, intake e recusa local preservados. Nenhuma leitura de segredo, sonda, rotação ou renovação de autorização |
| G02 — governança/CI | Publicação de baseline, proteção efetiva e CI do SHA publicado | Provider, remote/branch, owners, checks/proteções e autorização exata; prova do SHA remoto, CI e histórico | POM/gates e diffs locais. Nenhum workflow com nomes inventados, commit, push ou alteração do índice real |
| G03 — fonte real | Caracterização nominal, bootstrap histórico, identidade/crosswalk, completude e paridade core | Contratos `/info` autenticados, releases/fingerprints, chaves tipadas/escopadas e cardinalidades de raiz/filhos; filtros/janelas, garantia de paginação/completude, oráculo independente, validade/tetos e saúde do writer | As11 famílias entram por páginas declaradas e usam os adaptadores locais. Fretes compartilham revisão nos dois caminhos. Usuários mantém USERS_SNAPSHOT. Tradução nominal das expansões e identidade de pai/parada Raster continuam dependentes dos contratos reais |
| G04 — regras/referências | Ativação de referências, regras fiscais/financeiras e dimensões; compatibilidade das saídas; sweep nominal | Grão e precedência fiscal/financeira, série NFS-e com fonte própria, calendário/filial, frota/papéis/vigência, políticas de ausência por responsabilidade e manifesto dos18 contratos externos mais escopo do monitoramento interno | Importadores e consumidores recebem cinco famílias de referências e nove grupos de suplementos explícitos. Cinco fatos/19 saídas e33 previews são comparáveis localmente. Série, owner ou identidade ausente não são inferidos; apply integral de ausência é recusado |
| G05 — ambiente/provas materiais | Fundação operacional, fresh/upgrade no ambiente definitivo, durabilidade/recuperação real e desempenho representativo | Banco V2 dedicado isolado, principals segregados, TLS, quotas/storage, backup, RTO/RPO, janela/orçamento e autorização por efeito; COMMIT/crash/restore/rebuild/replay e medidas materiais | Schema102 existente preservado, V099–V102 já instaladas e não reaplicadas; provas sintéticas com transação externa, commit de domínio bloqueado e rollback. Isso não prova durabilidade de COMMIT, restore ou ausência de alterações compensatórias por simples igualdade de contagens |
| G06 — ensaio de corte | V2-048b/CUTOVER-DB-01, DATABASE_WIDE | G03–G05 e gates de segurança/consumidores/bootstrap/release; rota, Tcut, executores, janela e autorização; duas recuperações materiais, fences negativos e aceite nominal do ensaio | Roteiro e catálogo anteriores preservados. Nenhum novo ensaio material, writer, principal ou banco provisionado |
| G07 — corte | Gate V2-014 e primeira publicação produtiva aceita | DoD da unidade inteira, paridades aceitas, sweep pertinente, segurança, contratos, E2E/release, ensaio G06, backup/rota/abort e autorização individualizada | Nenhum deploy/cutover. Somente publicação produtiva aceita define PNR; após ele, o desenho mantém roll-forward V2 |
| G08 — desativação | Retirada de jobs/adapters/consumidores e arquivamento do legado | Cortes aceitos, observação/retenção datadas, inventário de consumidores, destinos e owners, autorização de retirada e zero responsabilidade mantida sem destino | Revisão/remoção local de classes apenas com prova de uso/substituição. Não é desativação operacional; V1, serviços e consumidores permanecem fora dos efeitos |

## Limites por contrato nominal

COL ainda exige snapshot e cardinalidade/crosswalk reais. FRE exige origem do frescor,
precedência fiscal/financeira e relações ratificadas. MAN exige identidade/crosswalk de
pick/MDF-e e referências de frota/competência. COT exige release tarifária com vigência e
owner. LOC exige vínculo nominal com Fretes e origem temporal. USER exige garantia real
do snapshot; não há incremental por `updatedAt` inferido.

CAP exige identidade de raiz/parcela/rateio e o mesmo conjunto `issue_date+created_at`.
FAT exige título/documento/Frete e fonte própria de NFS-e. INV exige raiz/componentes,
minuta e completude. SIN exige identidade/papéis/cardinalidades e formato temporal nominal.
RAS exige CodSolicitacao/Ordem estáveis no escopo e sob reordenação, além de completude e
frescor remotos. A decisão MANTER_RASTER permanece; o alcance operacional padrão continua
condicional, fora da primeira onda.

Dependência externa bloqueia somente essas parcelas. Falhas de implementação, integração,
empacotamento, testes ou documentação local deste macrobloco devem ser corrigidas antes da
entrega e não podem ser deslocadas para G01–G08.

O perfil `runtime-recovery-local-integration` e `RuntimeRecoveryLocalIntegrationIT`
exigem manifesto de campanha e escritas com COMMIT durável entre processos. Essa prova
material permanece em G05 e não foi executada ou renovada nesta ordem de rollback.
O gate atual inclui os modelos de recuperação do dispatcher com estado/JDBC sintéticos,
as fronteiras temporais e a retomada da qualificação com SQL sob rollback; cada camada
está identificada em P19. Essas provas não atestam durabilidade de COMMIT.
