# Checkpoint 0562 — Nove fontes, Fretes principal e catálogo real

24/10/2026. Sucessor de0561. Campanha externa `quatro-etapas-20261024-01`.

Etapa1 concluída no escopo declarado:18 HTTP200,9 INFO+9 DATA, GET com corpo,
dia17/per1/página1/intervalo3s/sem retry. Campos:6908=31,6389=114,6399=91,
6906=37,8656=26,8636=28,4924=52,10633=26,6392=44. Cotações/Sinistros
vazios nessa data; Inventário expandiu1 chave candidata em11 linhas. Não é
prova global de identidade/paginação/histórico/persistência. Stage1-v2 e recibos preservados.

Etapa2 concluiu a leitura pelo pedido principal e caso de uso real Fretes do JAR
D81052B6...EF96BCB:3 chamadas,per50,37 linhas/37 IDs,37 mapeamentos válidos,
zero quarentena,página2 vazia. Receiver em RAM;sem SQL/admissão operacional.
Stage2-v3 preserva as duas falhas offline anteriores (alvo descritivo ausente).
Resultado SHA256 8D0FC1F30A4EBD0044FE7EC37DDD9745AF1AB61E415A45455613EAE19F9AC70C.

Etapa3: catálogo SQL reservado por Root/Banco, conexão C78 openDev com segredo
DPAPI existente via stdin; mesma conexão comprovada nativamente antes do LOGIN7,
TLS validado,localhost/shadow/sa/16.0.1000.6,instância preservada. Primeira unidade
parou em CATALOG_ROW_BOUND na terceira SELECT:228 tabelas excediam4096 colunas.
Conexão fechada,zero DML/DDL;falha preservada,sem retry dessa unidade.
Sucessor estreitou a projeção às tabelas necessárias:12 SELECT concluídas,
14 tabelas/240 colunas/47 módulos/335 parâmetros,47 definições exatamente iguais
ao código normalizado das migrations;3 triggers nas tabelas lidas. Snapshot
zero requests concorrentes não é proteção de captura. Nenhuma captura admitida
pelo catálogo. Conexões/jobs encerrados;21 HTTP nesta campanha,zero429,2 conexões
SQL de leitura,15 SELECT tentadas no total,zero DML/DDL/commit retido.

Próximo: capacidade própria de prova Fretes fonte→staging tipado→leitura independente
→rollback→ausência, protegendo transação e footprint real contra corrida. Não usar
gateway normal com conexão física desprotegida:ele faz commit. Depois, etapa4
demais entidades/Usuários/Raster;sem semântica externa inventada ou aceite por fixture.

C78 rollback-only;SQL/instalação/contas/permissões/identidades/ACL/serviços/listeners/
trust/schema/Flyway/V1/WMS/produção intactos. MainHTTP histórico UNKNOWN preservado.
