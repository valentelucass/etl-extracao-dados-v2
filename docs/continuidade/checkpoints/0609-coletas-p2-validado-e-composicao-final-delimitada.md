# 0609 — Coletas P2 validado e composição final delimitada

O ajuste mínimo do catálogo de Coletas passou12 casos próprios, quatro no alvo antigo e oito no catálogo proposto. Original0/14.107ms/519.053.312B,1GiB/4min; zero Source, SQL ou providers. O mesmo INFO real0607 reencodificado produziu12 diferenças no antigo e aceitação no proposto. Filtros nus, prefixo incorreto, policy antiga e tentativa de concluir captura só com INFO foram recusados. DATA31, perfilLEGACY, serializador, paginação e ordenação anteriores foram preservados.

Manifesto5260533D358D103875F7807B838A12843696DF8F18803703FCCAB04079426EBA; um Java de produto e cinco substituições literais. A inversão recupera o catálogo canônico34454BD3E50EF0FEBE9D8FF57495616739512C8E655FC5C03E5C2914FA0F31B2. Parser, adapter, Gate, mapper e gateway não foram alterados. As JVMs antiga e proposta foram separadas; a proposta era overlay de catálogo, não um novo produto já qualificado.

Versão proposta `2026-10-26.p2-coletas-info-qualified.v1`; release993fe2617cf59dbfc67bb2667ee628df9b01c23aca4801e061446b477997ceb7, metadata9d2f43ccf274ac321582acf3abaa9b4b631ac156eeed6d531f0a7f2ddfbec08e e response68a4ebfe94e00dc80c4da109eeedb30fbde79b023599e79d15ebb3df2e6c4de3. O domínio do hash response inclui a versão; sua mudança não significa alteração da estrutura DATA. Não adicionar sourceContractVersion observado ao fluxoLEGACY de Coletas por analogia com Cotações.

Prova externa RootColetasP2Own12Qualification0608.json SHA E6F6B388A0EDBB845EBC60B6326004159556C6CFBA2A3D53B50F31DB133E54F7. Novo JAR, banco completo e admissão física continuam false. O último par Source permanece INFOColetas0607/delta1; nenhum novo HTTP foi feito nesta qualificação.

## Um único pacote final, sem produto intermediário

ComparaçãoR43, manifesto80B050A9AE89B38B01E66612DCAB24C31B6171E8BD7F3F983B795B42C09599E4, delimitou o menor delta entre15AB e F2A. As oito bibliotecas e o POM são iguais. Das classes,1321 têm bytes iguais,14 comuns diferem e60 existem somente no15AB. Os60 preservam o fluxo P3 observado; diferenças de Main, HTTP e Raster também devem permanecer no15AB. Apenas dois fontes de pins/perfil de Cotações e um recurso P2 precisam vir do F2A.

Decisão de preparação Root: conservar15AB e aplicar esses três inputsCOT mais o catálogoColetasP2 aprovado. São três fontes substituídos e um recurso acrescentado; manter os demais fontes,55 recursos esperados,1395 classes esperadas,778 fontes main/704 test, oito libs e POM. O builder antigo756/681 não pode ser executado sem rebind. Deve recompilar a árvore própria, gerar autoria/metadados reais, conferir os demais bytes e não copiar target, reports ou PASS antigos. Não houve criação do novo JAR, execução do builder, adoção ou deploy por essa decisão.

Reutilizar as qualificações existentes no novo alvo: corpos200 sem somar integridade duas vezes; oito corpos propostos de Coletas sobre o JAR final sem overlay; regressão integral624+9Clock e sequênciaBind/111/Clock4/Main7 com entradas e recibos próprios. Os quatro controles antigos de Coletas continuam no F2A e não são quatro sucessos adicionais sobre o produto final. Não somar suítes sobrepostas ou transferir PASS entre produtos. Decisão RootFinalRuntimeCompositionDecision0608.json SHA C1EA72361634302BDF8095480876294B07D1B6D81C72BF51DFA43F792513C20E; a escolha futura do executável continua com Root, sem autoridade Source/SQL/DDL.

## Coletas: ligação mínima e catálogo37 necessário

Mapa fechado coletas-bound-minimal-gap-map-0608-v1/map.json SHA9C6479057B4CF2BC9FC0343B76AAE0AFB5BA60D6A3DAC334A20B5BF9DB309F4C. O owner existente de11 tabelas é reutilizável, mas o catálogo STRICT atual13/13 não inclui a tabela e procedure de Coletas. O sucessor exige14 tabelas/14 módulos,37 SELECT por passagem e74 no ensaio de duas passagens, com os mesmos11 locks. Users16 não substitui essa prova; tampouco se podem fabricar as duas observações faltantes para os50 testes do loader.

Preparar primeiro o coletor de catálogo37 somente leitura com os componentes existentes, qualificação própria e observação real Root. BoundV16 permanece WIP até esse catálogo e o JAR final estarem qualificados/pinados. Não há novo owner ou framework nem ampliação frouxa de compatibilidade.

Preservar o empréstimo atual do control-plane no begin, cujos RPCs usam até18 parâmetros, e selecionar o borrower23 D4E somente no staging de Coletas. Não trocar o getConnection inteiro por uma porta que aceita apenas a chamada de Coletas.

Os31 campos são preservados em `ColetaStageRecord.payloadJson`, gerado de canonicalJson(record) inteiro e gravado no bind8 da procedure tipada. O genérico12 recebe identidade/hashes, não o payload. A futura leitura deve comparar o JSON completo e seus hashes, além dos23 binds e campos de presença/relações/status/freshness; não contar23 como se fossem31 colunas de negócio. Qualquer quarentena exige conservação explícita ou recusa; não declarar perda zero sem medição.

O staged_at genérico usa SYSUTCDATETIME do banco, enquanto bind23 recebe batch.observedAt; não exigir igualdade desses valores diferentes. O readback planejado inclui quatro SELECT delimitados, dois de relógio do banco, e conferência por faixa temporal própria. Manter o recorte17/10/2026/picks.request_date/AmericaSaoPaulo, perfilLEGACY, sequence_code asc e per100 até qualificar outro comportamento.

Raster continua em preparação de programa estrutural concreto, sem DDL aprovado ou executado. Usuários segue semEOF; Chrome aguardando reconexão, sem Excel/paridade. Etapas3/4, gravação completa Coletas, demais entidades, composição/adoção/deploy seguem abertas. Root único executor; outros chats somente arquivos. ESL só consultas/filtros/downloads, sem alterações de SQL/serviços/contas/ACL/V1/PM2 e sem commit de negócio retido. Commit e push mantidos.
