# Checkpoint0055 — B62 investigação real limitada

10/09/2026. Anterior canônico: [0054](0054-bloco61-fechamento-da-sucessao.md),
SHA-256 26d216872470ad0188666d292b19c20593040fd5f9ef296c12e27541a4fd3176.
Estado: **SOURCE_INVESTIGATION_COMPLETE_CHARACTERIZATION_PENDING**.
Critério do objetivo: V2-012a, Users preferencial/Coletas independente.

O usuário adotou B62 neste chat e depois instruiu: “nao faço ideia, vc deve
procurar ou usar .env para procurar nas apis”. Essa instrução posterior cobre
busca e investigação read-only limitada. V2-041 permanece aberto: não se afirmou
rotação, invalidação ou aceite de Segurança. O intake anterior e sua recusa
ATTESTATION_EVIDENCE_MISSING permanecem em target/b62-intake-20260910-155925/;
receipt SHA-256 c69a36ddcfdb84120f73af09c5aaae8ccb79d549cc08ea7e74ec7d2a3a42b23c.

Inventário desta rodada: target/b62-source-20260910-161039/inventory.json,
2.313 arquivos anteriores, before/ com quatro documentos/validator preservados.
Busca local encontrou .env legado com endpoints válidos e credenciais presentes;
última definição não vazia de chaves duplicadas, sem exibir/gravar valores.
No target, 82 fixtures e 21 referências a oráculos locais; nenhum export real
identificado. Atestado canônico ausente; não foi pedido que o usuário localize APIs.

Quatro chamadas seriais HTTP200/curl0, quatro reservas e quatro resultados:
schema Users, first5 Users, info6908 e página1/per2 de 6908 em 09/09/2026.
Budget próprio encerrado4/4, 180s/timeout30s, metadados1MiB/dados64KiB, sem retry
ou redirect. Corpo total38.809 bytes somente em memória; nenhum dado real no Git.
request.json, ledger.jsonl e source-summary.json preservam plano e resultados.
Sem reutilização de orçamento B60, SQL, runtime operacional, UAC, rotação,
escrita remota/produtiva, bootstrap, deploy, commit, push ou cutover.

Users: cinco IDs e names STRING, hasNextPage=true. IndividualInput.updatedAt
String observado, sem semântica temporal ou campo temporal no node comprovados.
Coletas: cinco linhas para dois IDs INTEGER, ROOT_ARRAY; status_updated_at
ausente e candidato Manifesto com quatro INTEGER/um NULL. Forma do contrato
operacional/B58 continua ENVELOPE_DATA_ARRAY; divergência antes do staging.
Nenhuma mudança silenciosa de release, identidade, presença, filtro ou watermark.

Sonda nova de uso único, dois positivos/oito recusas offline verdes; repetição
recusada antes de novo HTTP. Primeiro autoteste falhou por enumeração de array
unitário e foi corrigido antes de qualquer consulta; log preservado.
Dois novos testes Java sintéticos reutilizam parser/normalizer/consumidor B58:
preservação de expansão no normalizer e recusa de forma antes do mapper/staging.
Nenhuma fonte Java produtiva ou contrato histórico foi alterado.

Verify isolado offline Java17/heap512MiB: **1405/0/0/4**; Enforcer, Spotless,
Checkstyle, arquitetura e JaCoCo verdes, cobertura91,53% linhas/76,43% branches.
Skips: três symlinks condicionais e comando Cotações opt-in desabilitado.
Primeira invocação Maven falhou na interpretação PowerShell de -D; correção
de aspas, sem clean, logs verify-01/02 e java-verification.json preservados.

Sucessão: docs/catalogos/bloco62-investigacao/manifesto.json e
Test-Bloco62Investigation.ps1. Quatro snapshots documentais/de validação;
B61 recebe resolução histórica exata, mantendo seus hashes e recibos antigos.
Relatório público no catálogo; diff, scanner e gates finais privados em
final-checks.json/delivery-checks.json. Conferir seus exits: ausência/falha é
entrega pendente, nunca PASS inferido deste checkpoint. receipt final vincula
evidências próprias. Sem efeito externo desconhecido ou processo operacional ativo.

Roadmap preservado:67/115,48 pendentes,191 rotas,zero AGORA. V2-012a/b/c,
V2-041 e Q-USR-01/Q-COL-01 seguem abertos; Q-MAN-01 mantém EXTERNAL_HOLD.
Users SHADOW_UPSERT_ONLY não prova snapshot completo, ausência ou exclusão.

Até três próximas ações:
1. Conferir sucessão e registros finais; não repetir a campanha encerrada.
2. Revisar binding/release da forma/path Coletas e ligação test-only à fonte;
   usar o achado real e regressão existente, sem converter normalização em aceite.
3. Preparar oráculo independente/representativo e comparação por rota, tratando
   V2-041 com seus responsáveis. Fonte funcional e schema observado não fecham
   esses critérios; trabalho offline não depende de nova consulta à API.
