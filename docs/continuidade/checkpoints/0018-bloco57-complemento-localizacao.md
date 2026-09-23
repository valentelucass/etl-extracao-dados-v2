# Checkpoint 0018 — complemento B57: caminhos LOC confrontados

Data: 2026-09-09. Anterior: docs/continuidade/checkpoints/0017-bloco57-fechamento-local.md; SHA-256 71a18971f76167ed4fa36563f9642388b8f63771d925442eb4cbaf72feaaaa8e.
Objetivo: fechar F1/F2/F3 da auditoria local antes de escolher o próximo bloco.
Prompt: target/preparacao-bloco57/PROMPT-BLOCO-57.md, frentes B/D e preservação.
Instrução efetiva: “pode fechar, vc tem autorizacao de buscar as informacoes,
vc tem as documentacoes para finalizar esse bloco de forma segura”.

## Autorização, ação e preservação

Trabalho local de código/testes, documentação e reconciliação. Nenhuma consulta
real, SQL, campanha física, instalação, credencial, agenda, commit/push ou renovação.
Inventário inicial: target/bloco57-complemento/initial-inventory.json, 1.463 cópias
verificadas. Ação anterior aos efeitos: target/bloco57-complemento/action.json.
STATES observado e STATES entregue no B57 são versões distintas, ambas conservadas
em docs/continuidade/historico/bloco57-complemento. Não se atribui autoria ao drift.
Recuperação: comparar initial e hashes finais; restaurar só alterações próprias,
sem perder edição posterior. Manifests, ledgers e fotografias anteriores intactos.

## Alterações, decisão e provas

F1 TESTADO_NA_CAMADA: mesmo consumidor confronta raw String e JsonNode do mapper LOC.
Novo LocalizacaoCharacterizationPathTest atravessa também o contrato sintético B55,
streamer, caso de uso real e sink local em memória; nenhum SQL ou runtime físico.
Para números JSON 0/1, String é válida, JsonNode recebe
UNVERIFIED_NUMERIC_WIRE_LEXEME e o contrato sintético barra antes do staging.
String "0" e null atravessam o pipeline local. Não remover a proteção lexical nem
reconstituir léxico por toString. Tipo aceito em fixture não é contrato real ESL.
Contraprova: recusa numérica não passa como expectativa de registro válido.

Maven offline test, Java 17, heap 512 MiB, POM equivalente com saída isolada:
14 testes, zero falhas/erros/skips; exit 0.
Logs: target/bloco57-complemento/test-f1-01.log e test-f1-01-exit.json.
Relatórios sanitizados: target/bloco57-complemento/reports/localizacao-paths-*.json.
Formatter focado passou; verify global do complemento ainda não executado.
Efeitos externos desconhecidos: zero. Processo próprio de testes encerrado.
Aceites: nenhum; 67/115, 48 pendentes; Q-COT/Q-LOC/Q-FRE e V2-012a continuam abertos.

## Retomada imediata — até três ações

1. Entregar entrada executável futura COT 6906, limitada e ligada ao consumidor;
   provar offline recusa de inputs ausentes, limites, falhas e não repetição.
2. Sincronizar STATES/trilha/validadores por sucessão exata; preservar ambas as
   revisões STATES e manifestos B57 anteriores. Gate atual está pendente até isso.
3. Executar verify offline isolado e checks estáticos finais; emitir diff próprio,
   recuperação e recibo. Encerrar quando não restar trabalho local elegível.

Bloqueios externos COT: host/scopes, janela/garantias, oráculo representativo e
adoção específica. Documentação genérica não preenche esses campos por inferência.
Não executar fonte real neste complemento; nenhum Bloco 58 iniciado.
