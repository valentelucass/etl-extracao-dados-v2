# 0634 — Estrutura dos pais consultada; captura real em revisão

Em27/10/2026, o leitor focal51F7/B3BF8 passou seus oito testes reais, Original0,
sem repetir os testes de Fretes ou a suíte principal. Root executou em seguida quatro
SELECTs: contexto, dois documentos completos de metadata e contexto final. Uma conexão,
Original0,14230ms,529133568bytes; identidade local SQL16.0.1000.6 e encerramentos
de conexão/socket/child comprovados. Zero Source de negócio, DML, DDL ou commit.

Prova pessoal RootProtocolParentsLiteralMetadataObservation0633.json:
CF41BCAB1AED6C533BA713D8420729CC0F8C6504C861016C5FEB1C43E852C0D1.
Prova do driver live-qualification-or-counts.json:B93CB79750773C35771A083CDC50FA37B01F62A0C99CDD495E03B3D3B3A55581.
Own8 real:2A41289F761B60E6F93653DFCDD071BA3F8BFBC6EB3F8FBE464F1CE7FE86E024.

ctl.source_protocol_binding: source_instance NVARCHAR128 e source_kind NVARCHAR64,
ambos NOTNULL/Latin1_General_100_BIN2; registered_at_utc datetime2(3) NOTNULL;
PK composta e FK para source_catalog. DocumentoFD9D923D é idêntico ao observado0626.
ctl.execution_source_protocol: execution_id uniqueidentifier NOTNULL/PK,
source_instance NVARCHAR128/source_kind NVARCHAR64/binding_origin NVARCHAR16,
todos NOTNULL/BIN2. FKs confiáveis e habilitadas para execution_attempt e binding.
Documento0A007137 também é idêntico ao0626. Ambos têm trigger habilitado de
imutabilidade e nenhum RLS observado. Documentos integrais conservados nos artefatos
privados do projeto. São observações pontuais, sem snapshot protegido ou autorização
para DDL. Revisão estrutural de Usuários/P3 deve usar estes fatos literais.

Manifestos conserva Own3Java+PS3 reais0DED e preflight completoED9 do checkpoint0633.
Emissor17 v3/249731 teve revisão independente628F917A, sem bloqueadores. V2/C760
preservado e nunca executado: recusaria o código INPUT_HASH_DRIFT do caso PS
COT6906/LatestSource. V3 admite esse único caso e mantém demais códigos e igualdade
entre esperado/observado. Preloader pessoal deve conferir44+159 antes de qualquer
Common/emissor. RootLiteral2B709 recebeu revisão127DDF; scripts Source A74F/13own/
26externos preparados e parseados0 pelo Root, ainda em revisão independente.
Nenhum CODE/UUID/janela ou nova captura havia sido emitido neste checkpoint.

Comparador MFT/COT90D7 preparado; Root encontrou antes de qualquer execução uma
expressão de fingerprint ainda de Coletas. Correção isolada em sibling foi solicitada;
v1 não é elegível para captura real. Quatro causais preparados não equivalem a PASS.

Última Source continua Coletas0626/delta3/par0; globais não finais. Main7,322+3,
Raster6 e Raster3+1 reais anteriores preservados; sem repetição para metadados.
Capturas novas MFT/COT devem preceder adoção68/AST. Todas as entidades no banco e
confronto por registro/campo ainda não concluídos. Excel636 sem identidade/filtro
comprovados; ESL requer novo login/2FA. PM2/V1/produção/credenciais e proibição
permanente DDL/grants/Flyway preservados. Root único executor; commit e push.
