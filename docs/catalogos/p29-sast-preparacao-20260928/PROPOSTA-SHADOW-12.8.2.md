# Proposta aprovada — par físico shadow JDBC 12.8.2

**Decisão posterior, 28/09/2026:** o usuário autorizou expressamente a troca
12.8.1→12.8.2 e a continuação dos gates locais. O delta ativo foi aplicado;
qualificação offline do par/pacote passou em recibos novos. O UAC de transporte
loopback falhou sem recibo do helper; readback mostrou configuração e banco
inalterados. Flyway/migrations/JDBC não ocorreram, sem retry do UAC. P11/SCA
externo segue aberto. As frases abaixo sobre autorização pendente preservam a fotografia
anterior à decisão. O [runbook 12.8.2](../../runbooks/p08-shadow-12.8.2-preflight-20260928.md)
substitui operacionalmente o roteiro 12.8.1 sem alterar sua história.

**Estado:** exige decisão explícita do usuário para alterar o pin de
`AGENTS.md`. A decisão provisória de Segurança suspendeu JDBC e toda execução
física da variante 12.8.1. Este documento não autoriza UAC, SQL, DLL ou
alteração de ambiente. Os candidatos e recibos 0304/0305 ficam históricos.

## Precondições factuais conferidas após checkpoint 0311

O sinal UAC já foi dado e a instalação SQL Server 2025 Express local concluiu.
`LUCAS/MSSQLSERVER` está Manual/Running e
`localhost/ETL_SISTEMA_V2_SHADOW` está ONLINE, vazio e sem histórico Flyway.
TCP e Named Pipes continuam desabilitados; não há prova de transporte JDBC
restrito a loopback. A pendência de UAC citada na fotografia original desta
proposta foi resolvida, mas **não** resolve o pin: `AGENTS.md`, os dois perfis
shadow, lock, scripts e pacote candidato ainda fixam 12.8.1, suspenso por
Segurança. O par 12.8.2 global/normal não deve ser misturado com eles.

Além da decisão explícita do usuário para o delta abaixo, a futura execução
física exige nova qualificação offline do pacote e SCA, contrato de
autenticação integrada do Flyway revisado, schema V001–V104 e conexão JDBC
local comprovadamente sem listener externo. Nenhum desses gates é provado
pela instalação do banco nem por esta proposta.
## Delta exato após autorização

| Alvo ativo | Mudança delimitada |
| --- | --- |
| `AGENTS.md`, parágrafo **Prova Java/JDBC local autorizada** | Trocar apenas `mssql-jdbc_auth:12.8.1.x64` por `mssql-jdbc_auth:12.8.2.x64` e explicitar que o JAR do perfil usa `mssql-jdbc:12.8.2.jre11`; manter URL literal localhost/banco exato, duas travas Maven, Windows auth, gateway sintético e rollback. |
| `pom.xml`, perfis `shadow-local-integration` e `shadow-migrations-windows-auth` | Em ambos, trocar `mssql.jdbc.version` de `12.8.1.jre11` para `12.8.2.jre11` e `mssql.jdbc.auth.x64.version` de `12.8.1.x64` para `12.8.2.x64`. As propriedades globais já são 12.8.2 e ficam intactas. |
| Novo `docs/catalogos/macrobloco-qualificacao-pacote/dependency-lock.shadow-local-12.8.2.json` | Manter nove dependências e os oito componentes não JDBC; copiar entradas JDBC 12.8.2 do lock normal vigente, com origem/hash/tamanho/POM/licença verificados, e declarar escopo físico exclusivamente localhost. Preservar o lock 12.8.1 histórico. |
| Novo `PACKAGE-README-SHADOW-12.8.2.md` no mesmo catálogo | Manter contrato de extração, comandos offline, URL de processo e preflight; substituir o par de versões e registrar a suspensão até autoridade de execução. Preservar o README 12.8.1 histórico. |
| `scripts/validation/Invoke-QualificationBuild.ps1` | Em `PackageShadow`, selecionar o novo lock e exigir `12.8.2.jre11`/`12.8.2.x64`; emitir variante `SHADOW_LOCAL_12_8_2`. A fase normal continua `NORMAL_12_8_2`. Não executar fase física na preparação. |
| `scripts/validation/New-QualificationPackage.ps1` | Exigir variante `SHADOW_LOCAL_12_8_2`, selecionar novo lock/README e par 12.8.2. Para a variante shadow, excluir somente os POMs JDBC 12.8.1 históricos de `third-party/` e usar os POMs 12.8.2 já pinados nesse diretório; não copiar `third-party-shadow-local/` 12.8.1. Preservar todos os arquivos históricos. |
| `scripts/validation/Invoke-Qualification.ps1` | Trocar somente o guard do par físico JAR/DLL para versão e nomes 12.8.2. Manter a separação dos comandos offline e o preflight fail-closed. |
| `scripts/validation/Test-QualificationExtractedGuards.ps1` | Atualizar o nome do membro nativo usado no cálculo da contraprova de caminho, de 12.8.1 para 12.8.2; ambos têm o mesmo comprimento, mas a evidência deve refletir o membro real. |
| Runbook P08 corrente | Criar sucessor 12.8.2 com alvo/rollback e revisão novos; manter `p08-shadow-12.8.1-preflight-20260928.md` como histórico, sem reescrever 0304/0305. |

Os bytes 12.8.2 já pinados pelo lock normal são:

| Artefato | SHA-256 | Bytes | POM SHA-256 |
| --- | --- | ---: | --- |
| `mssql-jdbc-12.8.2.jre11.jar` | `a298b4b42a80c283961674cc37223b84cacecf5cc6abccf1319ebd7d2a2462df` | 1.219.348 | `21bf8059a26ce833e14500670049062344850dad259f0fc7ce120b86c4fb27a6` |
| `mssql-jdbc_auth-12.8.2.x64.dll` | `02db7b0053c4a65b622ef53eccb8b55687fda16ae808f96dc73acc7340c89c6e` | 318.120 | `223f6776fbe668640121080d2b709c68d0ab9a26e1e5474e7921bf79872828bf` |

O JAR declara MIT; a DLL declara Microsoft Proprietary License. A nova
variante mantém proveniência e licença conferidas byte a byte; a análise
jurídica e a baseline de Segurança continuam abertas. O escopo do CVE e a
correção em 12.8.2 constam nas [notas da Microsoft](https://learn.microsoft.com/en-us/sql/connect/jdbc/release-notes-for-the-jdbc-driver)
e no [NVD](https://nvd.nist.gov/vuln/detail/CVE-2025-59250).

## Prova e recuperação previstas

Após a decisão do usuário, criar um **novo** espelho e novos attempts, sem
reutilizar manifesto/ZIP/controle de 12.8.1. Conferir Maven effective POM dos
dois perfis e par normal, compilar com as duas travas opt-in sem URL, executar
testes focados, `clean verify` Java17 offline, formatter/Checkstyle/JaCoCo,
PMD/SAST e scanners nos bytes finais. Construir dois `PackageShadow` A/B,
comparar revisão/manifesto/ZIP, validar lock/SBOM/POM/licença/proveniência,
executar os comandos puros em JAR extraído, recusas dirigidas e guardas. O
relatório SCA P11 precisa cobrir o novo par e ser aprovado por Segurança.

Mesmo com pacote offline verde, JDBC aguarda autorização do novo pin,
schema e contagens conferidos, autenticação integrada revisada e prova de
transporte exclusivamente loopback. O sinal UAC e a instalação local foram
concluídos no checkpoint 0311; não se repete instalador nem `CREATE DATABASE`.
Nenhum comando físico é disparado por esta proposta.
Se um gate falhar, preservar o attempt e diagnosticar; se o alvo físico divergir,
parar sem DDL/JDBC. Rollback de código antes de qualquer execução física:
reverter somente o delta novo em novo attempt, conservar 12.8.1 histórico e
manter a suspensão; não apagar recibos, banco ou serviço. Qualquer efeito
físico futuro segue o runbook local autorizado com contagens antes/depois,
sintéticos e ROLLBACK, nunca `DROP`/`clean`/`restore`.
