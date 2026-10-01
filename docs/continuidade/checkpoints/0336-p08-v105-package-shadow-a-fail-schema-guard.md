# 0336 — P08/V105: smoke PackageShadow A falhou no guard físico

- Data: 2026-09-29 UTC; anterior: [0335](0335-p08-v105-063-064-baseline-observacional-pos-it.md), SHA-256 `AC96D08DD8473F820879E33E19507C9A110329ECE10B7BE6996AFD7D00A3B296`.
- Autoridade: Supervisor após 0335 autorizou smoke físico controlado dos candidatos A e B nessa ordem, condicionado a preflight e readback por run; B só após A integralmente reconciliado e PASS. Sem retry, restore, DDL, Flyway, fonte real, remoto ou produção.
- Estado: **A FAILED terminal; B não elegível e não extraído. P08 aberto.** PASS 5/5 da IT 0333 e 063/064 0335 permanecem evidências distintas; o FAIL estrito de 0333, outros FAILs e limites do backup 0325 permanecem preservados.

## Preparação e limites

Os dois candidatos `target/macrobloco-p08-package-v105-0332-20260929-01/candidate-{a,b}` tinham ZIP SHA-256 `0AE6403D42154FCDEA2924E2537BC37C2400B9DD17968A69488B47FB7D3084C2`, manifesto `0EE1040618E0962ED9CC2154CF507C62199653C5A95A1A6415626E67C06E51A0`, revisão `8AA4C98E244586C25F7459CDD07245D5D332966E47CF2AABDB434BDCFAD9FB3D` e V105 `29D6612E42DAE014388EB3384F46F4A30D0402FAC66E8548806121E1D4E3DB17`. Os 4.081 inputs registrados em A/B eram idênticos; apenas `STATES.md`, TRILHA e RETOMADA tinham drift de origem devido às atualizações compartilhadas posteriores à montagem. Nenhum input de código, migration ou payload empacotado mudou. Recibo privado `p08-v105-0336-input-drift.json`, SHA-256 `92A673C37D9A57A39DF9B13D5FEB7E039D1F1A96A2FA1D11746165E17DA12498`.

`Expand-QualificationPackage` extraiu **somente A** em `target/p08-0336-a`, com 189 membros e manifesto pinado. Controle novo irmão `target/p08-0336-control-a`; pacote e fontes não foram editados. A configuração externa ao payload `target/p08-0336-config-a.json`, SHA-256 `48296E69596555FFDE48B215FBF8F06CDF1A5254323D96CE51060D644B10C636`, reduziu `campaignSeconds` de 3600 para **420**; preservou heap 512 MiB, caso 300 s, query 60 s e socket 90000 ms. Campaign empacotada: duas raízes sintéticas e 19 saídas. JDK 17.0.20.1 e PowerShell 7.6.6 ficaram no ambiente do processo, URL JDBC de seis segmentos somente no ambiente privado, host/banco exatos e Windows auth; log sem URL.

Uma primeira tentativa de invocação de extração falhou no parser PowerShell antes de chamar a função; destino/controle ausentes. Depois, um primeiro invocador de `run` recusou a forma da URL **antes de iniciar processo filho/JDBC**, sem controle ou logs. As duas falhas foram preservadas no ledger. O invocador privado foi corrigido e passou self-test offline: seis chaves esperadas, sem identidade, filho não iniciado. Não houve repetição de `run` do pacote.

## Gates físicos e readback

Antes da execução efetiva A, preflight novo `lpc:localhost` por Windows auth confirmou master/alvo exatos, serviço `MSSQLSERVER` Running PID 20404, listeners somente `::1`/`127.0.0.1`, zero consumidores; histórico Flyway 106 = SCHEMA + 105 SQL/zero falhas, 1819 objetos/247 tabelas/147 linhas, sete schemas/principals V2, contagens monitoradas de domínio/auditoria zero. 063 do 0335 permanecia PASS; 064 pré-run repetiu SHA-256 `A0B50FFD1CB4FBDB3F8E51D5875FC3B7094B905F71741C7271CC3E3A90F8B865`. Inventário de 873 grupos de estatísticas repetiu `BAEB5ED431D478B5537B4939F51AB203C223902070C6AC011188EE925BFD8717`, sem stat novo. Master/alvo/contagens normalizados repetiram os hashes do checkpoint 0335.

Uma chamada real `Invoke-Qualification.ps1 -Command run` da extração A, com manifesto pinado, campaign empacotada, configuração externa e controle novo, saiu **2** em cerca de cinco segundos. O controle registrou `smoke=FAILED`, `CASE_EXECUTION_EXCEPTION`, `SQLException` / `QUAL_PHYSICAL_SCHEMA_DRIFT`, `testPassed=false`. Recibo e reconciliação confirmaram terminal, `rollback=true` e agregados antes/depois iguais. O journal terminou em `TERMINAL FAILED`. Output do invocador SHA-256 `A13F8E1A92D4FC9C7FC779757CBE8F41713107AC89A4818806E7A86CAA2E93D3`; recibo do caso `14EAFC797E95C317FAD655015B5EA2902D5C75617E4AC9E0F0261E67C5CE6B2C`; reconciliação `209D23237B74C22588E5562FEC890E2865E249E67899DFBDEA10AC8DCF0306A1`; journal final `644ADC084FECF54D04F6226753422F9A8DC15D0718C1DFD6F90E1C75A6E88CCA`.

Readback SQL/socket externo **após o run** repetiu master/alvo/contagens/064/stats exatamente: 106 linhas Flyway, zero falhas, dados/agregados, objetos, principals e 873 grupos de stats sem delta; listeners loopback. Nenhum `status`, `resume` ou `compare` foi chamado, pois `run` não saiu 0 nem ficou PASS. B não foi extraído ou executado.

## Diagnóstico causal somente leitura

O guard `QualificationPhysicalMetadata` do JAR usa contrato imutável `physical-columns.v098.json` com 971 colunas, embora o pacote declare schema V105. Consulta única catalog-only aos metadados das views `pub.analytic_lab_sql_01`–`19` confirmou também 971 colunas. Primeira divergência material: `analytic_lab_sql_01`, ordinal 4, collation esperada `Latin1_General_CI_AS`, atual `Latin1_General_100_CI_AS_SC`. Comparação de campos não textuais encontrou **181 divergências dessa collation e cinco de comprimento em bytes** (uma 28→56, quatro 2048→4096). Nomes Unicode não foram usados para a contagem de divergências, pois a saída do `sqlcmd` usou codepage local. O catálogo saiu 0, SHA-256 `E544D351AF565D3D5BD58378FF61C0AD03CB1A2AC7C97ED2B01D09DD9E3F080D`; inventário de stats imediatamente após e readback master/alvo/contagens permaneceram iguais. Isto prova o motivo imediato da recusa do pacote, **sem concluir que V105 causou o drift ou que o schema deva ser alterado**. Contrato V098, baseline/migrations e ambiente SQL Server precisam de revisão offline antes de novo pacote ou nova tentativa física.

Ledger físico privado `target/shadow-local-rebuild-20260928-01/p08-v105-0336-smoke-ledger.jsonl`, SHA-256 `576EF9555C13922A70C7271C0E5633FDF3F94E8A9E3E2B6345F97DDE1358952A`. Logs/controle/consultas privados foram preservados. Nenhum retry, B, restore, estatística removida, DDL, Flyway ou acesso externo ocorreu.

## Retomada — até três ações

1. Runtime e Banco revisam offline o contrato físico V098 contra migrations/baseline V105 e catálogo desta instância, mantendo as assertions; Supervisor decide a correção e os novos pins.
2. Somente após pacote revisado e autorização própria, planejar nova tentativa física A com caminho/controle novos; B continua condicionado a A PASS e readback sem delta.
3. Preservar backup 0325 e sua limitação: VERIFYONLY/HEADERONLY/FILELISTONLY/msdb concordam, mas ausência prévia do arquivo, tamanho físico e SHA não foram comprovados; restore não foi testado.
