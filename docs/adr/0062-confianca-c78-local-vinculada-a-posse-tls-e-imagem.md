# ADR0062 — confiança C78 local vinculada à posse TLS e à imagem

- Data: 12/10/2026.
- Estado: decisão técnica adotada sob delegação expressa HERMES_5d489ad1eea2; perfil cliente separado implementado e agregado qualificado offline em12/10/2026, com24testes novos/compilação/formatação/análise estática e revisão Runtime. Sem autenticação JDBC, catálogo ou captura por esta evidência.
- Responsável técnico: Codex; Banco implementa o adaptador, Runtime qualifica o agregado. A decisão não constitui revisão humana da implementação.

## Autoridade e evidência

Lucas delegou a decisão sobre C78 e a obtenção autônoma das provas de identidade local, imagem carregada e quota existente. Reiterou a proibição permanente de atualizar, rebaixar ou substituir a instalação SQL Server. Não instalar certificado, alterar serviço/listeners, chave/ACL, contas, credenciais, identidades, permissões ou trust global para resolver a confiança.

A observação pública histórica C78 não comprovava Finished. A nova ocorrência Banco `C78FIN01-023b2b6069f2468ebd5ca31e38061822`, em 12/10/2026 às 18:29:35 UTC, completou TLS1.2 com `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384`, uma conexão e um callback de certificado, sem LOGIN7, autenticação, SQL, HTTP ESL ou provedor de segredo. O pin exato foi conferido com metadados derivados do DER reparsado. A folha é autoassinada, CN=SSL_Self_Signed_Fallback, RSA3072/SHA256, sem SAN/extensões, válida de09/10/2026 a09/10/2056 conforme os bytes qualificados.

Enquanto o socket permaneceu aberto, uma leitura nativa independente identificou exatamente um reverse tuple TCP ESTABLISHED da conexão, pertencente ao PID48480 do serviço MSSQLSERVER. O início nativo bruto134360443019517054 foi idêntico PREPOST; o kernel identificou a imagem carregada em `C:\Program Files\Microsoft SQL Server\MSSQL16.MSSQLSERVER\MSSQL\Binn\sqlservr.exe`. O arquivo correspondente manteve versão16.0.1000.6, SHA256 `73dcd4a89ff178cf6b2978ee29dccd8898fe5c1977abac8208aa4ecb5ec43e32` e assinatura Microsoft válida em cache. Não é um digest da memória nem verificação de revogação online. Native buffers foram limpos/liberados; socket, filhos, streams e job próprio tiveram fechamento conhecido, active0, exit0, 8,235s.

Fontes e Root buscaram recibo de quota JÁ existente por registros, novas rotas de logs, memória, Pages e conectores disponíveis. Nenhum recibo vigente do mesmo escopo6908 foi comprovado. Sucesso genérico, timestamp de arquivo ou sucesso de outro escopo não libera STOP429. Falta acesso read-only à auditoria/export existente da integração, com UTC, procedência, validade, restrições e os seis vínculos privados ao caller. Não atribuir essa busca interna a Lucas nem afirmar exclusividade administrativa não provada.

## Decisão

Escolher a confiança manual de menor escopo suportada pela prova atual: **novo perfil cliente separado**, sem alterar certificado/servidor, baseado no pin imutável e na posse TLS associada ao endpoint/processo/imagem local observados. Não declarar cadeia CA ou hostname validados. A ausência dessas provas externas não deve ser escondida como PKI global.

### C78-LOCAL-TLS-001 — confiança exata antes da autenticação

Perfil `C78_LOCAL_SHADOW_SQL_PIN_V1`; referência `HERMES_5d489ad1eea2_C78_LOCAL`; alvo literal `localhost:1433/ETL_SISTEMA_V2_SHADOW`; DER SHA256 `C78BBE9A3FDB1A997971A46FB41E7860EF7153EC7813A41250A9156363F86BCC`. Exigir as duas flags próprias `c78.local.shadow.enabled` e `c78.local.shadow.sql-pin.enabled`, ambas desabilitadas por padrão.

Verificar DER exato reparsado, validade, tamanho/algoritmos, TLS1.2 e ciphers permitidos, peer realmente loopback e vínculo ao socket antes de LOGIN7. O driver deve completar a prova criptográfica de posse antes de enviar autenticação. Folha diferente, alvo/flags incompatíveis, segundo socket, falta de prova ou fechamento incerto recusam; não aprender, renovar ou substituir pin automaticamente. Conservar `encrypt=true` e `trustServerCertificate=false`; nenhuma forma de accept-all ou fallback.

Fonte: esta delegação e os recibos reais acima. Exemplo: E3/BF continuam válidos somente em seus próprios contratos e são recusados pelo perfil C78. Testes exigidos: pin/alvo/flags, validade, metadados falsificados, peer/socket, handshake incompleto, fechamento e real driver com streams sintéticos antes de LOGIN7. Responsável: Codex/Banco; contratos afetados: somente o novo adaptador cliente.

### C78-LOCAL-BOUNDARY-001 — opt-in independente

Usar somente `sa` existente e SecretProvider externo existente. Não colocar segredo em URL, argumentos, logs, fixtures ou causas de erro. Um socket, sem retry, timeouts finitos; nenhuma conexão física nesta etapa de qualificação offline. Não alterar nem ampliar E3, BF, Windows, fullpilot, Main, runners, DDL, migrations, dependências ou produção. O perfil C78 não herda autorização para captura, commit ou promoção.

Cada execução física futura exige bytes finais qualificados, alvo/versão/hash/coorte atuais, reserva nova e executor Banco. Catálogo e captura são unidades distintas. Captura só com quota6908 vigente, seis vínculos privados, identidade/contratos físicos/proteção contra writers e readback/confirmar definidos. Não converter esta prova NOAUTH em autenticação, catálogo ou prontidão de extração.

Fonte: AGENTS.md, ADR0059/0060 e HERMES_5d489ad1eea2. Exemplo: flags C78 não habilitam um runner E3. Testes: independência de opt-ins, alvo exato, segredo indisponível, cleanup e recusa pelo driver antes de LOGIN7. Responsável: Codex/Banco/Runtime.

### C78-LOCAL-RECOVERY-001 — recuperação somente no cliente

Desabilitar as novas flags e fechar recursos próprios. Preservar recusas, ocorrências e ledgers; reconciliar resultado incerto antes de qualquer nova unidade. Não refazer eventos consumidos ou limpar dados já retidos. Nenhuma recuperação modifica SQL Server, versão16.0.1000.6, V1, conta, permissão, identidade, serviço, listener, certificado, trust global, schema ou produção.

Fonte: proibições permanentes do usuário e governança de ocorrências. Exemplo: a primeira recusa do gate local foi preservada, teve zero efeitos reconciliados e recebeu sucessor causal com autoridade distinta. Testes: fechamento/recusa/segundo socket e limpeza de credenciais. Responsável: Banco/Runtime; Codex integra a evidência.

## Rastreabilidade e limites

Unidade privada `hermes5d489ad1eea2`: BancoOS02/Runtime native03; banco02/physical01/effect-reconciliation01.json; banco03/prepared-manifest01.json; runtime/c78-finished-admission02.json; root/c78-finished-authorization02.json; banco03/executions01/<ocorrência>/receipt.json, active-binding.json, java-final.json e closures; banco03/controller01/<evento>/closure.json; fontes-quota01/handoff07.json. Checkpoints/STATES registram as qualificações posteriores, sem alterar fotografias históricas.

Qualificação local e comunicação Hermes completed não são aceite integral, captura retida, DQ, promoção ou cutover. A versão do SQL Server **JAMAIS** pode ser alterada, inclusive em retomadas.
