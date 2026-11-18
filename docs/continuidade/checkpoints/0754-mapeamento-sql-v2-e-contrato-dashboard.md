# Checkpoint 0754 — mapeamento SQL V2/dashboard e continuidade da preparação

## Identificação e objetivo

- Data de referência: 08/11/2026; gravação documental iniciada em `2026-11-08T23:57:52Z`.
- Anterior: `0753-155-faturas-tres-periodos-e-cinco-provas-concluidos.md`, SHA-256 `6FA59DCF079167AD92FA94D13C43C6B053BD9AE47D33AA3B9995DE337AD6D54F`. Preservar seu conteúdo e seus aceites históricos.
- Objetivo final do usuário: ETL V2 periódico gravando em `ETL_SISTEMA_V2`, organizado para consumo posterior pelo dashboard, preservando a extração qualificada.
- Unidade atual: documentar o levantamento já realizado e tornar sua consulta obrigatória na continuidade; registrar preparação antes das implementações.
- Estado: documentação do levantamento entregue; preparação técnica seguinte `PROPOSTO`; criação/instalação, carga, runtime e cutover não executados.

## Autorização e limites

Instrução efetiva do usuário: “documente bem, esses documentos avise no states.md para olhar sempre ele”; “o proximo vc vai rodar uma preparacao para essas pendencias”; “a primeira seria criar a nova database”. A instrução anterior “n vamos mexer em codigo” permanece para esta unidade. O contexto distingue preparação de implementação: não executar criação do banco agora.

Alvos documentais: `STATES.md` e continuidade do V2, `states.md` do dashboard e índice central da integração. Instância observada por leitura na investigação: `ROD-SRVW-001`, Standard 16.0.1000.6. Nenhuma escrita SQL, chamada de origem, migração, Main, endpoint, agendador, serviço, conta, permissão ou configuração é parte da atualização documental. Não há novo orçamento físico de extração nem reserva de efeito SQL. Não foi feita pergunta de aprovação nesta unidade.

## Alterações e decisões

Índice canônico da frente: [Integração ETL V2, SQL Server e dashboard](../../catalogos/integracao-etl-v2-dashboard/README.md).

Arquivos desta unidade:

- `STATES.md`: aviso de leitura obrigatória, conclusões comprovadas e próximas tarefas, acrescentados antes do histórico.
- `docs/catalogos/integracao-etl-v2-dashboard/README.md`: decisões, documentos/evidências, fontes, consumo atual, pendências F01–F10 e D01–D08, sequência e critérios de preparação.
- Este checkpoint: limites, evidências e retomada.
- `docs/continuidade/RETOMADA.md`: ponte curta para a frente, acrescentada sem apagar histórico.
- `../dashboards-etl/states.md`: aviso equivalente, contratos e pendências do consumidor, sem alteração de runtime.

O levantamento original está em `C:\Users\suporte\Documents\Codex\2026-11-08\etl-v2-sql-server-dashboard\outputs`. Leitura humana: `Mapeamento_ETL_SISTEMA_V2.txt`, `Consumo_Dashboard_e_Fontes_V1_V2.txt` e `Dicionario_Campos_Dashboard.txt`. O índice central lista os JSON por finalidade. Alterações preexistentes dos três repositórios foram registradas em `work/initial-git-status.json` no mesmo chat; o inventário de hashes anterior do V2 está em `work/mapping-input-snapshot.json`. Não pertencem a esta unidade.

Decisões mantidas: Shadow continua validação; produção futura usa banco separado; dados próprios do portal continuam em `DASHBOARDS`; preservar os sete schemas e contratos de extração existentes. Publicação estável em `pub` com contratos explícitos para o consumidor é proposta a preparar, ainda sem adoção física.

Hipóteses sem aceite: equivalência dos candidatos de nomes/tipos, estabilidade de identidade entre execuções, desenho final de collation/publicação e prontidão SQL/JDBC das provas offline. Não deduplicar multiplicidades legítimas nem preencher campos com zeros/NULL para simular compatibilidade.

Abordagens rejeitadas: renomear Shadow como produção; trocar apenas a URL ou os synonyms; executar recursivamente todo `database`; substituir nomes globalmente; executar V002 com efeitos em contas/permissões sem compatibilidade com as regras vigentes. Cada uma ignora dependências encontradas no levantamento.

## Execução e evidência

| Critério | Camada | Observação / limite | Evidência |
| --- | --- | --- | --- |
| Inventário de banco e recebimento V2 | SELECT e leitura estática | Banco produtivo novo ausente; sete schemas; baseline até V105 e V106 no diretório; V107 ainda template | `Mapeamento_ETL_SISTEMA_V2.txt`, inventário e pendências JSON |
| Consumo atual do dashboard | SELECT e leitura estática SQL/Java/TypeScript | 26 synonyms; 958 pares objeto/campo, sem aceite semântico; 87 vínculos de rota/tipos estáticos | `Consumo_Dashboard_e_Fontes_V1_V2.txt`, dicionário e JSON de linhagem/API |
| Regra atual de conciliação | SELECT na view inteira | 17.131 linhas; LIKE 15.273, igualdade “Conciliado” zero; não é teste de endpoint/período/ACL | `sql-dashboard-rule-probes.json` |
| Integridade original do levantamento | Hashes/estado Git | Recibo anterior à atualização documental, preservado como fotografia histórica | `manifesto-entrega.json`, `verificacao-preservacao.json` |
| Atualização de documentação 0754 | Arquivos/links/hashes | Conferência separada de conteúdo anterior, fontes e arquivos alterados; resultado detalhado no recibo | `verificacao-documentacao-0754.json`, `manifesto-documentacao-0754.json` |

Os dois últimos recibos documentais ficam no diretório de outputs acima. A cópia legível do índice entregue ao usuário é `Continuidade_Preparacao_ETL_SISTEMA_V2.txt`. Não reescrever o manifesto original para apagar as alterações documentais posteriores.

Nenhum teste de aplicação ou instalação foi executado nesta unidade documental. Não há efeito SQL de resultado desconhecido ou campanha própria de extração em andamento. O aceite encerrado é de documentação/continuidade; não é aceite do fluxo produtivo. A confirmação de preservação usa os hashes e os históricos, respeitando as alterações documentais intencionais desta unidade.

## Retomada imediata — até três ações

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa independente |
| --- | --- | --- | --- | --- |
| 1 | Preparar a matriz por campo/rota com regra, tipo, granularidade, origem, publicação e adaptação | Ler os três relatórios, o índice e as evidências do domínio | Matriz revisável com decisões propostas e lacunas explícitas | Preparar outros domínios que não dependam da decisão pendente |
| 2 | Preparar instalação e integração, incluindo perfil de produção, SQL, referências, precisão, V106/V107 e segurança | Preservar extratores e regras vigentes; não executar DDL nem fonte | Lista de arquivos afetados, precondições, plano de testes e recuperação | Completar contratos de leitura e critérios de aceite |
| 3 | Fechar a sequência das implementações; primeira será criar `ETL_SISTEMA_V2` | Pacote e atributos do alvo definidos; escopo da etapa conferido | Roteiro verificável, distinguindo criação, instalação, carga e corte | Preparar validações de contrato sem efeito externo |

Bloqueio externo nesta unidade: nenhum; a documentação pode ser concluída com as evidências disponíveis. Questões de semântica/segurança são pendências da preparação, não motivo para interromper seu trabalho independente. Parar a ação dependente se houver deriva de fonte/contrato, alvo ambíguo, efeito não previsto ou conflito com regras vigentes. Conclusão da preparação exige os critérios do índice central, sem transformar checklist de planejamento em implementação realizada.
