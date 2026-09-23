# Encaminhamento após a primeira etapa local

## Resultado e correção

Foi corrigida a seleção que mandava sempre retornar à primeira etapa não aceita.
P09–P15 não têm novo trabalho local identificado nesta conferência; permanecem
abertos para os critérios externos. Não houve nova implementação Java, SQL,
execução de fonte, Maven ou qualificação física. Houve leitura das dependências
das 25 etapas, conferência dos 70 pins de componentes/testes e dos recibos fechados.
Isso é uma conferência delimitada, não uma nova auditoria funcional universal.

Fonte dos critérios: [matriz vigente](../../qualificacao-p07-p33/matriz.json),
seções stages, entities, references, dimensions, facts e contracts. O campo
localWorkStillEligible está vazio; nenhuma tarefa adicional foi inventada.
Os 2.263 unitários e 492 ITs são evidência histórica reutilizada de P07/P08.

## Primeira etapa: requisito e alcance que continua bloqueado

| P | Insumo concreto | Responsável | Efeito condicionado |
| --- | --- | --- | --- |
| P09 | Atestado G01 autenticado de rotação, invalidação e continuidade | Segurança/Operações | Uso externo de credenciais, release e operação; não testar tokens |
| P10 | Remote/provider/branch/owners aprovados e proteções/CI/Gitleaks no SHA | Owner do repositório | Governança remota e release; não criar destino por inferência |
| P11 | Aceite nominal da baseline já corrigida de 13 dependências | Segurança | Aceite da baseline; scan técnico não é aprovação |
| P12 | Ambiente, ACL/TLS, capacidade, retenção, backup/restore e RTO/RPO materiais | DBA/Operações/Segurança/Compliance | Efeitos operacionais e recuperação; rollback local não substitui COMMIT |
| P13 | Release contratual autenticada e vigente por fonte | Fornecedor/data owner | Confronto real da fonte correspondente |
| P14 | Identidade, grão, relações e semântica das fatias abertas | Fornecedor/Negócio | Somente verticais e relações dependentes |
| P15 | Exports/políticas oficiais das oito famílias e ratificação independente | Owners das referências/Negócio | Importação e ativação material da família usada |

P14: 8636 raiz/parcela/rateio; 4924 título/documento/Frete/filhos; 10633
raiz/componentes/Frete; 6392 raiz/minuta/ocorrência; Raster solicitação/parada.
Frota exige IDs estáveis escopados, não placa/nome. Formatos já observados não
devem ser solicitados novamente. P15: CALENDAR, PICK_STATUS, BRANCH_OPERATIONS,
OWNED_FLEET, BRANCH_ATTRIBUTION, CUBAGE_EXCLUSION, LOGISTICS_REGION, QUOTE_TARIFF.
Não refazer intakes existentes nem gerar referências oficiais a partir de fixtures.

## Etapa 2: entrada por fatia, sem bloqueio global artificial

| Trabalho | Entradas efetivamente necessárias para o aceite real | Parcela local existente |
| --- | --- | --- |
| P16 verticais | P12–P15 apenas do escopo usado | Onze verticais e enforcement locais |
| P17 caracterização | P16 da entidade, oráculo independente e janela autorizada | Harness local |
| P18 histórico | P17, horizonte/T0/Tcut/partições e fonte histórica autorizada | Bootstrap/replay local |
| P19 relações | Crosswalk, cardinalidade, órfãos, SLA e histórico aplicável | MC/CF locais |
| P20 paridade core | P17–P19 aplicáveis e janelas reais fechadas/repetidas | Comparadores locais |
| P21 dimensões | Fontes usadas aceitas, grão, bindings, vigência, rekey e IDs de frota | Seis dimensões locais |
| P22 ausência | P20 e política nominal/completude por responsabilidade | 33 responsabilidades classificadas; zero ENABLED |
| P23 apply | P22, snapshot completo e autorização específica | Preview/apply sintéticos |
| P24 fatos | P20; P15/P19/P21 apenas conforme entradas consumidas | Cinco fatos locais |
| P25 contratos | P16/P20/P21/P24 aplicáveis, manifesto por consumidor | 19 contratos; SQL-10 exige aceite interno próprio |
| P26 paridade analítica | P25 e P20/P24 aplicáveis, oráculo por saída | Comparadores locais |
| P27 escala | P20 ou P26, volume/distribuição representativos, SLO e orçamento | Harness/instrumentação locais |
| P28 E2E/recuperação | P12/P27 e P20 ou P26; P23 só para ausência habilitada | E2E/rollback locais, sem prova material de crash/restore |
| P29 RC | G01, P10/P11/P12/P28 do escopo; política SAST e aceites no RC exato | Pacote P08 reproduzível; PMD de dez regras não é SAST integral |

Cotações não espera MC/CF. Contas a Pagar não espera Faturas/Fretes. Localização
não espera CAP/FAT/INV/SIN. Fretes base não espera MC. A fonte não espera sua
dimensão derivada. Sweep não bloqueia uma saída que não o consome.

| Fato | Dependências do DAG existente |
| --- | --- |
| MAT-01 | Fretes + Localização + pagadores excluídos/documentos de filiais |
| MAT-02 | Fretes + Manifestos + Inventário + aliases/filiais |
| MAT-03 | Fretes + Localização + Faturas por Cliente + calendário/atribuição de filial |
| MAT-04 | Faturas por Cliente, grão de título e precedência fiscal nominal |
| MAT-05 | Manifestos + Coletas + Fretes + frota própria + MC/CF usados |

## Etapa 3: entrada operacional

P30 usa P29 e a cobertura aceita de P18/P20/P25/P26/P28 e P22 da unidade
CUTOVER-DB-01 / DATABASE_WIDE. P31 exige G06, alvo/execução autorizados e duas
recuperações materiais com RTO/RPO medidos. P32 exige P31, DoD integral e G07
nominal/datado. P33 exige P32, observação/retenção e G08 de retirada.
Não presumir corte por entidade quando a unidade aceita é DATABASE_WIDE.

## Uso dos próximos chats

[Prompt 2](ETAPA_2.txt) e [prompt 3](ETAPA_3.txt) estão completos. Eles permitem
continuar no escopo elegível, sem esperar aceite agregado da etapa anterior.
Com os mesmos insumos atuais, não há garantia de avanço material: a primeira
ação é conferir somente o delta recebido ou um defeito concreto, sem repetir
a revisão inteira. Se nada mudou e não há trabalho elegível demonstrado,
uma resposta curta deve referenciar esta lista, sem criar outro checkpoint,
rodar suíte ou enviar o usuário de volta à etapa 1.

Documentação pronta não significa caminho operacional livre. Aceites, ambiente,
conteúdo oficial e autorizações materiais precisam existir antes do efeito.
