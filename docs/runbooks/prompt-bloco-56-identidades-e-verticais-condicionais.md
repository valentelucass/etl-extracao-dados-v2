# Proposta B56 — identidades e verticais com continuidade comprovável

**PROPOSTO_NAO_ADOTADO_NAO_EXECUTADO** — preparado em 08/09/2026.
Este documento responde ao planejamento de um bloco amplo e retomável. Não é
autorização operacional nem indicação de que P08/P09/P10/P11 foram desbloqueados.
A escolha de modelo ou a disponibilidade de horas não altera essa condição.

## Objetivo e base preservada

Resolver, com evidência nova suficiente, as identidades ainda pendentes e
implementar somente as verticais liberadas pelos seus critérios. Trabalhar
por frentes independentes, com checkpoints e provas por etapa. Entregar também
os impedimentos que permanecerem, sem usar código parcial como aceite integral.

Base: B55 A–J concluído; 65/115 itens. Cinco verticais locais operacionais,
Java 17 e 1138 testes históricos, V001–V023. Preservar dados, evidências, ledgers,
revisões protegidas e alterações anteriores B53/B54/B55. Não executar `clean`
no target canônico, reinstalar baseline nem reeditar migration aplicada.

Ler AGENTS, STATES, contexto global, [protocolo](continuidade-agentes.md),
[RETOMADA](../continuidade/RETOMADA.md), critérios V2-009b/V2-029–032,
contratos V2-025b aplicáveis, matriz V2-017a e os quatro manifests de identidade.
Ler o código legado apenas como fonte estática permitida e indicada pelos
catálogos; não consultar produção nem transformar heurística antiga em contrato.

## Triagem obrigatória antes de selecionar a implementação

| Frente | Tarefa existente | Evidência que precisa responder à lacuna |
| --- | --- | --- |
| A | V2-009b/8636 | ID e wire type da raiz accounting_debit, grão raiz/parcela/rateio, colisões e cardinalidade; ant_ils_sequence_code não é identidade aceita da linha física |
| B | V2-009b/4924 | Estabilidade/escopo do ID da linha, título lógico, CT-e/NFS-e/Frete, identidade e cardinalidade dos filhos; nenhum hash ou unique_id inventado |
| C | V2-009b/10633 | Raiz e papel Frete/minuta, shape real de invoices_mapping, colisão e cardinalidade; DTO Object e fixture não provam shape remoto |
| D | V2-009b/6392 | Grão sequence_code versus composição legada, raiz/invoice/minuta/ocorrência, papéis e cardinalidade; nenhuma identidade simplificada por semelhança |

Fontes de verdade: `docs/catalogos/identidade-contas-a-pagar/manifesto.json`,
`identidade-faturas-por-cliente/manifesto.json`, `identidade-inventario/manifesto.json`
e `identidade-sinistros/manifesto.json`, todos sob `docs/catalogos/`.

Para cada input recebido, registrar origem, versão, data, escopo, autorização,
integridade e quais afirmações ele sustenta. Diferenciar observação de amostra,
garantia do fornecedor, regra aprovada e fixture. Procurar contraexemplos;
unicidade numa amostra não prova estabilidade ou completude universal.
Nenhum parser/validator pode declarar autenticidade ou aceite de negócio sozinho.

Sem evidência nova que responda à lacuna, conservar o resultado atual. Não repetir
o Bloco 34 com os mesmos dados nem criar schema/domínio sobre identidade suposta.
Pode preparar o pacote exato do input que falta e avançar em outra frente liberada.

## Implementação condicionada por domínio

| Frente | Condição anterior | Critérios de implementação que continuam obrigatórios |
| --- | --- | --- |
| E / V2-029 | A comprovada e dependências do STATES atendidas | CAP-01–05; filtros issue_date+created_at; raiz/parcela/rateio, frescor e reducers financeiros sem multiplicação por expansão |
| F / V2-030 | B comprovada e dependências atendidas | FAT-01–07; source ID↔título↔documentos↔Fretes; filhos tipados, presença, frescor, cardinalidade e replay |
| G / V2-031 | C comprovada e dependências atendidas | INV-01–04; aliases, raiz/filhos, reducers, minuta, parsing/quarentena, reativação e identidade escopada |
| H / V2-032 | D comprovada e dependências atendidas | SIN-01/02; grão e reducers, horas brutas/tipadas, timezone, frescor e relações sem inferência |

Cada frente inclui domínio, mapper/batch, persistência, migration aditiva quando
adotada, testes e matriz de portabilidade. Usar os critérios originais completos
do STATES; esta tabela é índice, não redefinição abreviada do aceite.
Adaptação para o JAR não é automática: confirmar contrato, DQ, recuperação,
capabilities, scopes e grants próprios antes de propor a travessia operacional.
Os 32 grants/16 scopes de B55 não autorizam as quatro novas entidades.

## Escopo ainda a concretizar para adoção

Esta preparação não abre campanha, cria ledger B56 ou reserva unidades.
O saldo B55 de 125 e o saldo B54 de 18 não são transferíveis. Não renovar vigência.

Depois da triagem, o pacote a apresentar deve conter: frentes realmente elegíveis,
alvo/contas reaproveitáveis conferidos, alterações e hashes, aplicação/verificação/
recuperação, limites por operação, totais, concorrência, duração, dados que
permanecerão e deltas exatos de direitos. Não preencher teto ou grant por chute.
Concluir código/testes independentes já autorizados antes de solicitar um delta.
Uma aprovação anterior exatamente aplicável não deve ser pedida novamente.

Fonte real exige V2-041 e autorização específica de fonte/janela/teto. O pedido
de documentação não autoriza rotação, consulta remota, ETL_SISTEMA, produção,
agenda, commit ou push. Esses limites não impedem a preparação offline autorizada.

## Execução longa sem depender do contexto do chat

1. Criar inventário inicial próprio; identificar mudanças preexistentes.
2. Elaborar uma matriz por frente: critério → componente → prova → limite → estado.
3. Selecionar uma unidade coerente e registrar checkpoint antes de efeitos.
4. Testar e registrar resultados esperados/observados. Não chamar a suíte inteira
   a cada edição; usar regressão dirigida e suíte completa sobre o conjunto final.
5. Após cada frente/campanha/falha relevante, salvar novo checkpoint e atualizar
   RETOMADA. No máximo três próximas ações concretas no índice.
6. Após compressão, reabrir arquivos e conferir resultado autoritativo. Efeito
   desconhecido exige reconciliação antes de qualquer repetição.
7. Continuar trabalho independente adotado. Se todos dependerem de input ausente,
   entregar bloqueios concretos e pacotes preparados, sem repetir tentativas.

## Aceites e cálculo do progresso

Somente quatro identidades aprovadas não fecham automaticamente V2-009 pai.
V2-009b pode fechar quando todas as suas fatias e critérios forem satisfeitos.
Enforcement, Raster e demais relações continuam nos itens próprios.

Com denominador 115 inalterado: quatro identidades mais V2-009b seriam cinco
aceites, total 70/115 = 60,9%. Se também V2-029–032 forem integralmente comprovadas,
seriam nove, total 74/115 = 64,3%. São cenários condicionais; nenhuma frente tem
garantia de fechar nesta proposta. Recalcular após mudanças legítimas de escopo.
Não criar subcheckboxes para obter um número maior nem contar rotas como itens.

## Entrega final exigida quando o pacote for adotado

Manifesto de evidências; matriz das oito frentes; testes por camada; inventário e
diff próprios; operação/recuperação das partes exercitadas; pendências com requisito
e input exatos. Atualizar STATES, trilha e validadores sem reescrever históricos.
Somente dizer "B56 concluído" se o escopo efetivamente adotado tiver sido atendido;
tempo gasto, pacote preparado ou meta percentual não substituem implementação.
