import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, readdirSync, rmSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
const redocly = fileURLToPath(new URL('./node_modules/@redocly/cli/bin/cli.js', import.meta.url));
const config = fileURLToPath(new URL('./redocly.yaml', import.meta.url));
const contract = 'docs/contratos/web/openapi.yaml';
const examplesDirectory = join(root, 'docs/contratos/web/ejemplos');

function runRedocly(command, options = []) {
  const args = [redocly, command, '--config', config, contract, ...options];
  const result = spawnSync(process.execPath, args, { cwd: root, stdio: 'inherit' });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `Redocly ${command} falló`);
}

function readJson(path) {
  return JSON.parse(readFileSync(path, 'utf8'));
}

function verifyAccess(examples) {
  const chassis = examples['machines-authorized'].value.items[0].chassis;
  assert.match(chassis, /^0[0-9]+$/, 'La máquina del escenario conserva los ceros iniciales');
  assert.equal(examples['machine-detail'].value.chassis, chassis);
  assert.deepEqual(examples['machines-empty'].value.items, []);

  const token = examples['csrf-token'].value.token;
  assert.equal(examples['csrf-header'].value, token);

  const cookieAttributes = examples['session-cookie'].value.split(';').slice(1).map(attribute => attribute.trim());
  for (const attribute of ['Secure', 'HttpOnly', 'SameSite=Lax']) {
    assert.ok(cookieAttributes.includes(attribute), `La cookie de sesión declara ${attribute}`);
  }
}

function verifyMeasurements(examples, parameters) {
  const rpmFirst = examples['history-rpm-page-1'].value;
  const rpmSecond = examples['history-rpm-page-2'].value;
  const coolantPage = examples['history-coolant'].value;
  const latest = examples['latest-normal'].value;
  const rpm = [...rpmFirst.items, ...rpmSecond.items];
  const coolant = coolantPage.items;

  for (const page of [rpmFirst, rpmSecond, coolantPage]) {
    assert.equal(page.asOf, latest.asOf, 'Las series y el detalle usan el mismo corte');
    assert.equal(page.total, 10);
  }
  assert.deepEqual([rpmFirst.page, rpmSecond.page, coolantPage.page], [1, 2, 1]);
  for (const page of [rpmFirst, rpmSecond, coolantPage]) {
    assert.equal(page.items.length, page.size, 'La corrida principal cubre páginas completas');
  }
  assert.equal(rpmFirst.size, rpmSecond.size);
  assert.equal(rpm.length, 10);
  assert.equal(coolant.length, 10);
  assert.equal(new Set([...rpm, ...coolant].map(record => record.recordId)).size, 20);

  const from = Date.parse(parameters.From.example);
  const to = Date.parse(parameters.To.example);
  const instants = rpm.map(record => record.occurredAt);
  assert.equal(Date.parse(instants[0]), from);
  assert.ok(instants.every(instant => Date.parse(instant) >= from && Date.parse(instant) < to));
  assert.deepEqual(coolant.map(record => record.occurredAt), instants);
  for (let index = 1; index < instants.length; index += 1) {
    assert.equal(Date.parse(instants[index]) - Date.parse(instants[index - 1]), 60_000);
  }

  for (const [series, definitionId, unit] of [
    [rpm, 'engine.rpm', 'rpm'],
    [coolant, 'engine.coolant_temperature', '°C'],
  ]) {
    for (const record of series) {
      assert.equal(record.definitionId, definitionId);
      assert.equal(record.unit, unit);
      assert.equal(record.quality, 'VALID');
      assert.ok(Date.parse(record.receivedAt) >= Date.parse(record.occurredAt));
      assert.ok(Date.parse(record.receivedAt) <= Date.parse(latest.asOf));
    }
  }

  assert.equal(rpm[0].value, 0, 'Un cero medido se conserva como valor válido');
  assert.deepEqual(latest.items.map(item => item.measurement), [rpm.at(-1), coolant.at(-1)]);
  const invalid = examples['latest-invalid'].value.items[0].measurement;
  assert.equal(invalid.quality, 'INVALID');
  assert.equal(invalid.value, null);
  assert.ok(examples['latest-stale'].value.items.some(
    item => item.measurement.quality === 'VALID' && item.freshness === 'STALE',
  ));
  const empty = examples['history-empty'].value;
  assert.equal(empty.total, 0);
  assert.deepEqual(empty.items, []);
}

function verifyLateRecord(examples) {
  const before = examples['history-before-late-confirmation'].value;
  const after = examples['history-late-record'].value;
  const [late, recent] = after.items;
  assert.deepEqual(before.items, [recent], 'El corte temprano excluye la confirmación posterior');
  assert.equal(before.total, 1);
  assert.equal(after.total, 2);
  assert.ok(Date.parse(late.occurredAt) < Date.parse(recent.occurredAt));
  assert.ok(Date.parse(recent.receivedAt) < Date.parse(late.receivedAt));
  assert.ok(Date.parse(late.receivedAt) <= Date.parse(before.asOf));
  assert.ok(Date.parse(before.asOf) < Date.parse(after.asOf));
  assert.notEqual(late.context.sourceId, recent.context.sourceId);
  assert.notEqual(late.context.appliedConfigVersion, recent.context.appliedConfigVersion);
  assert.deepEqual(examples['latest-after-late-record'].value.items[0].measurement, recent);
}

runRedocly('lint');

const output = mkdtempSync(join(tmpdir(), 'pfc-web-contract-'));
const bundlePath = join(output, 'openapi.json');

try {
  runRedocly('bundle', ['--output', bundlePath]);
  const bundle = readJson(bundlePath);
  const examples = Object.fromEntries(
    readdirSync(examplesDirectory)
      .filter(name => name.endsWith('.json'))
      .map(name => [name.slice(0, -5), readJson(join(examplesDirectory, name))]),
  );
  assert.deepEqual(bundle.components.examples, examples, 'El contrato incluye cada ejemplo JSON versionado');
  verifyAccess(examples);
  verifyMeasurements(examples, bundle.components.parameters);
  verifyLateRecord(examples);
  console.log(`${Object.keys(examples).length} ejemplos verificados. Las comprobaciones entre ejemplos son coherentes.`);
} finally {
  rmSync(bundlePath, { force: true });
  rmdirSync(output);
}
