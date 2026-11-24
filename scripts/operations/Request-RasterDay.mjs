import { open } from 'node:fs/promises';
import { request } from 'node:https';

// O corpo contém as credenciais do Raster e é recebido somente pela entrada padrão.
async function consultar() {
  const [endereco, arquivo] = process.argv.slice(2);
  const url = new URL(endereco);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash ||
      !/^\/datasnap\/rest\/TWebService\/%22getEventoFimViagem%22$/i.test(url.pathname)) {
    throw new Error('RASTER_ROUTE_REQUIRED');
  }
  const partes = [];
  for await (const trecho of process.stdin) partes.push(trecho);
  const corpo = Buffer.from(Buffer.concat(partes).toString('utf8').trimEnd());
  const resposta = await new Promise((resolve, reject) => {
    const consulta = request(url, {
      method: 'POST', agent: false, signal: AbortSignal.timeout(90000),
      headers: {'Content-Type': 'application/json', Accept: 'application/json',
                'Content-Length': corpo.length, Connection: 'close'},
    }, resolve);
    consulta.once('error', reject);
    consulta.end(corpo);
  });
  if (resposta.statusCode === 200) {
    const destino = await open(arquivo, 'wx');
    try {
      let tamanho = 0;
      for await (const trecho of resposta) {
        tamanho += trecho.length;
        if (tamanho > 10485760) throw new Error('RASTER_RESPONSE_TOO_LARGE');
        await destino.writeFile(trecho);
      }
    } finally { await destino.close(); }
  } else {
    resposta.destroy();
  }
  console.log(resposta.statusCode);
}

consultar().catch(erro => {
  const codigo = erro.code ?? erro.name;
  console.error('Falha na leitura HTTPS do Raster (' + String(codigo).replace(/[^A-Za-z_0-9]/g,'') + ').');
  process.exitCode = erro.message === 'RASTER_RESPONSE_TOO_LARGE' ? 2 : 1;
});
