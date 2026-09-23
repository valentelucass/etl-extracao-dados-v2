# Checkpoint0075 — matriz relacional e reconciliação (2026-09-11)

EM_EXECUCAO. Objetivo A–J integral permanece; não encerrar entre frentes.
Anterior0074-relacional-primeira-cadeia-jdbc.md, SHA256
450144f07ea79b25b99fc5616935d197996422a026c977ab159f22054859a107.
Prompt/inventário/before/ e tentativas: target/macrobloco-relacional-20260911-01/.
Autorização vigente: localhost/ETL_SISTEMA_V2_SHADOW, Windows, migrations aditivas
fora da IT e todo DML sintético rollback-only. Sem API/.env/grants/reset/produção/
V1/scheduler/commit/push. Nenhuma confirmação pendente ou orçamento antigo usado.

V029–V035 instaladas, congeladas por migrations.json de cinco instalações:
schema-install-01, schema-evolution-install-01, schema-strengthen-install-01,
schema-completeness-install-01 (quatro diretórios, cinco evoluções numeradas
quando V029–V031 são vistas como grupo inicial). Usar os ledgers reais;
qualificação com rollback precedeu cada instalação. Não editar migrations.

physical-09:38 testes,37 passaram; referência pick0 foi rejeitada corretamente
pelo mapper existente. Fixture corrigida para pick7; zero sintético de item é
exercitado separadamente. physical-11:12 IT de fluxo +26 de matriz passaram,
sem falhas/erros/skips nesses dois conjuntos. Incluem comandos via API Java,
reabertura do adapter nas seis fronteiras, modos, gaps, bindings/cardinalidades,
revisão, presença/tipo, fila/leases/teto, fontes inválidas e duas sessões reais.

physical-11 escalas16/256 passaram com planos SQL reais, gauge V2-050 e rollback.
Escala4096 excedeu seu teto240s durante staging Fretes. Encerrado somente fork
JVM próprio confirmado; physical-11-stop.json e falha Maven preservados. Leitura
after.log igual a before.log confirmou restauração dos agregados. Não alegar
4096 aprovado nem recuperação durável. A observação mostrou custo crescente da
escrita física por registro no staging existente. Campanha seguinte declara
16/256/1024 e repetição256, teto240s por caso e deadline cooperativo; heap é
diagnóstico, não prova de platô. Nenhuma redução dos requisitos A–J.

V035 distingue ausência de captura de três capturas vazias válidas, verifica
proveniência da captura de hidratação e sela reconciliação contra evidência
incompleta. Adapter lê política SQL e não permite budgets divergentes. Hidratação
vazia adia tentativa; não registra sucesso falso. Essas últimas alterações e
novas contraprovas estão em verify-01, execução own com perfil físico, offline,
Java17/heap512MiB/teto900s, ainda sem resultado nesta fotografia.

Baseline/allowlists estáticos passaram com V034; foram ampliados explicitamente
para V035 depois da instalação. Não houve banco vazio/reset; equivalência de
fresh install vs upgrade física não foi autorizada nem alegada.
Runner JAR e scripts escritos; prova no processo ainda pendente. Main padrão
dormente. Test-RelationalLaboratoryJar.ps1 valida dez casos sem fonte real.

Próximas ações:
1. Consultar verify-01; corrigir falhas e concluir verify/IT/escala/JAR sem
   reescrever tentativas. Copiar somente formatter das fontes próprias.
2. Validar SQL/baseline/runtime/segredos, inspecionar planos e conectar delta
   legítimo aos validadores históricos por snapshots byte a byte.
3. Entregar matriz A–J, relatório, diff próprio/inventário final e continuidade
   sincronizada. Dependências externas de identidade/oráculo/aceite/Security/
   V2-041 seguem específicas. Sem B64, checkbox ou qualificação real nova.
