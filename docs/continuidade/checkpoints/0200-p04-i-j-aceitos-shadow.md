# Checkpoint0200 — P04/I/J aceitos no escopo shadow

Data2026-09-20T22:14Z. Anterior0199, SHA-256 b86a8018ec3e4091c80373bef95e47c11c097e8255a0f13193d8803ab757c954.
Autoridade: ordem POS0198, ledger antes do código;48h até2026-09-22T21:34:04Z.
Somentelocalhost/ETL_SISTEMA_V2_SHADOW,Windows integrada,sintéticos,rollback;
semDDL/fonte/produção. Limite2P04+1P05/3campanhas,3600s cada;1P04consumida.

Preflight canônico18/18 e72offline PASS. P04#1:20IT/20unidades PASS,5XMLs
íntegros,exit0/sem timeout;Maven17m06s. Reserva21:52:44Z–22:10:09Z,abaixo3600s.
Recibos sucesso7etapas,cancelamento7,tardia5,com33/33/33/33/0 terminal;
19saídas por etapa,journals terminais,rollback e246agregados iguais.
Máxima etapa31.072s;sequências160.487/156.930/90.134s.2076arquivos de
fonte/banco sem drift,zero processo próprio. Auditoria0/453/0preservada.

I e J ACEITO_NO_ESCOPO local/sintético; aceite SHA-256 509a477ab2e14d29f61efd4640b1c6c549d31293dfe6070bdce776dc137cb49d
em target/P04-P05-POS0198-20260920-01/p04-acceptance.json.
39/45 e67/115 inalterados;nenhum paiV2,apply,recovery durável ouprodução.
Falhas do verificador(header.json/journal.lock classificados como eventos)
foram corrigidas somente no parser e preservadas;nenhum retry físico.
STATES/trilha/matriz/ledger sincronizados. P05 ainda não reservada.

1. Preparar/testar offline a guarda serial de P05 e snapshots agregados por
   escala, mantendo2/4/8/16 e limites;nenhuma mudança funcional de domínio.
2. Revalidar alvo,ledger/vigência/saldo/processos;reservar a única P05 por3600s
   antes do efeito. Parar efeitos na primeira escala falha.
3. Conferir quatro recibos/rollback/agregados/XMLs/bytes/processos e consolidar
   estado,trilha,matriz,validadores,checkpoint e entrega,semP06–P08.
