# Checkpoint0440 — run JDBC recusado por certificado alterado

- Data:05/10/2026, após16:52UTC; unidade `target/first-extraction-20261005/`.
- Anterior: `0439-sql-sample-pin-qualificado-diagnostico.md`, SHA256 `30FC33EE8A2B0616D09891B4ADF2E99A883D4061917FF9BFC4A80ABB63841F4E`.
- Objetivo vigente: pedido direto do usuário para rodar V2, corrigir causas com documentação/testes e obter a primeira extração; V1 será deixado parado pelo usuário.
- Estado: diagnóstico físico encerrado; execução Java/API independente em preparo, SAMPLE integrado bloqueado na identidade do certificado SQL.
- Autoridade/limites: `authority.json` e `jdbc01-dispatch.json` da unidade; nenhum orçamento Hermes reaberto. Diagnóstico1conexão/12SELECT/10s/60s/semretry. Proibições permanentes preservadas.

## Evidência observada

- Inventário inicial Git limpo em `initial-state.json`; Codex único editor docs/CI, Banco único executor SQL, Runtime e Fontes com arquivos próprios.
- Helper diagnóstico privado corrigido: checkpoints sem FROM exigem sessão limpa antes/depois do catálogo.74contraprovas e10 verificações controlador/IPC PASS; sem alteração de produção.
- Runtime corrigiu ausência de variável JDBC no lançador:14checks PASS; inputs finais em `runtime/inputs03/`, qualificados contra JAR existente.9JARs/2183entradas de build sem drift; sem Maven repetido.
- Root conferiu110pins privados/120produção/3externos antes do despacho. Fontes confirmou configuração provisionada e dia01/10/2026; nomes técnicos exclusivos da ocorrência adotados em STATES, sem certificação de tenant fornecedor.
- JDBC01 real:1tentativa,0SELECT,0HTTP,0escrita; `LEAF_CHANGED` antes da autenticação. Nenhuma Connection retornada. Processo encerrado, limpeza sem incerteza reportada, versão/hash do executável configurado preservados. Evidência `banco/execution01/` e `banco/physical-handoff01.json`.
- Pin BF723 antigo preservado; identidade SQL e contrato físico atuais não comprovados. Captura/adoção de nova folha ainda não executadas neste checkpoint.
- V1 conhecido sem PID/descendentes; cache PM2 parado, sem RPC/prova de ausência global. Nenhum PM2 alterado; não iniciar V1 que o usuário deixou parado.
- Nenhum checkbox, P08/G01, paridade, staging real ou cutover aceito.

## Próximas ações no mesmo pedido

1. Banco prepara captura pública limitada que sempre recusa confiança; Root revisa/despacha. Apresentar folha exata antes de qualquer decisão de confiança, sem autenticação/SQL.
2. Fontes/Runtime qualificam Java independente de SQL, usando gateways/contrato/mapper reais do JAR, per1/página1/1000linhas/cap2/60s e HTTPS estrito; Root reserva/despacha a leitura autorizada.
3. Conferir resultado real, registrar limites de camada e preservar todas as recusas. SQL integrado depende de decisão específica sobre nova folha; nenhuma renovação automática do pin.

Sem processos próprios remanescentes da JDBC01 ou efeitos desconhecidos reportados. CONTEXTO_GLOBAL ausente. O pedido continua ativo para o trabalho independente acima; este checkpoint não é encerramento do objetivo do usuário.
