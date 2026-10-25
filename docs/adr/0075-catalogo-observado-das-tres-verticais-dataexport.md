# ADR 0075 — Catálogo observado de Manifestos, Cotações e Localização

24/10/2026. Owner técnico Root. Este passo adiciona estrutura de contrato;
não ativa seleção operacional,consulta,persistência,promoção ou completude.

Adicionar DataExportObservedEntityContractCatalog e registry fechado de pins,
com três recursos UTF-8 de versões2026-10-24.p1b-*-observed.v1. Os nomes/tipos
declarados do INFO e os formatos de DATA vêm das provas técnicas da campanha,
sem valores/IDs/nomes/cursors/payloads de negócio em recurso ou teste.

INFO tem91/37/26campos e18/6/8filtros. DATA_FIELD permaneceUNDECLARED;filtros
conservam somente o tipo realmente declarado e seu fingerprint. Tipos históricos
da famíliaNUMBER não são estreitados por amostras novas de50linhas;modela-se
INTEGER|NUMBER sem garantir integralidade. Os2pathsNULL-only continuamUNOBSERVED,
nullable e sem tipo de valor inventado.

Listas foram caracterizadas por observação atual delimitada:Manifestos tem
duas listasARRAY→ARRAY→STRING;Localização invoices_mapping éARRAY→STRING.
São95/37/27paths de resposta,distintos dos91/37/26campos de raiz. Nomes/valores
dos elementos nunca são embutidos. Campo/tipo/descendente inesperado falha;
não existe allowance opaca ou descoberta automática que amplie o contrato.

O loader confere bytes antes do parse,limite65536,JSON estrito,template/versão,
metadata/response/release fingerprints e pins não fornecidos pelo chamador.
Cache é lazy por template. Arquivo ausente/alterado ou versão trocada recusa.
Os defaults B55,sua ordem,per100,GET_WITH_QUERY e seleção histórica permanecem.

Os corpos observados não tinham order_by;isso não qualifica a ordem do enum.
Conectar o novo release ao fluxo requer perfil fechado,pedido/guard/transporte
coerentes e nova qualificação. Localização também requer raw slices até mapper26
e revisão do envelopeSQL17 em escopo próprio;não basta adicionar catálogo.

Prova:P1B externo469verificações;produto58testes funcionais PASS,3rechecks após
correção somente de braces no teste,style/strictcompile/7IT/bytes do pacotePASS.
NovoJAR219D408F...86E4AAB,sem deploy.1367entradas anteriores do A49 permaneceram
byte a byte iguais;378casos distintos atuais/preservados por composição,não
fullverify/cobertura nova. Checkpoint0568 e artefatos externos conservam falhas,
hashes,fontes e limites. Identidade global e snapshot continuam sem aceite.
