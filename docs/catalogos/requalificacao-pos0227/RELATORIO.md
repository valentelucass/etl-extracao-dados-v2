# Requalificação local P07/P08 após0227

P07 e P08 concluídos no escopo local dos pins atuais. Os16 requisitos externos de
P10/P11/P12/P14/P15/P21 continuam sem evidência suficiente para aceite integral.

| Macrobloco | Resultado executado |
| --- | --- |
| 1 — diagnóstico e P07 |2162unitários/247classes,4skips históricos;492ITs/105classes,zero falha/erro/skipIT. Maven verify,Enforcer,Spotless,Checkstyle e JaCoCo PASS;identidades e multiplicidades exatas. |
| 2 — P08 |730 membros declarados,9 componentes;dois ZIPs comparados byte a byte (7940550 bytes). Exemplos do build aprovado,lock/SBOM/POMs/licenças conferidos;smoke inspect/plan/run/status/resume/compare do JAR extraído. A/B:7etapas,133comparações e231previews cada.8variantes,8guardas de controle,21de admissão e envelope PASS. |
| 3 — P10/P11/P12 |Evidências anteriores reutilizadas e íntegras. Publicação/CI,aceite nominal da baseline e provas materiais de ambiente/retenção/recuperação continuam externos. |
| 4 — P14/P15/P21 |Tipos observados e provas sintéticas reutilizados no alcance original. Identidades/semântica/referências/dimensões/frota ainda dependem de fornecedor e Negócio. |
| 5 — fechamento |Sucessão nova preparada com snapshots;resultado dos validadores em validacoes.json quando efetivamente observado. |

O pré-flight registrou zero requisições/transações read-write/JDBC e Java próprio,
flags SQL de memória0/0,sistema0 e5542588KiB livres no host. Agregados antes,
apósP07 e apósP08 iguais:auditorias0/0/453,246tabelas/1816objetos e todas as
contagens por tabela preservadas. Windows integrado,alvo exato local,rollback;
nenhum DDL ou commit de domínio.

As falhas históricas continuam preservadas:full01 teve lock timeout no readback
pós-rollback da quarta escala;full02 teve query timeouts em ManifestGates.
Os diagnósticos4+4 já aprovados não foram repetidos. A pressão de memória então
observada é correlação;nem esta suíte aprovada demonstra a causa retroativa.
Nenhum Java,POM,SQL,oracle,assertion,timeout ou massa foi alterado nesta rodada.
O nativo12.8.2 já qualificado em0227 foi reutilizado;os novos bytes foram exercitados
pela suíte e pelo pacote atuais. Não houve novo scan NVD ou consulta pública.

Smoke01 falhou antes de iniciar inspect/JAR/JDBC por resolução duplicada de
pwsh.exe no PATH privado. A falha e o RED foram preservados; normalização
somente do ambiente filho passou GREEN com um executável e filho exit0.
Após readback igual e processos reconciliados,smoke02 executou a cadeia.
Nenhum byte do build ou do pacote foi alterado;nenhum timeout foi ampliado.
[Diff do executor privado](../../../target/requalificacao-pos0227-20260922-01/executor-path-fix.diff).

A ordem é própria,finita,serial:target/requalificacao-pos0227-20260922-01/authority.json. Ledger anterior fechado
permanece intacto;reservas atuais não transferem seu saldo. Chamadas JDBC internas
não foram instrumentadas:databaseCalls=null. Rodada pública0220 conserva total
desconhecido/sete GETs recebidos;rodada física0227 conserva quatro GETs Central.

[Resultados e hashes](resultado.json),[matriz](matriz-atual.json),
[evidência por requisito](investigacao-inputs.json),[diff](../../../target/requalificacao-pos0227-20260922-01/delta-final.diff).

| Requisito externo | Critério restante | Responsável documentado por papel |
| --- | --- | --- |
| G02-PUBLICACAO | Provider, remote/branch, conjunto exato aprovado, owners/aprovadores e autorizacao nominal de publicacao; inventario local nao e conjunto aprovado. | Owner do repositorio |
| G02-CI | Protecoes/checks obrigatorios e Gitleaks versionado; depois do efeito autorizado, SHA remoto, historico completo e recibos reais de CI vinculados ao mesmo SHA. | Owner do repositorio |
| FEED-BASELINE | Aceite nominal da baseline pelo responsável de segurança; não foi inferido da autorização de consulta pública. | Responsavel de seguranca |
| G05-AMBIENTE | Alvo V2 dedicado isolado, principals segregados, TLS e ACL nominais, capacidade/quotas/storage, config/scheduler/alertas aprovados; sem valores de credenciais ou certificados. | DBA, Operacoes, Seguranca e Compliance |
| G05-RETENCAO | Data owner e compliance ratificam policy/TTL por staging, logs e archive, autoridade de hold/release; DBA/Operacoes comprovam WORM ou imutabilidade equivalente, criptografia, capacidade e identidade de servico. | DBA, Operacoes, Seguranca e Compliance |
| G05-RECUPERACAO | Backup e restore drill materiais, RTO/RPO medidos, COMMIT/crash/rebuild/replay quando requeridos, janela/orcamento e autorizacao individual por efeito com ledger previo. Rollback sintetico nao autoriza nem prova esses efeitos. | DBA, Operacoes, Seguranca e Compliance |
| G03-10633 | Garantia versionada de raiz/Frete, semântica e chave dos filhos, escopo/estabilidade, colisões e cardinalidade. ARRAY de STRING já observado, sem pedir nova fixture para esse shape. | Fornecedor e owner de dados por fonte |
| G03-8636 | Identificador tipado da raiz accounting_debit e prova representativa raiz-parcela-rateio. ant_ils_sequence_code nao identifica raiz nem linha fisica. | Fornecedor e owner de dados por fonte |
| G03-4924 | Entidade e estabilidade do ID de linha; crosswalk linha/título/documento/Frete e identidade dos filhos. ARRAY de STRING já observado. | Fornecedor e owner de dados por fonte |
| G03-6392 | Garantia versionada da raiz ou observacoes repetidas com colisoes entre sequencia, minuta e ocorrencia de invoice; papeis e cardinalidade, sem simplificar hash legado por inferencia. | Fornecedor e owner de dados por fonte |
| G03-RASTER | Tipos/escopo/estabilidade de CodSolicitacao e identidade de parada; Ordem sob reordenacao/insercao/remocao/retorno, atomicidade/frescor e cardinalidade. Sem fallback posicional. | Fornecedor e owner de dados por fonte |
| G04-SEMANTICA | Owner de negocio ratifica grao, relacoes, alias/rekey, titulo/documento e precedencia fiscal/financeira aplicaveis, incluindo fonte propria de serie NFS-e. Nenhuma chave ou owner nominal e inferido. | Negocio e owners de referencias e consumidores |
| G04-REFERENCIAS | Export/baseline autorizado por familia consumida, release/escopo, proveniencia, vigencia, grao, contagem e fingerprint; normalizacao e tokenizacao por versao aprovada, sem fornecer tokens/documentos/chaves neste chat. | Negocio e owners de referencias e consumidores |
| G04-RATIFICACAO | Autor/importador/aprovador segregados, cobertura calendario/filial/frota/regiao/status/tarifas, fixtures de paridade e politicas de ausencia/conflito; importacao material requer P12/G05 e autorizacao propria. | Negocio e owners de referencias e consumidores |
| G04-DIMENSOES | Por dimensao: fonte qualificada e paridade core das fatias usadas, grao/papeis, binding explicito, vigencia, current/history, ausencia, conflitos e rekey ratificados; publicacao contratual permanece P25. | Negocio e owners de referencias e consumidores |
| G04-FROTA | Veiculos/reboques e motoristas exigem ID estavel escopado do fornecedor, semantica de placa/nome/filial, colisoes/homonimos, merge/split, papeis separados e lifecycle. Evidencia tecnica do fornecedor por G03; ratificacao de negocio por G04. V2-035c nao autoriza inferir identidade. | Negocio e owners de referencias e consumidores |

São23 artefatos previamente pesquisados com hashes reconferidos;nenhum input
novo justificou sondas ou busca adicional. Não foi inferida vigência/garantia de
fornecedor ou ratificação nominal de uma fixture. FEED-ACHADOS continua atendido
pela prova0220 de13dependências/zero achado;FEED-BASELINE exige Segurança.
P09 não reavaliado.39/45 e67/115 inalterados. Sem produção,publicação,deploy,
cutover ou revisão humana alegada. Recuperação documental:aplicar inverso somente
do diff desta rodada com os snapshots before;históricos e alterações anteriores
preservados. SQL já revertido,não existe migration desta rodada para desfazer.

Fechamento documental observado:sucessão/cadeia/trilha,autoteste do scanner,scans delimitados e UTF-8 PASS. [Recibos](validacoes.json) e [checkpoint0233](../../continuidade/checkpoints/0233-requalificacao-pos0227-fechamento-conferido.md). Os scans são locais e delimitados; não representam CI remoto nem novo Gitleaks do histórico.
