# DEV LOCAL sintético

Composição opt-in `DevLocalMain`:11entidades de exemplo passam pelos mappers/parser e regras existentes, com outputs e journal em arquivos locais. Cada comando abre seu próprio processo e encerra após o readback. Entrega qualificada: agregado offline atual, pacote extraído A/B e 24 comandos reais do launcher, incluindo as quatro escalas e os quatro cenários. [Matriz final P01–P33](MATRIZ.md): 23 recortes de fase e 35 unidades com recorte local verificado; 10 fases e 13 unidades opcionais excluídas, com motivo/impacto/trilha futura. Nenhuma dependência essencial ao DEV pendente; aceites integrais permanecem separados.

No diretório do repositório, use PowerShell e o JDK17 já disponível:

```powershell
$java = Join-Path $env:LOCALAPPDATA 'Programs/Eclipse Adoptium/jdk-17/bin/java.exe'
$launcher = './scripts/dev/Invoke-DevLocal.ps1'
$workspace = './target/meu-dev-local-01'
& $launcher -Java $java -Command init -Synthetic -Workspace $workspace -Config './config/dev-local.synthetic.json'
& $launcher -Java $java -Command run -Synthetic -Workspace $workspace -Execution bootstrap-01
& $launcher -Java $java -Command run -Synthetic -Workspace $workspace -Execution delta-01 -Mode INCREMENTAL -Previous bootstrap-01
& $launcher -Java $java -Command run -Synthetic -Workspace $workspace -Execution recomposicao-01 -Mode RECOMPOSE -Previous delta-01
& $launcher -Java $java -Command status -Synthetic -Workspace $workspace -Execution delta-01
& $launcher -Java $java -Command replay -Synthetic -Workspace $workspace -Execution delta-01
& $launcher -Java $java -Command scenario -Synthetic -Workspace $workspace -Execution ausencia-01 -Case ABSENCE_REACTIVATION
& $launcher -Java $java -Command stop -Synthetic -Workspace $workspace
```

O pacote DEV fica em `target/dev-local-package-b77/etl-dataexport-v2.jar`, com `lib/` ao lado. O launcher recebe `-Package` quando se deseja escolher outra cópia desse mesmo pacote qualificado. `init` exige diretório novo; o nome final deve ter 1 a 80 caracteres ASCII, começar por letra ou número e usar letras, números, ponto, underscore ou hífen; repetir inicialização no mesmo diretório recusa e preserva evidência. `status` e `replay` consultam ocorrências existentes. `stop` registra encerramento local; se receber `-Execution` de uma ocorrência STARTED, faz recuperação explícita dessa ocorrência. Não limpa arquivos nem altera serviços.

Teste erros com `-Command cancel -Execution cancel-01` e `-Command error -Execution erro-01` no workspace inicializado. Cancelamento termina CANCELLED; falha injetada termina FAILED/exit3. Recusa de input/config/opt-in retorna código diferente de zero. Para a proteção de ausência, use também `scenario` com `PARTIAL_REFUSED`, `QUARANTINE_REFUSED` e `OWNER_REFUSED`; o cenário completo conclui somente ao comprovar a recusa do apply no filho e a preservação do estado anterior. Consulte `reasonCode` e o estado do filho no resultado.

Configuração fechada e claramente sintética em [dev-local.synthetic.json](../../../config/dev-local.synthetic.json): escala2/4/8/16, lote1..16, in-flight1, bytes4096..4194304 e deadline1..60s. O padrão usa escala2/lote4/1MiB/30s e heap128MiB. `init` grava a configuração validada e seu hash. `run` usa essa configuração ou `-Config` explícito validado. Campo extra, duplicado, coerção, URL/JDBC ou opção de segredo recusa.

O sample embutido contém11entidades: Usuários, Manifestos, Coletas, Fretes, Cotações, Localização, Contas a Pagar, Faturas, Inventário, Sinistros e Raster. A fixture tem44linhas/22páginas,28aceitas/11quarentena/5filtradas e150comparações de oráculo literal. Escala2 processa56linhas aceitas replicadas como ocorrências SYNTHETIC. As observações de origem, valores e ambiguidades ficam visíveis; identidade canônica/completude de fornecedor permanecem não provadas. MC/CF usam declarações explícitas, sem associação por coincidência numérica.

Saídas em `runs/<execution>/outputs/result.json`, eventos e journal de metadata com hashes; confirme o caminho observado pelo comando. Estados/computação são materializados com teto finito, não streaming de massa produtiva. Métricas informam linhas retidas e bytes, heap no recibo e soma dos picos de pools (não RSS nem pico simultâneo). As33responsabilidades canônicas de sweep permanecem BLOCKED/apply=false. Somente cenários de política SYNTHETIC separada exercitam ausência/reativação local.

As seis famílias de bindings de dimensão e o catálogo sintético de19contratos são metadata local; não equivalem a cadastros/views SQL. MAT-01..05 permanecem `EXCLUIDO_DEV_LOCAL/SQL_ONLY/computed=false/applyCapability=false`, sem linhas de fatos inventadas. Plano P01–P33 e48unidades são reconciliados na matriz com recortes locais e exclusões individuais; aceites integrais permanecem separados.

Recuperação: consultar a ocorrência e seus outputs; resultado incerto permanece STARTED até recuperação explícita. Replay terminal conflitante recusa. Lock impede outro processo de abrir o mesmo workspace simultaneamente; links/junctions/corrupção/limites recusam, sem purge. Arquivos locais não comprovam durabilidade contra perda elétrica ou backup/restore SQL.

Rollback desta composição: deixar de usar o launcher/entrypoint DEV. `Main`, defaults strict, SQL/JDBC, credenciais reais e caminhos antigos conservam seus contratos. Este pacote DEV não autoriza fonte externa, SQL Server, produção, V1/PM2, deploy, migrations, mudança de versão/contas/permissões/serviços/TLS ou renovação de quota. STOP429/PAUSED e evidências históricas continuam preservados. [ADR0061](../../adr/0061-dev-local-sintetico-sem-infraestrutura-real.md).
