# ADR0059 — identidade por pin manual exclusiva do SQL SAMPLE local

Data: 2026-10-04. Decisão adotada pelo usuário em Hermes325fac56a833, em resposta à proposta concreta0438. Perfil implementado e qualificado offline integralmente; resultado JDBC e limitações físicas no checkpoint0439/STATES. Extração real ainda pendente.

## Decisão e origem

Regra **COL-SAMPLE-TLS-PIN-001**. Responsáveis técnicos: Supervisor ETL, Banco/Persistência (conector/TLS/peer), Runtime (admissão/CLI). Origem: pedido autorizado325fac56a833 e material público recuperado na unidade9bb10e17fcfa. O driver12.8.2 recusou offline localhost nessa folha: confiança em um truststore sozinho não resolve a correspondência DNS.

O catálogo privado Banco usa **COL-SAMPLE-SQL-PIN-01** como identificador de implementação da mesma política; ele remete a esta decisão canônica, sem criar outra autorização ou liberar a origem. Exemplos, contratos afetados e testes estão em `target/sql-pin-sample-20261004-325fac56a833/banco/rule-catalog.json`.

Aceitar manualmente como identidade inicial somente DER SHA256 `BF723AE91FC90F3CC448122366C90650CBC2C6F5CD4A849F4B3C88C68E7A43A0`, subject=issuer `CN=SSL_Self_Signed_Fallback`, RSA3072/SHA256withRSA, sem SAN e sem extensões de uso, validade UTC2026-10-03T20:13:47Z..2056-10-03T20:13:47Z. Procedência independente incompleta; autoassinatura e captura loopback não a comprovam. Exceção DNS explícita somente para novo perfil JDBC SQL SAMPLE V2, opt-in separado, localhost/loopback, banco literal ETL_SISTEMA_V2_SHADOW, sa existente via segredo externo. Identidade futura é igualdade com os bytes inicialmente aprovados e prova criptográfica de posse, não validação DNS.

## Critérios de implementação e qualificação

- Pin fixo obrigatório, referência manual desta decisão e modo de identidade explícito; rejeitar pin arbitrário vindo de CLI/ambiente e renovação silenciosa.
- Validar DER exato, validade/tamanho/assinatura e política criptográfica; permitir a ausência das extensões desta folha apenas no escopo aprovado, recusando extensões críticas desconhecidas ou uso incompatível se presentes.
- Socket efetivamente loopback, sem proxy/redirect/fallback remoto; alvo e perfil verificados antes de segredo/conexão. TLS deve terminar com prova criptográfica antes de enviar autenticação. Mostrar ordem causal com driver real, não só implementação falsa de trust manager.
- Manter encrypt=true e trustServerCertificate=false; nenhum accept-all, trust global, TLS de HTTP alterado ou mudança no servidor. Configuração de classes do driver é composta internamente pela API aprovada, não escape da allowlist por texto externo.
- Defaults Windows/fullpilot/Main sem opt-in e demais consumidores JDBC intactos. Recusas causais: pin errado/folha alterada/expirada/notyetvalid/remoto/outrobanco/perfilmismatch/ausênciaoptin/TLSinseguro e desvio dos defaults.
- Uma qualificação agregada dos bytes finais após freezes/integração CI; cobertura/arquitetura/linters originais, histórico RED/FAIL preservado. Offline não comprova SQL/API ou extração.

Exemplo positivo: folha exata válida, handshake criptográfico com peer loopback e perfil separado explicitamente aprovado para shadow. Exemplo negativo: mesma CN com DER diferente, alvo remoto ou SQL padrão com opção pin; recusar antes da autenticação.

## Operação, risco e recuperação

Risco aceito: confiança inicial manual não certificada independentemente; rotação do certificado fallback invalida o pin e exige nova evidência/decisão. Validade longa não cria autorização perpétua. Alternativa DNS/PKIX normal exige certificado SQL compatível, fora desta intervenção cliente.

Banco é único executor SQL. Nova prova JDBC causal após qualificação/despacho/reserva:1conexão/até12SELECT/10s timeout/60s absoluto/semretry, target/auth/peer/version/contrato/PREPOST e fechamento. Fonte STOP429 depende evidência específica ou nova autorização de sondagem; nenhuma sondagem por esta decisão TLS. Prova realColetas6908 só após todos os gates, per1/página1/1000linhas/HTTP2, staging/readback/rollback/POST. Diagnóstico não pode ser relatado como extração.

Recuperação: desabilitar opt-in novo e voltar ao caminho estrito anterior; remoção de artefatos V2 apenas com verificação de alvo. Não configurar SQL Server, trocar certificado/chave, importar confiança global ou alterar versão16.0.1000.6/contas/permissões/identidades/serviço/schema/V1. Nenhum aceite produtivo/cutover decorre desta ADR.

Evidência e testes executados serão referenciados em STATES e target/sql-pin-sample-20261004-325fac56a833; este texto registra a decisão, não antecipa resultado.
