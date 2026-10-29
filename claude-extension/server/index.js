#!/usr/bin/env node
// Minimal MCP server (stdio, newline-delimited JSON-RPC) that lets Claude Desktop open the installed EduTrack app.
// No dependencies on purpose: Claude Desktop runs it with its built-in Node.js.
'use strict';

const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const readline = require('node:readline');

const SERVER_INFO = { name: 'edutrack', version: '0.1.0' };

const TOOLS = [
  {
    name: 'open_edutrack',
    description: 'Open the EduTrack desktop app (learning goals, roadmaps and study sessions) on this computer.',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    annotations: { title: 'Open EduTrack', readOnlyHint: true, openWorldHint: false },
  },
];

function candidateExecutables() {
  const candidates = [];
  if (process.env.EDUTRACK_EXE) candidates.push(process.env.EDUTRACK_EXE);
  if (process.env.LOCALAPPDATA) candidates.push(path.join(process.env.LOCALAPPDATA, 'EduTrack', 'EduTrack.exe'));
  if (process.env.ProgramFiles) candidates.push(path.join(process.env.ProgramFiles, 'EduTrack', 'EduTrack.exe'));
  return candidates;
}

function openEdutrack() {
  const exe = candidateExecutables().find((file) => fs.existsSync(file));
  if (!exe) {
    return {
      isError: true,
      content: [{
        type: 'text',
        text: 'EduTrack is not installed. Install EduTrack-0.1.0.msi first (looked in: '
          + candidateExecutables().join(', ') + ').',
      }],
    };
  }
  spawn(exe, [], { detached: true, stdio: 'ignore', windowsHide: false }).unref();
  return { content: [{ type: 'text', text: `EduTrack is starting (${exe}). The window appears in a few seconds.` }] };
}

function handle(request) {
  switch (request.method) {
    case 'initialize':
      return {
        protocolVersion: request.params?.protocolVersion ?? '2025-06-18',
        capabilities: { tools: {} },
        serverInfo: SERVER_INFO,
      };
    case 'ping':
      return {};
    case 'tools/list':
      return { tools: TOOLS };
    case 'tools/call':
      if (request.params?.name === 'open_edutrack') return openEdutrack();
      throw { code: -32602, message: `Unknown tool: ${request.params?.name}` };
    default:
      throw { code: -32601, message: `Method not found: ${request.method}` };
  }
}

function send(message) {
  process.stdout.write(JSON.stringify(message) + '\n');
}

readline.createInterface({ input: process.stdin }).on('line', (rawLine) => {
  const line = rawLine.replace(/^﻿/, '');
  if (!line.trim()) return;
  let request;
  try {
    request = JSON.parse(line);
  } catch {
    send({ jsonrpc: '2.0', id: null, error: { code: -32700, message: 'Parse error' } });
    return;
  }
  // notifications (no id) such as notifications/initialized need no answer
  if (request.id === undefined) return;
  try {
    send({ jsonrpc: '2.0', id: request.id, result: handle(request) });
  } catch (error) {
    send({ jsonrpc: '2.0', id: request.id, error: { code: error.code ?? -32603, message: error.message ?? String(error) } });
  }
});
