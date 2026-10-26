# Checkpoint 0603 — Controle Statement passou; banco274 aprovado localmente

25/10/2026. Base publicada:bfe9c0ff767aec99c872b47ed61a952b1b049cb3.
Etapas3/4 abertas. Nova conexão diagnóstica C78 sem Source/DML/DDL/commit.
Ainda não há ensaio completo de gravação/readback/ausência8 aprovado.

## Controle causal real da inicialização

Diagnóstico V2, manifesto
3F519C54B37C286578B231EF14CE71ED93AB6100256B2E44DBC715E7185FCF41.
Troca INIT por Statement.execute com o mesmo literal e fechamento TWR;
consultas continuam preparadas. Corrige a margem antes da alocação e a
classificação genérica quando o delegate do tee falha. Sem mudar driver,
JAR, servidor, configurações globais ou o runtime bancário congelado.

Os14 próprios passaram:original0/14.559ms/pico497.045.504B,1GiB4min,
0Source/SQL/provedor/native. RootSqlPrecheckOwn14Qualification0603.json.
Revisão independente confirmou5 arquivos/305pins,16 classes e a adaptação
mínima do controller0603; sem bloqueantes funcionais. Não é qualificação SQL.

A execução real sql-precheck-native-root0603-01 passou o prefixo completo:
- INIT literal por Statement; LOCK_TIMEOUT_ZERO=TRUE.
- STATE com cinco tipos e predicados aceitos, linha única, identidade vinculada.
- Posse dos11locks OBJECT/X/TRANSACTION confirmada antes e depois do STRICT,
  com a mesma identidade de transação.
- STRICT35 concluiu35SELECTs/um passe no catálogo real,13tabelas inspecionadas.
- Rollback original ACK1, conexão/child/stdin fechados normalmente, socket exato
  ausente e SQL16.0.1000.6/PID/início inalterados.

Child0 e original0/19.941ms/pico295.718.912B,1GiB4min,sem timeout.
Uma conexão/provedor1,0Source/DML/DDL/commit. Sem IDs transacionais ou mensagens
brutas persistidos. RootSqlPrecheckStatementControl0603.json.
Diagnóstico73C9348DDED18936244BCE371FDAE2FFE39F917B299C541F2890CC69BD42BC72.

0602 havia observado LOCK_TIMEOUT_ZERO=FALSE com INIT preparado. O controle novo
observa TRUE e completa o prefixo com INIT por Statement, conservando as demais
regras. Isso demonstra a correção deste caminho, coerente com o escopo SET/RPC
documentado em0602. Não reescrever o predicado antigo0601, que não foi retido.
Ajuste de precisão: em0602 só os11comandos retornaram; a posse dos11locks foi
demonstrada agora em0603.

O diagnóstico não chama registro de execução, staging ou begin() do ensaio.
bankQualified=false,retainedChangesOutcome=NOT_ASSESSED,sem ausência8.
Unidade0603 aposentada sem replay; prefixo aprovado não é gravação aprovada.

## Correção mínima integrada no Bound V14

Candidato externo manifestos-cotacoes-cleanup-p2-statement-bound-v14-candidate,
selo9BC7EDE59E801FA399F206941BFE00A05A6829D1BF25D96E331D8F294DA5209B.
Dos43Java anteriores, só GuardedBankSessionCandidate mudou: inicializador
fechado sem argumento SQL, INIT literal via Statement/TWR, charge e querySeconds
reais antes da criação; prepare recusa INIT. RollbackOwner/Source/JAR654B iguais.
Os11 fontes de modelos/fixtures anteriores têm hashes idênticos.

Passaram274 próprios,271 reexecutados mais3 novos causais do método real, e
dois preflights de arquivos, Manifestos/Cotações. Original1GiB4min,
exit0/130.680ms/pico710.135.808B,sem timeout.0Source/SQL/provedor/native.
RootBankV14Own274Qualification0603.json.
Qualificação3BAE4DC815CF1EE9A73AFFC0ED360460AF160566C872CD43BB620C1778F57F2F;
originalBF3BCBEC7EE03985BEBBD68999CBB1BBAC10F4B9C5DE51F158A8A81E649EB2E2.
O campo caseCount=81 é um componente; ownTotal=274 é o total executado.

Revisão independente122arquivos/1107pins sem bloqueantes, relatório
8C98AB924C98006D1B9AD9E1CA7F0966D24EE262D6E2B9B4B54A45B75F4AFBC5.
V13b/own271/V4/STOP preservados. WrapperV5 em preparo para consumir BoundV14 e
último par Source0601 exit1/delta3; não reutilizar a projeção antiga COT.
Não há SourceCODE, janela ou resolução positiva novos nesta etapa.

## Integração: acompanhamento real aprovado; pacote ainda recusado

As JVMs temporárias44484 e67396 encerraram; voltaram as cinco identidades F7EF.
Tentativa de emitir seleçãoV3 foi recusada antes de gravar porque a contagem já
era5. RootCurrentExternalJvmCimSelection0602.json não existe. Nenhum processo foi
parado pelo Root e nenhum perfil6 foi adotado. R29/R30/R31 permanecem congelados.

Capture R29 real passou após nova observação das cinco tuplas:original0,
1.893ms/pico96.284.672B,worker36608 igual ao original,10samples,duas barreiras
frescas,produtor no mesmo Job e fechado normalmente. Máxima idade404.621µs e
consulta297.162µs,limites preservados.0SourceSQL/0encerramentos externos.
RootR29CaptureQualification0602.json. A declaração anterior de Capture ainda
não iniciada era o estado do checkpoint0602; esta é a execução posterior.

R30Prepare também passou:original0/88.091ms/pico35.631.104B,
2741arquivos compilados conferidos,JAR15AB5FF74440772205EE3BF60F55049DAF6E30740CB97B0915351CC4C4EDFF28.
Inputs3EC9681D65399C01C046D7A919746C7304DB4C1D012E23FFFA7AEFBEF6D07031.
RootR30PrepareQualification0603.json. Sem executar testes nessa preparação.

R30Run posterior falhou:original2/60.296ms/pico651.939.840B,1GiB6min,sem
timeout ou excesso do teto. Duas classes do runner compiladas/compile.log0;
junit-result e worker-result ausentes. O catch genérico não reteve o predicado,
e finally pode substituir a falha original. Não inferir erro PM2/CIM ou sete
testes aprovados a partir disso. Original72492/Java53252/produtor8268 observados
ausentes depois; fechamento gracioso do produtor não comprovado pelo recibo.
RootR30Direct7Failure0603.json. Unidade encerrada e preservada.

Novo diagnóstico em preparo: trace somente observacional do arquivo R25 pinado,
com códigos literais/tipos/etapas limitados, primeira causa e limpeza separadas.
Mesmo worker/inputs/testes/guard/limites,sem mensagens brutas ou leitura de
argumentos/ambiente. Não modificar congelados nem admitir retry automático.
Bind/Root111/Clock4/Main7 e adoção continuam pendentes.

## Continuidade

Última Source real continua MFT0601,exit1/fonte terminal315/17manifestos/3consultas.
SQL0602/0603 não são Source. Delta3 e contadores globais parciais preservados.
INFO5 atualiza somente arquivos do consumidor desse predecessor; sem emissão.
Usuários54.540/seis quarentenas/hasNext,sem EOF ou replay.

Root único executor pesado/API/SQL/browser/Git; demais somente arquivos.
ESL/Chrome exclusivamente consulta,sem novo Excel ou paridade aprovada.
Sem DDL,commit retido,implantação ou cutover. Versão16.0.1000.6,contas,
permissões,serviços,certificados,V1/WMS/PM2/produção preservados.
Artefatos reais privados fora do Git; commit e push das alterações revisadas.
