# Pacote residual B54: 19 unidades existentes

Estado: **PREPARED_NOT_AUTHORIZED**. A sexta campanha encerrou com 109/128 reservas.
Este pacote prepara uma sétima campanha de até 15 minutos; não a abre. O módulo
de orçamento aqui é uma proposta isolada, não substitui o helper ativo e não
libera oitava campanha nem a reserva 129. A confirmação adicional diz respeito
somente a essa nova campanha, sem escolher novamente banco, contas ou grants.

Alvo exclusivo localhost/ETL_SISTEMA_V2_SHADOW, V001–V020 imutáveis, 5.328 linhas,
25 EXECUTEs, SERVICE v15/OPERATOR v1, scope original 1 v10 e demais sete v1.
Validade original preservada. Artefato v4 já instalado e diagnosticado; manifesto
f2fe15f0ddaa3f8f92c07aef54e909df917eb8e3055b652b40af375f99e1a3bd.
O JAR v4 passou em preview offline; execução operacional física ainda pendente.

| Reservas adicionais | Aplicação e verificação |
| --- | --- |
| 1 | Fonte loopback própria na porta da configuração já congelada |
| 2–3 | Papéis temporários SERVICE e dois scopes REPLAY; compensação reservada antes da alteração |
| 4–5 | Duas políticas DQ sintéticas de REPLAY, até cinco linhas por política |
| 6–8 | REPLAY Coletas/Fretes e repetição: publicação única, origem e watermark preservados |
| 9–11 | FORCE_RUN válido, parcial recusado e OPERATOR negado |
| 12 | Drift recusado, com alerta obrigatório conferido no SQL |
| 13–18 | Segunda janela antes da primeira e repetição da primeira, duas unidades por invocação |
| 19 | JAR com validação TLS desabilitada na configuração derivada: recusa e zero HTTP/SQL |

O controlador confere os arquivos por SHA-256, catálogo, contagens, perfil e versões
antes de abrir a campanha. A política é exportada pelo comando `plan`, sem efeitos.
As janelas usam os IDs já persistidos e uma nova autorização a cada execução.
Não há scheduler, DDL, grant novo, conta, senha, certificado ou renovação.

Aplicação após autorização: PowerShell 7 com UAC normal, executar
`Invoke-ResidualRuntime.ps1`, passando os hashes reais de `manifest.json` e do
bundle v4, e a autorização `OWNER_APPROVED_B54_SEVENTH_EXISTING_BALANCE`.
O parâmetro registra a aprovação do owner; fornecer essa string não substitui
uma aprovação que ainda não ocorreu. O agente obtém os hashes dos arquivos.

A habilitação excepcional encurta a validade do mapping até antes do deadline.
Snapshot, hash e compensação são gravados antes da alteração. Em `finally`, a
compensação exige o estado esperado, remove os papéis, restaura a validade original
e avança as versões. Os dois scopes novos permanecem revogados, sem limpeza.
`verify-retained-replay.sql` confirma exatamente esse perfil em nova conexão.
Conflito mantém a recusa; recuperação exige leitura do estado real, sem repetir
o provisionamento. Nunca reutilizar IDs, diretórios ou reservas anteriores.

O pós-teste preserva os 95 attempts, 75 publications e oito EXTRACTING de B53,
confere decisões/consumos/publicações por ocorrência, fronteira temporal, alerta,
catálogo e ausência de filhos/conexões próprios. Os arquivos e resultados da
quinta/sexta campanhas permanecem. Falhas independentes recebem resultado próprio.

Mesmo com 19 PASS, a matriz integral continua exigindo outras provas: artefato/ACL
e adulterações do JAR, lease/cancelamento/queda no pipeline oficial, falha física
do sink e todos os limites/fusos/período mensal auditado. Não há orçamento novo
nem autorização implícita para esses casos. Comparação real conserva seus gates.
