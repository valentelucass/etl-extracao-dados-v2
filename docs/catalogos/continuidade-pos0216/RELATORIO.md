# Correção local da sucessão e do catálogo de frota

CORRECAO_LOCAL_POS0216. A ordem de localizar, corrigir e testar as dificuldades
locais corrige a conclusão prematura de esgotamento no checkpoint0216. A preparação
P10/P11/P12/P14/P15/P21 conserva seus testes; duas falhas técnicas exigiam trabalho
local adicional. Nenhuma ausência de autorização foi usada para encerrar essa correção.

## Causa e correção

O último elo executável da cadeia de continuidade era P06. Seus hashes descrevem
a revisão P06, mas os validadores comparavam essa revisão com os arquivos atuais
após P07/P08, P09 e a preparação das seis frentes. Foram recuperados por SHA-256
os 18 arquivos da revisão P06 que já tinham evoluído antes desta correção.
O novo manifesto registra o delta completo, snapshots anteriores, preservados e
arquivos novos. O módulo P06 passa a verificar sua fotografia exata e compor a
sucessão até a revisão atual. Não há exceção por extensão ou simples dispensa de hash.

Na frota, a decisão V01 fixa o mapper `f99ad676…11384`; o mapper atual é
`f3865ac8…04d6`. O snapshot original foi recuperado do histórico `bloco61-local`.
O único delta é o tratamento textual de `status`, já existente antes desta rodada.
Os campos de frota não mudaram. O validador verifica os 14 anchors da decisão
histórica, os hashes anterior e atual do mapper e as guardas estáticas atuais.
Isso não prova identidade nominal de Veículos/Motoristas.

Os manifestos, selos, decisões, ledgers e checkpoints anteriores não foram
reescritos. As falhas registradas em0216 e seus recibos continuam vermelhos e
íntegros. PASS nesta rodada significa nova execução após a correção.

## Escopo, entradas e recuperação

P10: preparação local; publicação/CI depende de G02, owner do repositório.
P11: política local; feed/baseline real depende do responsável de segurança.
P12: contratos locais; ambiente/retention/restore depende de G05, DBA/Ops/
Segurança/Compliance e data owner. P14: identidades locais; G03/G04 depende do
fornecedor e negócio. P15: referências locais; releases/vigências dependem dos
owners de referências. P21: contratos dimensionais locais; G04 e fontes
qualificadas dependem de seus owners. Os requisitos sanitizados e suas origens
permanecem em `../preparacao-offline-p10-p21/matriz.json`; não chegou input novo.

Não houve rede, fonte real, segredo, produção, SQL/DDL/DML, rotação/revogação,
deploy, serviço, job, release, paridade real, cutover, revisão humana ou aceite V2.
Contadores39/45 e67/115 preservados. Nenhum runtime, dependência ou schema foi
alterado nesta correção. Recuperação por diff próprio contra `before/` na rodada
`target/correcao-local-pos0216-20260921-01/`, sem restauração ampla.

Próximo trabalho elegível após validar esta correção: intake offline de pacote
sanitizado novo para G02/FEED/G05/G03/G04, conforme matriz; sem repetir efeitos.
Logs e contraprovas desta correção ficam na rodada acima; o checkpoint de
fechamento registra os resultados efetivamente observados.

## Resultado executado

Trilha ampla e frota: PASS. Sucessão:14 contraprovas novas +11 P06 PASS;
matriz das seis P:13; preparação da trilha:24. Scans3+21+294 textos, zero achado.
Recibos e hashes em [validacoes.json](validacoes.json).48 testes Java/10 classes
da rodada anterior continuam íntegros; não foram reexecutados nesta correção
sem delta Java. Os25 testes da sucessão verificam escopo, tipos, hashes,
predecessor, inventário exato, snapshots, remoção, pins, caminho e composição.

A primeira rodada recusou um caminho histórico com acento. A validação de
segmentos agora admite letras Unicode e continua recusando absoluto/traversal;
as três falhas OFFLINE_SUCCESSION_PATH foram preservadas antes do novo PASS.
As buscas próprias interrompidas preservaram o resultado parcial; a busca
dirigida recuperou os19 arquivos exatos, sem leitura de entradas secretas.

A conferência comparou3680 arquivos iniciais:3674 íntegros, apenas quatro
preâmbulos documentais e dois validadores alterados;115 checkboxes/67 marcados.
Não existe falha técnica local conhecida restante neste recorte. O fechamento
está no checkpoint0218;0217 preserva a etapa de recuperação anterior aos testes.
