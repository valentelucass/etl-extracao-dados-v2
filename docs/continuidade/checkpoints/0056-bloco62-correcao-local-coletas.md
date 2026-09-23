# Checkpoint0056 — B62: correção local de Coletas

10/09/2026. Anterior:0055-bloco62-investigacao-real-limitada.md,
SHA-256 fa6a45095acf90e0fc42df12b1eba0991a7ec383232e234c4af5c793e0b2fadd.

**COLETAS_LOCAL_FIX_VERIFIED_REAL_REPLAY_PENDING.** Objetivo efetivo: o usuário
pediu encontrar e implementar a solução, após autorizar procurar nas APIs com
.env. Continuação de B62; B61 A–D e aceites históricos preservados.

Autorização: alterações/testes locais e investigação read-only necessária6908.
Sem SQL, UAC, produção, mudança de credencial, runtime operacional ou renovação
B60. Nenhuma pergunta de localização ou aprovação está pendente ao usuário.
Rodada própria2/2 encerrada: /info200, /data429; duas reservas e dois resultados,
sem retry/redirect. Dia09/09,per2,order sequence_code asc,timeout30s,duração90s,
teto10MiB transporte/64KiB dados. Nenhuma nova consulta após429. Valores apenas
em memória; só perfil técnico sanitizado persistido. Não comprova V2-041.

Inventário anterior:2324 arquivos em target/b62-coletas-fix-20260910-163750/,
inventory.json e before/. Dez deltas com snapshots na sucessão; manifests,
ledgers e contratos históricos preservados. A alteração funcional adiciona
DataExportColetasContractCatalog, seleciona-o na composição e deriva a forma
HTTP do release. A ADR0043 declara os tipos estritos e a captura dos21 campos
não consumidos como escalares sem coerção; não são tipos observados da fonte.
Nenhuma regra de negócio, chave, relação ou frescor foi inferida desses campos.

| Prova | Observado | Referência privada/local |
| --- | --- | --- |
| Diagnóstico6908 | info31 campos/6 filtros;data429;parada | diagnostic-summary.json;diagnostic-ledger.jsonl |
| RED composição anterior | 1 falha esperada na seleção de raiz | directed-red-04.log;red-reports/ |
| Fluxo HTTP/parser/gate/mapper/staging | 21 testes novos passaram | DataExportColetasContractTest |
| Handlers oficiais com JDBC sintético | 5 testes passaram | RuntimeOperationalExecutionTest |
| Verify offline Java17/512MiB | 1427/0/0/4;gates verdes | verify-01.log;java-verification.json |
| Validadores runtime offline | 6 checks passaram | runtime-local.log |

Cobertura91,63%linhas/76,52%branches. Skips:3symlinks Windows e Cotações opt-in.
As três falhas iniciais de autoria dos testes estão preservadas. Compilação
com warnings fatais, formatter, Checkstyle e arquitetura permaneceram ativos.
Diff-completo.patch, receipt, final-checks e delivery-checks ficam na rodada;
conferir seus resultados antes de presumir gate de entrega. O código verificado
foi sincronizado da cópia isolada somente nos arquivos desta manutenção.

Nenhum efeito externo desconhecido: os dois resultados HTTP foram registrados.
Nenhuma campanha operacional própria ativa. Nenhum novo aceite do roadmap:
67/115,48 pendentes,191 rotas,zero AGORA. COL-SHAPE-01 corrigido/testado localmente;
replay real da revisão pendente. V2-012a/b/c,Q-COL-01,Q-USR-01,V2-041 abertos;
Q-MAN-01 EXTERNAL_HOLD. Rejeitado mascarar a forma original com envelope artificial
ou aprender/abrir automaticamente o contrato. Rollback pelos deltas/before/, sem
reescrever ocorrência contratual anterior ou evidência física.

Próximas ações:
1. Conferir Test-Bloco62ColetasFix.ps1 -IncludePrivateEvidence -SelfTest e os gates
   finais; o relatório está em docs/catalogos/bloco62-coletas-fix/RELATORIO.md.
2. Quando a condição externa429 for tratada, preparar rodada de replay limitada
   desta revisão; não reutilizar saldos encerrados nem chamar SQL/runtime.
3. Continuar oráculo independente e representatividade de Q-COL-01/Q-USR-01 pela
   matriz B61; tratar V2-041 com a prova própria, sem confundir acesso com rotação.

Conclusão local: correção implementada e fluxo completo exercitado offline.
Limite externo: falta replay real e oráculo independente; isso não deixa uma
frente de implementação local desta correção pendente.
