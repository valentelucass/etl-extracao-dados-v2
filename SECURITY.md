# Política de segurança

## Estado suportado

O V2 ainda é uma implementação em sombra, sem release ou distribuição suportada. A ausência de release não reduz a necessidade de tratar vulnerabilidades e exposições com confidencialidade.

## Como reportar

Relate suspeitas por um canal interno restrito já aprovado pela organização, direcionado somente aos papéis necessários de Segurança e ao owner operacional afetado. Se nenhum canal desse tipo estiver disponível, interrompa a investigação e peça ao owner que indique um; não abra issue, discussão ou pull request público.

O registro inicial deve conter apenas:

- componente e classe do problema;
- impacto potencial e passos mínimos de reprodução sanitizados;
- versão, branch ou SHA, quando existir;
- forma segura de contato com o relator.

Não inclua segredo, valor parcial, hash de credencial, payload, URL sensível, tenant, host, cursor, documento ou identificador de negócio. Evidência detalhada deve permanecer no armazenamento restrito aprovado.

## Resposta e contenção

- Preserve evidência sem copiar valores sensíveis para Git, terminal compartilhado ou chat.
- Interrompa chamadas, deploys e releases que possam ampliar a exposição.
- Não rotacione credencial, reinicie consumidor nem execute health check externo sem owner, janela, rollback e autorização correspondentes.
- Coordene por classe de credencial, valide a saúde do único writer legado e comprove a invalidação anterior por mecanismo autorizado.
- Execute os scanners locais com saída redigida conforme `docs/runbooks/conter-e-rotacionar-segredos.md`.

O incidente registrado em V2-041 permanece em `EXTERNAL_HOLD` para rotação, invalidação, rede, release e deploy. Esse hold não autoriza reutilizar os valores e não impede validações puramente offline já liberadas em `STATES.md`.

## Identidade e autorização do runtime

Toda ação operacional protegida do entry point oficial deve atravessar o boundary do composition
root, inclusive quando o JAR for chamado diretamente por esse entry point. O estado atual é
`deny-all`: provider, authority, principals e
mapeamentos RBAC continuam `EXTERNAL_INPUT_REQUIRED`. Identidade ou papel recebido por CLI,
propriedade de sistema, arquivo de configuração ou ambiente não é confiável.

As decisões devem falhar fechadas quando credencial, provider, política ou auditoria estiverem
ausentes, inválidos ou indisponíveis. A auditoria usa somente referência opaca e nunca registra
credencial ou claim bruto; a pseudonimização dessa referência ainda deve ser provada pelo adapter
externo aprovado. Migrations e cutover usam processos e identidades separados,
fora do runtime JAR. Veja o
[modelo de ameaças](docs/seguranca/modelo-ameacas-autorizacao-runtime.md).

Não há SLA público documentado neste estágio. Prioridade, comunicação e fechamento são definidos pelos owners no registro restrito do incidente.
