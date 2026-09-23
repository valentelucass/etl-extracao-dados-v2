# Checkpoint0052 — validadores correntes e inputs de paridade

10/09/2026. Anterior: [0051](0051-bloco61-fronteira-manifestos.md), SHA-256
148008f270350be0883f7291f08831818befd659d747eb3a459ae6e7f5b99384.
Mesmo objetivo e autorização B61 A–D. B=TESTADO_NA_CAMADA offline;
C=PREPARADO_LOCALMENTE_ORACULOS_PENDENTES; D ainda EM_EXECUCAO.

Inventário e logs: `target/b61-local-20260910-144800/`. O runner corrente
`Test-RuntimeLocal.ps1` passou nos seis checks, com resultado em
`target/runtime-local/f1777360647241958cf19cb8666277ae/result.json`.
As 30 contraprovas incluem seis falhas do controlador real. Contra o módulo
anterior, a mesma regressão recusou `B61_RECOVERY_LOST_READBACK_STOP`:
cleanup falho suprimia registros dos filhos. Log `b-controller-red.log` preservado.

`RuntimeObserverContract` avalia os predicados reais do AST de SQL059 sobre
variáveis/tuplas sintéticas. Testa limite temporal, NULL/NULL e divergência de
cada coluna; mutações reais do SQL são recusadas. Não executa SQL nem comprova
o motor SQL ou o observador inteiro com duas JVMs. SQL059/060 são preservados;
057/058 continuam históricos. Comparadores compartilhados agora distinguem
NULL/vazio/campo ausente. O índice é `docs/runbooks/validacao-runtime-corrente.md`.

Matriz C: `docs/runbooks/bloco61-matriz-paridade.md`. Q-USR-01 é a candidata
preferencial pelas dependências locais atendidas; Q-COL-01 pode precedê-la se
seu oráculo chegar antes. Nenhuma está AGORA. Q-MAN-01 mantém EXTERNAL_HOLD.
Q-FND-01/02 e consumidores B58 são reutilizados; não existe nova fundação.
Fonte-oráculo, scope/binding, segurança, janela/volume e aceites nominais faltam.

O primeiro verify completo chegou a 1403 testes, 0 falhas, 5 erros por ausência
do teto obrigatório de heap de 512 MiB, e 5 skips. Logs/reports preservados em
`verify-01.log`/`verify-01-reports`. Reexecução com Java17, `_JAVA_OPTIONS=-Xmx512m`
e opt-in de recibo sintético está em andamento no mesmo build isolado. Sem clean,
perfil físico, SQL, fornecedor ou efeito externo desconhecido.

Nenhum checkbox/rota/aceite agregado fechado. B60 mantém seu aceite local e
controlador histórico NOT_QUALIFIED. Sucessão pública preparada para exatamente
11 arquivos existentes, com bytes anteriores de B61-preparação preservados.

Próximas ações:
1. Conferir o verify corrigido, cobertura, skips e warnings; corrigir falhas locais.
2. Sincronizar STATES/trilha/RETOMADA, relatório e sucessão exata; executar gates D.
3. Entregar diff contra inventário, checkpoint final e inputs externos pendentes.
