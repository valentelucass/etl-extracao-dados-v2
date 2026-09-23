# Checkpoint 0029 — adoção e inventário B59

Data: 09/09/2026. Anterior: 0028-pos-bloco58-fechamento-documental.md,
SHA-256 e773a42b4d3034e3f6871958e03b154b04bc7d537bf66496719ee102fb84e6b1.
Objetivo: executar integralmente A–F do prompt B59, com zero pendência local
resolvível e sem reduzir gates. Autorização: mensagem explícita do usuário;
nenhuma nova consulta ou confirmação necessária para as frentes locais.

EM_EXECUCAO. Número 59 livre; baseline.json confirma 1.622 vínculos íntegros,
1.532 cópias canônicas congeladas e 50 artefatos do recibo anterior. Recuperação
somente contra `target/bloco59-local/initial`, nunca contra Git HEAD.
STATES e trilha registram a fatia local sem alterar checkboxes/contagens.

Lacunas observadas: request exige Data Export; sessão/selo/auditoria são tipados
Data Export; auditoria GraphQL só verifica não nulo na conclusão; falta composição
Usuários em src/main. Reader JDBC exige sourceKind DATA_EXPORT e recovery SQL V022
fecha allowlist em cinco verticais/terminal vazio Data Export. Essa última
dependência precisa de decisão/prova local; não será mascarada pelo simulador.
GraphQlHttpGatewayBundle não existe: a factory retorna GraphQlGateway diretamente.

Nenhum teste Java deste bloco executado ainda. Nenhum aceite encerrado. Não há
processo próprio ativo ou resultado externo desconhecido. Zero API/SQL físico,
.env, credenciais, instalação, agenda, commit/push ou orçamento. SHADOW_UPSERT_ONLY.

Próximas ações:
1. Concluir leitura dos contratos/SQL pertinentes e reproduzir as lacunas em testes.
2. Implementar a extensão mínima request/travessia/promoção e integração do root,
   preservando os bindings e a recusa padrão.
3. Validar cada unidade, registrar checkpoints e fechar regressão/sucessão/diff/recibo.
