# P11 — provas locais e busca das pendências

A foi qualificada e C permanece corrigido. B foi executado, mas não qualificado: as duas suítes integrais apresentaram falhas SQL. P10/P11/P12/P14/P15/P21 não foram declarados integralmente concluídos.

| Prova | Resultado observado |
| --- | --- |
| JDBC/nativo 12.8.2 | 1 IT passou; DLL/POM exatos do Maven Central, hashes conferidos, rollback |
| Unitários nas duas rodadas | 2162 casos por rodada, zero falhas/erros, 4 skips históricos |
| P07 integral01 | 492 ITs/105 classes;491 passaram,1 erro de lock no readback após rollback; cobertura passou, Maven falhou |
| P07 integral02 | 79 ITs/22 classes registrados,2 timeouts na preparação de Manifestos; interrompida após os erros, Maven exit1 |
| Diagnósticos originais | Escala4 casos e ManifestGates4 casos passaram, sem alterar código/assertions/timeouts |
| P08 | Não iniciado; dependência de P07 não satisfeita |
| Readback final | Agregados idênticos: auditorias0/0/453,246 tabelas e1816 objetos; nenhum DDL/commit de domínio |

O SQL Server informou pressão de memória física após full02 (process_physical_memory_low=1). Após o diagnóstico, a flag era0 e os agregados estavam iguais. Isso registra pressão transitória, sem demonstrar causalidade retroativa dos timeouts. A primeira falha foi de lock, a segunda de query timeout; não foram fundidas em uma causa presumida. Nenhum serviço/processo de terceiros foi alterado. Apenas a árvore Maven própria de full02 foi encerrada, após conferir PID, início, pai e attempt. O wrapper permaneceu para registrar logs e rollback.

O limite previamente registrado foi de duas tentativas corretivas (native02 e full02). Os diagnósticos preservaram os testes originais e não promoveram os resultados falhos. Nenhum teto, vigência ou limite foi renovado. A pendência B é técnica, não um aceite externo nem um pedido de credencial. A próxima prova integral exige uma ordem finita própria, condições locais verificadas e, depois do PASS, P08.

[Resultados e hashes](resultado.json), [matriz vigente](matriz-atual.json), [busca por requisito](investigacao-inputs.json) e [diff da rodada](../../../target/p11-fisico-20260921-01/delta-final.diff).

## Busca solicitada nas documentações e testes

Foram relacionados23 documentos/recibos aos16 requisitos. As11 consultas reais de B56 e os testes locais foram reaproveitados no alcance comprovado. A busca não localizou evidência suficiente para fechar os critérios abaixo; não afirma inexistência em sistemas externos.

| Requisito | Evidência localizada e limite | Responsável registrado |
| --- | --- | --- |
| G02-PUBLICACAO | Consulta local atual: zero remotes; nenhum CODEOWNERS nos caminhos convencionais. Politica reserva provider, conjunto e aprovadores ao owner. Nenhum destino foi inferido. Provider, remote/branch, conjunto exato aprovado, owners/aprovadores e autorizacao nominal de publicacao; inventario local nao e conjunto aprovado. | Owner do repositorio |
| G02-CI | Workflows versionados e verificacoes locais existem; nao foi localizado recibo de CI de provedor vinculado ao SHA aprovado. Preparacao nao e execucao remota. Protecoes/checks obrigatorios e Gitleaks versionado; depois do efeito autorizado, SHA remoto, historico completo e recibos reais de CI vinculados ao mesmo SHA. | Owner do repositorio |
| FEED-BASELINE | Reutilizados JSON/HTML e resultado P11: NVD atualizado, 13 dependencias, zero achados/erros. Documento declara aceite nominal de Seguranca ainda ausente. Aceite nominal da baseline pelo responsável de segurança; não foi inferido da autorização de consulta pública. | Responsavel de seguranca |
| G05-AMBIENTE | Laboratorio local e provas SQL existem; alvo ONLINE confirmado nesta rodada. Nao comprovam ambiente definitivo, TLS/ACL, quotas e agenda aprovados. Alvo V2 dedicado isolado, principals segregados, TLS e ACL nominais, capacidade/quotas/storage, config/scheduler/alertas aprovados; sem valores de credenciais ou certificados. | DBA, Operacoes, Seguranca e Compliance |
| G05-RETENCAO | Lifecycle implementado/testado localmente; runbook declara TTL candidato nao aprovado e identidades/retenção nominal pendentes. Data owner e compliance ratificam policy/TTL por staging, logs e archive, autoridade de hold/release; DBA/Operacoes comprovam WORM ou imutabilidade equivalente, criptografia, capacidade e identidade de servico. | DBA, Operacoes, Seguranca e Compliance |
| G05-RECUPERACAO | Rollback e readback agregado sao provas locais existentes; nao ha nesta evidencia backup/restore material nem RTO/RPO medidos. Backup e restore drill materiais, RTO/RPO medidos, COMMIT/crash/rebuild/replay quando requeridos, janela/orcamento e autorizacao individual por efeito com ledger previo. Rollback sintetico nao autoriza nem prova esses efeitos. | DBA, Operacoes, Seguranca e Compliance |
| G03-10633 | B56 remoto: mapping ARRAY de STRING observado. Nenhuma garantia de chave filha, estabilidade ou vinculo raiz/Frete; nao pedir novamente prova do shape ja observado. Garantia versionada de raiz/Frete, semântica e chave dos filhos, escopo/estabilidade, colisões e cardinalidade. ARRAY de STRING já observado, sem pedir nova fixture para esse shape. | Fornecedor e owner de dados por fonte |
| G03-8636 | B56 remoto e contraexemplo historico: INTEGER na parcela, raiz tecnica ausente; candidata refutada como chave de linha. Schema GraphQL nao demonstra crosswalk. Identificador tipado da raiz accounting_debit e prova representativa raiz-parcela-rateio. ant_ils_sequence_code nao identifica raiz nem linha fisica. | Fornecedor e owner de dados por fonte |
| G03-4924 | B56 remoto: ID INTEGER de linha e arrays de textos. Identidade da linha nao comprova titulo, filhos, documento ou Frete. Entidade e estabilidade do ID de linha; crosswalk linha/título/documento/Frete e identidade dos filhos. ARRAY de STRING já observado. | Fornecedor e owner de dados por fonte |
| G03-6392 | B56 remoto: sequence INTEGER e numero de nota STRING; identificadores tecnicos testados ausentes. Nota nao comprova raiz/ocorrencia. Garantia versionada da raiz ou observacoes repetidas com colisoes entre sequencia, minuta e ocorrencia de invoice; papeis e cardinalidade, sem simplificar hash legado por inferencia. | Fornecedor e owner de dados por fonte |
| G03-RASTER | Contrato local preserva CodSolicitacao e Ordem como candidatos; contraexemplo de reordenacao refuta fallback por posicao. Garantia de identidade ausente. Tipos/escopo/estabilidade de CodSolicitacao e identidade de parada; Ordem sob reordenacao/insercao/remocao/retorno, atomicidade/frescor e cardinalidade. Sem fallback posicional. | Fornecedor e owner de dados por fonte |
| G04-SEMANTICA | FAT-02 registra divergencia mapper/SQL quando CT-e e NFS-e coexistem; fonte propria de serie NFS-e e precedencia nao ratificadas. Owner de negocio ratifica grao, relacoes, alias/rekey, titulo/documento e precedencia fiscal/financeira aplicaveis, incluindo fonte propria de serie NFS-e. Nenhuma chave ou owner nominal e inferido. | Negocio e owners de referencias e consumidores |
| G04-REFERENCIAS | Manifestos e importadores/fixtures qualificam o formato; nao sao export oficial autorizado por familia, release e vigencia. Export/baseline autorizado por familia consumida, release/escopo, proveniencia, vigencia, grao, contagem e fingerprint; normalizacao e tokenizacao por versao aprovada, sem fornecer tokens/documentos/chaves neste chat. | Negocio e owners de referencias e consumidores |
| G04-RATIFICACAO | Segregacao autor/importador/aprovador e cobertura sao exigencias testaveis. Nenhuma evidencia selecionada contem ratificacao real independente. Autor/importador/aprovador segregados, cobertura calendario/filial/frota/regiao/status/tarifas, fixtures de paridade e politicas de ausencia/conflito; importacao material requer P12/G05 e autorizacao propria. | Negocio e owners de referencias e consumidores |
| G04-DIMENSOES | DAG, bindings sinteticos e current/history locais existentes. A evidencia declara fontes qualificadas e regras de vigencia/rekey externas pendentes. Por dimensao: fonte qualificada e paridade core das fatias usadas, grao/papeis, binding explicito, vigencia, current/history, ausencia, conflitos e rekey ratificados; publicacao contratual permanece P25. | Negocio e owners de referencias e consumidores |
| G04-FROTA | Decisao V02 explicitamente BLOCKED: placa/nome/filial e coocorrencia nao fornecem IDs estaveis. Papeis principal/reboques comprovados somente no grao Manifesto. Veiculos/reboques e motoristas exigem ID estavel escopado do fornecedor, semantica de placa/nome/filial, colisoes/homonimos, merge/split, papeis separados e lifecycle. Evidencia tecnica do fornecedor por G03; ratificacao de negocio por G04. V2-035c nao autoriza inferir identidade. | Negocio e owners de referencias e consumidores |

Nenhum responsável nominal, aprovação humana, CI publicado ou garantia de identidade do fornecedor foi deduzido de uma fixture ou de um teste. FEED-ACHADOS continua atendido pelo scan público de13 dependências/zero achado do checkpoint0220; não houve novo scan NVD.

## Delta e preservação

Atualizado o lock atual para quatro Jackson2.18.11, JDBC12.8.2.jre11 e nativo12.8.2.x64, com seis POMs exatos adicionais. Os outros três componentes e todos os POMs/licenças históricos foram preservados. Os metadados da licença nativa vêm do POM12.8.2; o texto12.8.1 continua histórico. Binários ficam no cache/target. Nenhum fonte Java, teste Java, POM público ou SQL foi alterado nesta rodada.

A matriz separa quatro rodadas: preparação offline, auditoria pública0220, regressão unitária e execução física atual. Na atual foram quatro GETs Central; chamadas JDBC internas não foram contadas e permanecem null. Na pública0220 o total continua desconhecido, com sete GETs recebidos individualmente. Manifests históricos não foram regravados.

Falhas native01, harness01/02, full01/full02 e diagnósticos permanecem em target/p11-fisico-20260921-01/ e nos attempts referenciados. A sucessão documental conserva os bytes anteriores dos cinco arquivos alterados. Recuperação documental deve usar somente o diff desta rodada; SQL foi revertido transacionalmente. Nenhum pacote novo recebeu selo de qualificação.

39/45 e67/115 permanecem inalterados. Sem nova fonte de negócio, produção, publicação, deploy, cutover ou reavaliação de P09. A validação documental é registrada separadamente e não converte estas falhas em PASS funcional.
