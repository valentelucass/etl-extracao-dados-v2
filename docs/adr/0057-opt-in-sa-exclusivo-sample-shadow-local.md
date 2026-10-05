# ADR 0057 — Opt-in SA exclusivo para SAMPLE shadow local

Data: 03/10/2026. Decisão de escopo adotada; implementação e validação serão
referenciadas no fechamento da unidade Hermesfe74ee193a8d em STATES.md.

O usuário confirmou `localhost/ETL_SISTEMA_V2_SHADOW`, escolheu `sa` e autorizou
o preparo seguro desse caminho. A composição existente exige autenticação
Windows, portanto a escolha não pode ser atendida apenas trocando a URL.

O SAMPLE deve receber um opt-in separado de autenticação SQL, mantendo Windows
como caminho original. A credencial vem de provedor externo ao repositório e
não integra URL, argumentos, recibos ou logs. O novo caminho exige alvo local
exato, identidade escolhida e TLS com certificado validado. Preflight Windows
não serve como prova de identidade SQL; a prova física futura deve verificar
banco, identidade e demais gates no modo realmente escolhido.

Preservam-se staging-only, conexão compartilhada com commit bloqueado, readback
na mesma ocorrência, rollback e verificação independente de fechamento. O
`Main` padrão permanece sem DataSource. Não alterar versão SQL Server, segredo,
login, grants, listeners, TLS do servidor, schema ou Flyway.

Testes sintéticos devem cobrir opt-ins ausentes, produção/remoto, URL com
credencial, TLS relaxado, segredo ausente, identidade divergente e regressão do
caminho Windows antes de conectar. A qualificação offline não concede READY,
conexão ou liberação de STOP429. Cada efeito físico futuro conserva autorização,
reserva, teto e recuperação próprios; não reaproveitar unidades encerradas.

Recuperação de implementação: retirar o opt-in e sua composição isolada,
preservando Windows e sem efeito de banco ou serviço. Histórico, falhas e
manifests permanecem íntegros; não alegar revisão humana nem prova física.
