import test from 'node:test';
import assert from 'node:assert/strict';
import { simulate, periodHistory, cents, decimal } from './payrollInsights.js';

// Synthetic DTO fixtures for tests only; the UI receives these shapes from live GETs.
function source() {
  return {
    employeeId: 'EMP001', allowanceType: 'HOUSING', proposedAmount: '3000.00', effectiveFrom: '2026-08-01',
    periods: [
      { id: 1, employeeId: 'EMP001', startsOn: '2026-08-01', endsOn: '2026-08-31', status: 'CLOSED' },
      { id: 2, employeeId: 'EMP001', startsOn: '2026-09-01', endsOn: '2026-09-30', status: 'CLOSED' },
      { id: 3, employeeId: 'EMP001', startsOn: '2026-10-01', endsOn: '2026-10-31', status: 'OPEN' },
    ],
    snapshots: [
      { statementId: 11, periodId: 1, employeeId: 'EMP001', startsOn: '2026-08-01', endsOn: '2026-08-31', allowanceType: 'HOUSING', ruleVersion: 1, originalAmount: 2000, issuedAt: '2026-08-31T12:00:00Z' },
      { statementId: 12, periodId: 2, employeeId: 'EMP001', startsOn: '2026-09-01', endsOn: '2026-09-30', allowanceType: 'HOUSING', ruleVersion: 1, originalAmount: 2000, issuedAt: '2026-09-30T12:00:00Z' },
    ],
    rules: [{ id: 1, employeeId: 'EMP001', allowanceType: 'HOUSING', ruleVersion: 1, monthlyAmount: 2000, effectiveFrom: '2026-08-01' }],
    adjustments: [],
  };
}

test('August/September original issued amounts produce two 1000 differences and gross 2000', () => {
  const result = simulate(source());
  assert.deepEqual(result.rows.map(row => [row.startsOn.slice(0, 7), row.original, row.corrected, row.difference]), [
    ['2026-08', '2000.00', '3000.00', '1000.00'], ['2026-09', '2000.00', '3000.00', '1000.00'],
  ]);
  assert.equal(result.affectedMonths, 2);
  assert.equal(result.gross, '2000.00');
});

test('effective-date boundary, open periods and no affected periods are handled without invented amounts', () => {
  const input = source();
  assert.equal(simulate({ ...input, effectiveFrom: '2026-09-01' }).affectedMonths, 1);
  assert.equal(simulate({ ...input, effectiveFrom: '2026-10-01' }).gross, '0.00');
  input.snapshots.push({ ...input.snapshots[0], periodId: 3, statementId: 13, startsOn: '2026-10-01', endsOn: '2026-10-31' });
  assert.equal(simulate(input).affectedMonths, 2);
  assert.equal(simulate({ ...input, snapshots: [] }).gross, '0.00');
});

test('uses each actual snapshot rather than the latest rule amount; reductions and unchanged months work', () => {
  const input = source();
  input.snapshots[1].originalAmount = 2100.25;
  input.rules[0].monthlyAmount = 9999;
  const result = simulate({ ...input, proposedAmount: '2000.00' });
  assert.deepEqual(result.rows.map(row => row.difference), ['0.00', '-100.25']);
  assert.equal(result.gross, '-100.25');
});

test('approved corrections are separate from gross, filtered by source statement, employee and allowance', () => {
  const input = source();
  const approved = { id: 1, employeeId: 'EMP001', allowanceType: 'HOUSING', sourcePeriodId: 1, statementId: 11, difference: 500, status: 'APPROVED' };
  input.adjustments = [approved, { ...approved, id: 2, status: 'DRAFT' }, { ...approved, id: 3, statementId: 99 }, { ...approved, id: 4, employeeId: 'EMP999' }, { ...approved, id: 5, allowanceType: 'TRAVEL' }];
  const result = simulate(input);
  assert.equal(result.existingApproved, '500.00');
  assert.equal(result.rows[0].approvedCount, 1);
  assert.equal(result.gross, '2000.00');
  assert.equal('netPayable' in result, false);
  assert.equal('outstandingBalance' in result, false);
});

test('excludes other employees/types; rejects invalid input and ambiguous or missing source links', () => {
  const input = source();
  input.snapshots.push({ ...input.snapshots[0], employeeId: 'EMP999' }, { ...input.snapshots[0], allowanceType: 'TRAVEL' });
  assert.equal(simulate(input).affectedMonths, 2);
  for (const proposedAmount of ['-1', '1.001', '1e3', '', '1000000000000']) assert.throws(() => simulate({ ...input, proposedAmount }), /proposed amount/);
  for (const effectiveFrom of ['2026-08-02', '2026-13-01', 'not-a-date']) assert.throws(() => simulate({ ...input, effectiveFrom }), /first day/);
  assert.throws(() => simulate({ ...input, allowanceType: 'UNKNOWN' }), /existing rule/);
  assert.throws(() => simulate({ ...input, periods: [] }), /matching pay period/);
  assert.throws(() => simulate({ ...input, snapshots: [input.snapshots[0], input.snapshots[0]] }), /inconsistent/);
});

test('money uses exact cents, including maximum supported amounts and totals beyond Number safe cents', () => {
  assert.equal(decimal(cents('0.10') + cents('0.20')), '0.30');
  const input = source();
  input.snapshots.forEach(row => { row.originalAmount = 0; });
  const result = simulate({ ...input, proposedAmount: '999999999999.99' });
  assert.equal(result.gross, '1999999999999.98');
  assert.equal(decimal(cents('9999999999999999.99') + 1n), '10000000000000000.00');
});

test('Time Machine retains original statement alongside later known backdated versions and linked corrections', () => {
  const input = source();
  input.rules.push({ ...input.rules[0], id: 2, ruleVersion: 2, monthlyAmount: 3000 }, { ...input.rules[0], id: 3, ruleVersion: 3, effectiveFrom: '2026-10-01', monthlyAmount: 3500 });
  input.adjustments.push({ id: 5, employeeId: 'EMP001', sourcePeriodId: 1, statementId: 11, newVersion: 2, status: 'APPROVED', eventId: 'trigger-event', paymentPeriodId: 3 });
  const history = periodHistory({ ...input, periodId: '1' });
  assert.equal(history.originals[0].originalAmount, 2000);
  assert.equal(history.originals[0].ruleVersion, 1);
  assert.deepEqual(history.versions.map(row => [row.ruleVersion, row.issued]), [[1, true], [2, false]]);
  assert.equal(history.adjustments[0].statementId, history.originals[0].statementId);
  assert.equal(history.adjustments[0].eventId, 'trigger-event');
  assert.equal(history.adjustments[0].status, 'APPROVED');
  assert.equal(periodHistory({ ...input, periodId: 3 }).originals.length, 0);
  assert.equal(periodHistory({ ...input, periodId: 999 }), null);
  assert.equal(periodHistory({ ...input, employeeId: 'EMP999', periodId: 1 }), null);
});

test('exploration is local and leaves every API source DTO unchanged', t => {
  t.mock.method(globalThis, 'fetch', () => { throw new Error('Read-only exploration must not call the network'); });
  const input = source();
  const before = structuredClone(input);
  simulate(input);
  periodHistory({ ...input, periodId: 1 });
  assert.deepEqual(input, before);
  assert.equal(globalThis.fetch.mock.callCount(), 0);
});
