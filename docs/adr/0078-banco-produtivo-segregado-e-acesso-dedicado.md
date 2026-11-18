# ADR 0078 — banco segregado, acesso dedicado e implantação por contrato

Data: 08/11/2026. Escopo: quatro marcos solicitados pelo usuário, criação de `ETL_SISTEMA_V2` e exceção de segurança aprovada diretamente. Estado: infraestrutura/fundação qualificada; recebimento produtivo, automação e consumo de negócio ainda pendentes. Ver [implantação 0758](../catalogos/integracao-etl-v2-dashboard/IMPLANTACAO-0758.md).

## Decisão

Preservar `ETL_SISTEMA`, `ETL_SISTEMA_V2_SHADOW`, `DASHBOARDS`, SQL Server 2022 Standard 16.0.1000.6, serviços, Windows, TLS, acessos antigos e rota atual do consumidor. O usuário autorizou expressamente a proposta revisada de acesso e reforçou que não estará presente para novas respostas e que a comunicação dashboard/V1 não pode ser quebrada.

Criar o banco separado e instalar somente pacotes com alvo, hashes, ordem, pré/pós-condições e recibos próprios. Os originais V001–V106, baseline e manifestos históricos permanecem imutáveis. Não substituir SHADOW globalmente, executar recursivamente `database`, copiar dados V1 para simular extração V2 ou registrar migrations não aplicadas como concluídas.

O bootstrap cria apenas o contêiner. A segurança usa V001/V002 originais; a fundação compartilhada usa V003–V009 originais. O diário `ctl.production_installation_step` registra os arquivos realmente executados, com SHA-256. Não há histórico Flyway simulado, baseline fictício ou declaração de aplicação de V010–V106. Há 77 tabelas no marco instalado: 75 das sete migrations compartilhadas, a allowlist de grants e o diário real.

O owner dos sete schemas é `v2_schema_owner`, sem login. As seis roles originais mantêm a contenção de ownership/EXECUTE AS. A identidade de aplicação `etl_v2_runtime_prod` recebe apenas membership `v2_runtime` no banco novo; não recebe sa, sysadmin, dbcreator, db_owner, DDL ou escrita livre em core. O segredo novo fica sob DPAPI CurrentUser em diretório privado já protegido, fora dos repositórios; nenhuma ACL foi alterada.

O login existente `usuario_etl` recebe CONNECT/SELECT pub no banco novo. Suas permissões antigas, inclusive a condição legada de owner em DASHBOARDS e dbcreator no servidor, são preservadas. Não declarar que esse login antigo passou a ser globalmente de mínimo privilégio. A rota física permanece nos 26 synonyms atuais em DASHBOARDS, apontando para ETL_SISTEMA. Não alterar `.env` produtivo, iniciar/reiniciar API/UI ou aplicar corte nesta unidade.

## Qualificação real e limites

Foram exercitados criação/atributos do banco, instalação transacional, autenticação das duas identidades e seis casos físicos de controle: chamada autorizada, repetição idempotente, conflito rejeitado, fim da transação, SELECT direto recusado e readback independente com zero linhas de teste após rollback. Esses casos usam metadados explicitamente sintéticos; **não são testes de recebimento dos dados reais das entidades**.

O cliente Java17/JDBC12.4.2 falhou no handshake com CertificateException. Um teste separado com JDBC13.4.0 já instalado passou, sem bypass de TLS ou efeito no servidor. A dependência do próximo build do dashboard foi explicitada para 13.4.0.jre11. Após a mudança, passaram os 44 testes backend pertinentes; o teste JDBC físico usou esse artefato Maven. Preservar o driver/perfil C78 do V2 existente: sua autorização continua restrita ao Shadow. A mudança de biblioteca de aplicação não é mudança de versão do engine SQL.

O teste JDBC leu metadados dos 26 objetos atuais, confirmou os 958 nomes de colunas e as 26 rotas originais; leu a view de evidência da instalação no banco novo e conferiu a identidade dedicada. Não é paridade de conteúdo, teste HTTP/tela/exportação, publicação não vazia ou aceite dos 134 campos. A view `pub.v2_installation_status` expõe somente passos de instalação, sem declarar prontidão de negócio.

A correção da conciliação do dashboard usa igualdade positiva após trim/case; “Não conciliado” não entra. O dicionário acompanha a regra; nove casos no engine, 44 testes backend, TypeScript, lint, 63 suítes/401 testes frontend e encoding passaram. Código preparado; produção não reiniciada. A primeira tentativa do lançador de testes e os handshakes recusados permanecem como evidências, sem ocultar falhas.

## Próximo boundary

A fundação não torna o runtime de sombra um runtime produtivo. Criar o contrato explícito por entidade/fonte/tenant, resolver precisão/larguras/arrays/referências/publicação e ligar os adapters existentes sem remover suas recusas. Exigir recebimento de origem real, persistência, reconciliação, publicação e recuperação físicas; respeitar os limites/holds do fornecedor e preservar V1.

Somente depois qualificar o supervisor externo one-shot de 60 minutos, exclusão de sobreposição e progresso contínuo por entidade/escopo. A recuperação 0757 continua testada offline, sem supervisor instalado. Não habilitar um agendamento que chamaria um runtime ainda incompatível com o destino.

Preparar o corte DATABASE_WIDE e retorno com inventário dos 26 objetos/958 campos, snapshot das rotas e pré-condições. Templates 0758 são não executáveis, contêm recusa incondicional e não foram adicionados ao discovery do Flyway. O ensaio físico e a aprovação de corte continuam pendentes. Ler o plano P0–P6 e o checkpoint para os itens restantes.
