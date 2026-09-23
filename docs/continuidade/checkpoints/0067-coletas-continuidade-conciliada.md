# Checkpoint0067 — continuidade conciliada após edições concorrentes

10/09/2026. COLETAS_TEMPORAL_LINK_LOCAL_VERIFIED. A entrega local de Coletas
conserva a implementação e o verify-03,1530/0/0/4,38 casos novos;770 fontes iguais
ao build. Nenhuma execução Java adicional nesta conciliação documental.

Duas frentes criaram checkpoints com número0066 durante a mesma janela.
Preservar ambos, identificados por caminho/hash, sem renomear ou apagar:
- 0066-coletas-ligacao-temporal-entrega.md: 51ad650ef4481d70096f396a66981cad24596a456c7960452ab6a98a1e6cd71e.
- 0066-avisos-java-testes.md: ce8abdbe5312f938252f1938d997f7503861a0c5da524fd419a8bb4a641de64a.
Ambos apontam para0065-coletas-ligacao-temporal-verificada.md. Este0067 reúne
as linhas de continuidade e passa a ser o índice corrente. Número isolado não
identifica o predecessor; consultar os nomes completos acima.

A manutenção concorrente dos avisos Java declarou sua conclusão e documentou
as cinco mudanças de teste já preservadas e exercitadas por nosso verify-03.
Também inseriu seções em STATES/trilha/RETOMADA após o selo candidato da entrega.
Essas seções foram preservadas integralmente; este passo acrescenta somente
o registro de conciliação e os hashes atuais ao manifesto desta entrega em curso.
Snapshots e candidato anteriores: concurrent-docs-before/ e
manifest-before-documentary-conciliation.json no diretório privado da rodada.
Manifestos de fases anteriores, checkpoints0065 e ambos0066 permanecem imutáveis.

9 caminhos existentes editados por esta entrega,3 deles com documentação
concorrente preservada;5 outros testes alterados pela manutenção de avisos,
sem edição por esta implementação. Origem explícita no manifesto. A adição
0066-avisos-java-testes.md pertence à manutenção concorrente.
O diff integral registra todos os deltas contra o inventário2400; os diffs de
implementação e concorrência identificam essas origens. Não ampliar o roadmap.

A prova funcional continua local: captura paginada, ligação de par explícito,
presença/bruto/identidade/proveniência e bloqueios conservadores. Nenhum
consumidor SQL do complemento, API, .env, credencial, DDL/UAC, novo orçamento,
ativação, produção, commit ou push. Sem efeito externo desconhecido.
COL-TIME-01/Q-COL-01/V2-012a/b/c/V2-041 sem novo aceite;67/115,191 rotas,zero AGORA.
Conferir checks e receipt finais em target/coletas-temporal-link-20260910/.

Próximas ações:
1. Integrar staging/cruzamento SQL próprios preservando proveniência e conflitos.
2. Qualificar bindings/transições reais, mesmo lote e concorrência nas camadas próprias.
3. Conferir aceites representativo/V2-041 antes de ativação operacional.
