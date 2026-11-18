# Checkpoint 0756 — prontidão e caminho até produção

## Identificação, objetivo e limites

- Registrado em UTC: 2026-11-09T00:33:40.4203502Z.
- Anterior: 0755-preparacao-e-qualificacao-contratos-dashboard.md, SHA-256 7622F7A6B338F6C36A9FD59E6CF5855BF12AC6163E449A35BE46D9D3B6AB6A76.
- Pedido: avaliar se está tudo pronto para começar implementações e mapear o caminho até produção.
- Unidade: revisão de prontidão e roteiro completo, sem implementação inferida da pergunta.
- Estado: plano PROPOSTO; P0/pacote técnico ainda pendente de fechamento. Instalação e produção não aprovadas.

## Alterações e decisões

Documento principal: [PLANO-PRODUCAO.md](../../catalogos/integracao-etl-v2-dashboard/PLANO-PRODUCAO.md). Índice central e os dois estados apontam para ele; RETOMADA registra o marco imediato. Históricos preservados por prefixos. Aplicações, configurações, SQL versionado e bancos não foram alterados. Nenhum SQL, fonte, teste de aplicação, agendamento, deploy ou corte foi executado nesta rodada.

Conclusões verificadas na documentação/código: ADR0007 já determina agendador externo e execução única; RuntimeEnvironment permanece exclusivamente Shadow; V002 cria usuários/roles e grants/denies; o runbook antigo de corte prevê mudança de permissões e unidade DATABASE_WIDE até prova granular. Efeitos de segurança conflitam com a regra posterior vigente: preparar solução compatível, sem executar ou omitir dependências silenciosamente.

Perguntas enviadas ao usuário sobre atraso aceitável dos dados e histórico inicial. Resposta não registrada ao gravar; não assumir opção pré-selecionada. Retenção, recuperação e responsáveis também têm marcos antes da operação/corte. Sem nova necessidade de aprovação para ações documentais independentes.

Evidências de tipo/dados/testes continuam as do 0755, com limites originais. Nenhum resultado anterior foi elevado a compatibilidade integral. A entrega legível fica em outputs/Roteiro_Ate_Producao_ETL_SISTEMA_V2.txt no chat 2026-11-08/etl-v2-sql-server-dashboard. Recibo e manifesto 0756 separam esta atualização das evidências anteriores.

## Retomada — até três ações

1. Fechar P0 com alvo/atributos, pacote versionado, segurança compatível, contratos produtivos, referências e tratamento das diferenças de armazenamento/publicação.
2. Implementar P1–P4 por dependência, primeiro criação do novo banco; depois recebimento/publicação não vazia e consumo com regressões reais.
3. Qualificar P5/P6: ciclos agendados, capacidade/alertas, recuperação medida, ensaio de corte/retorno e acompanhamento; decisões humanas fechadas antes dos seus marcos.

Conclusão desta unidade: roteiro e prontidão documentados/conferidos. Objetivo produtivo permanece pendente. Não há processo próprio de aplicação, extração ou efeito SQL em andamento. Nenhum bloqueio de ação documental; dependências operacionais estão especificadas no plano, sem impedir preparações independentes.

## Esclarecimentos recebidos durante esta unidade

O usuário informou que o dashboard naturalmente mostra o último mês e que não deseja mudança no dashboard quanto a isso. Preservar o período padrão e preparar os dados para os contratos/janelas atuais, incluindo dependências de cálculo; não inferir retenção de um mês nem descarte de dados. Na pergunta de atualização respondeu “nao entendi”; o conceito foi explicado com uma mudança às 10h aparecer até 11h/10h15 e a pergunta foi reformulada. Sem frequência escolhida até a gravação deste complemento.

### Resposta posterior que fecha a frequência e a meta

O usuário definiu “a cada 60min baixar os dados dos ultimos 7 dias”, com reconciliação de completude e pendências para recuperar paradas, e confirmou “1h depois ta tranquilo”. Cadência de 60 minutos, releitura de sete dias e meta de atualização em uma hora ficam registradas; capacidade de cumpri-las ainda precisa ser medida. Recuperar todo o intervalo pendente por entidade, inclusive anterior aos sete dias; não avançar cobertura contínua sobre falha/lacuna, não marcar dia aberto como fechado, não publicar conjunto parcial ou duplicar ocorrências por reexecução. A preferência pelo último mês no dashboard permanece.

Leitura adicional de código confirmou base existente de planejamento/contiguousEnd e uma lacuna de integração: `RuntimeTemporalPolicy.Cadence` só admite dia/mês e o planner espera fechamento da janela. O supervisor produtivo horário/dia corrente precisa ser preparado e testado; não equivale a configuração já pronta. O plano registra dez cenários operacionais de aceite, ainda não executados nesta rodada. Nenhum código/banco foi alterado.

## Conferência adicional do V1 solicitada pelo usuário

Esclarecimento final: trata-se de extração na origem, com novos dados recebidos, não apenas leitura do SQL. Código/configuração V1 confirmam D-7..D0 inclusivo e espera de 60 minutos após fim do ciclo, com fila e reconciliação por data/API/entidade. Watermark antigo não expande consulta; último sucesso máximo não prova cobertura contínua; reconciliação diária só agenda ontem; fechamento mensal depende do dia1. Não há prova de recuperação automática de toda parada longa. No V2, a política produtiva precisa integrar esses contratos e o requisito explícito de recuperar todo intervalo pendente.

Validação nova: Maven offline protegido executou ExecutionWindowPlannerTest 12 testes e LoopReconciliationServiceTest 9 testes, zero falhas/erros/skips, relatórios novos com timestamp/hash em outputs/testes-regras-v1-0756.json. Primeira tentativa não iniciou Maven por argumentos ao cmd; preservada em attempt1 e corrigida por lançador PowerShell. Nenhuma fonte/API/SQL/daemon foi executada. Fontes/configurações V1/V2/dashboard não editados; target V1 contém os artefatos normais do teste.

Documento [REGRAS-EXTRACAO-V1-V2.md](../../catalogos/integracao-etl-v2-dashboard/REGRAS-EXTRACAO-V1-V2.md) separa regras confirmadas, configuração do checkout versus JVM produtiva não inspecionada e integração produtiva ainda pendente. Documento e plano foram espelhados em outputs. Estado final desta unidade: conferência de regras e prontidão documentada, objetivo produtivo ainda aberto.