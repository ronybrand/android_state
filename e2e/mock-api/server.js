#!/usr/bin/env node
'use strict';

// A stand-in for the Estado backend, for the Maestro end-to-end flows: the same
// routes, envelope and status codes the app uses, with its state in memory so a flow
// can create, edit and delete without touching real data. Zero dependencies, so the
// workflow starts it with plain `node`.
//
//   node e2e/mock-api/server.js [port]      (default 4010)
//   POST /__reset                           restores the seed data between flows

const http = require('node:http');
const { Buffer } = require('node:buffer');

// Demo credentials of the mock only; they open nothing real.
const CREDENTIALS = { username: 'admin', password: 'e2e-password' };

const SEED = [
  { id: 1, nome: 'São Paulo', sigla: 'SP', dataHoraCadastro: '2026-01-10T09:00:00' },
  { id: 2, nome: 'Rio de Janeiro', sigla: 'RJ', dataHoraCadastro: '2026-01-11T09:00:00' },
  { id: 3, nome: 'Minas Gerais', sigla: 'MG', dataHoraCadastro: '2026-01-12T09:00:00' },
].map((state) => ({ ...state, dataHoraUltimaAtualizacao: null }));

const plain = (text) => text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

// The token is JWT-shaped (the app only decodes `exp`); it is not signed.
function issueToken() {
  const exp = Math.floor(Date.now() / 1000) + 3600;
  const payload = Buffer.from(JSON.stringify({ sub: CREDENTIALS.username, exp })).toString(
    'base64url',
  );
  return `mock.${payload}.signature`;
}

function createMockApi() {
  let states = structuredClone(SEED);
  let nextId = states.length + 1;

  function send(res, status, body) {
    res.writeHead(status, { 'content-type': 'application/json' });
    res.end(body === undefined ? '' : JSON.stringify(body));
  }

  function readJson(req) {
    return new Promise((resolve) => {
      let text = '';
      req.on('data', (chunk) => (text += chunk));
      req.on('end', () => {
        try {
          resolve(text ? JSON.parse(text) : {});
        } catch {
          resolve({});
        }
      });
    });
  }

  const signedIn = (req) => /^Bearer .+/.test(req.headers.authorization ?? '');

  return http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const route = `${req.method} ${url.pathname.replace(/\/+$/, '') || '/'}`;

    if (route === 'POST /__reset') {
      states = structuredClone(SEED);
      nextId = states.length + 1;
      return send(res, 204);
    }

    if (route === 'GET /actuator/info') {
      return send(res, 200, { build: { commit: 'e2e0001', time: '2026-01-01T00:00:00Z' } });
    }

    if (route === 'POST /auth/login') {
      const body = await readJson(req);
      if (body.username === CREDENTIALS.username && body.password === CREDENTIALS.password) {
        return send(res, 200, { token: issueToken(), expiresInSeconds: 3600 });
      }
      return send(res, 401, { message: 'Invalid username or password.' });
    }

    if (route === 'GET /estado/paginado') {
      const search = url.searchParams.get('busca');
      const sort = url.searchParams.get('sort');
      let content = states.filter(
        (s) =>
          !search ||
          plain(s.nome).includes(plain(search)) ||
          plain(s.sigla).includes(plain(search)),
      );
      if (sort) {
        const [field, direction] = sort.split(',');
        if (field === 'nome' || field === 'sigla') {
          content = [...content].sort((a, b) => a[field].localeCompare(b[field]));
          if (direction === 'desc') content.reverse();
        }
      }
      return send(res, 200, {
        content,
        page: { size: 100, number: 0, totalElements: content.length, totalPages: 1 },
      });
    }

    const byId = url.pathname.match(/^\/estado\/(\d+)\/?$/);
    if (byId) {
      const id = Number(byId[1]);
      const state = states.find((s) => s.id === id);
      if (req.method === 'GET') return state ? send(res, 200, state) : send(res, 404, {});
      if (!signedIn(req)) return send(res, 401, {});
      if (!state) return send(res, 404, {});
      if (req.method === 'DELETE') {
        states = states.filter((s) => s.id !== id);
        return send(res, 204);
      }
      if (req.method === 'PUT') {
        const body = await readJson(req);
        Object.assign(state, {
          nome: body.nome,
          sigla: body.sigla,
          dataHoraUltimaAtualizacao: new Date().toISOString().slice(0, 19),
        });
        return send(res, 200, state);
      }
    }

    if (route === 'POST /estado') {
      if (!signedIn(req)) return send(res, 401, {});
      const body = await readJson(req);
      if (states.some((s) => s.sigla === body.sigla)) return send(res, 409, {});
      const created = {
        id: nextId++,
        nome: body.nome,
        sigla: body.sigla,
        dataHoraCadastro: new Date().toISOString().slice(0, 19),
        dataHoraUltimaAtualizacao: null,
      };
      states.push(created);
      return send(res, 201, created);
    }

    return send(res, 404, { message: 'Not found' });
  });
}

module.exports = { createMockApi, CREDENTIALS };

if (require.main === module) {
  const port = Number(process.argv[2] ?? process.env.PORT ?? 4010);
  createMockApi().listen(port, '0.0.0.0', () => {
    console.log(`mock API listening on http://localhost:${port}`);
  });
}
