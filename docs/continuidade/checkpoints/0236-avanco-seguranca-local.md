# Checkpoint 0236 — avanço técnico local

Data UTC: 2026-09-22T04:24:26.114Z. Estado TESTADO_NA_CAMADA unitária/offline; fechamento documental em andamento. Anterior:0235-p09-p33-entrega-local-conferida.md;SHA-256 6663691abc4bb544fa5e504009206c7b671526678529bf0faa73c149edff99bf.

Objetivo vigente: avançar P09–P33 com rapidez e segurança. Usuário pediu "pode começar a executar para avançarmos"; cobre construção, decisões técnicas e testes locais. Esta unidade não cria autorização produtiva nem aceite de terceiros. Baseline3811 conferido; cópia privada completa em target/avanco-seguranca-20260922-01/before.

Oito defeitos corrigidos,24regressões novas; 2186 casos/4skips históricos,zero falhas/erros.13casos intermediários PASS,mas unit02 teve5erros da JVM filha sem teto512MiB;unit03 corrigiu o ambiente privado e parou no EmptyBlock,unit04 valida o fechamento ajustado. Primeira tentativa falhou no filtro Spotless e causa foi corrigida. PMD10regras/653fontes mantém37alertas;12contraprovas PASS,sem supressões. Evidência e alterações exatas:../avanco-seguranca/resultado.json;recibos completos target/avanco-seguranca-20260922-01/. Revisão por agentes é técnica, não aprovação humana.

Limites:4tentativas locais600s cada(uma corretiva adicional após EmptyBlock,registrada antes do efeito);PMD máximo8execuções180s cada;nenhuma campanha física;zero SQL/rede/segredos/produção. Logs/processos das tentativas são a fonte para reconciliar qualquer resultado desconhecido. Recuperação:snapshots before e manifesto;sem reset/limpeza de material anterior. P07/P08 antigos históricos após deltaJava;39/45 e67/115 preservados.

Próximas ações:1.validar sucessão/trilha/scan e fechar recibo;2.qualificar os bytes alterados na camada física antes de promoção;3.admitir evidências externas pertinentes aos16requisitos e demais critérios da matriz,sem inferir aceite. Nenhum bloqueio externo foi usado para interromper a correção local.
