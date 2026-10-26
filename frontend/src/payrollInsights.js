// Read-only calculations over existing API DTOs. No API client or write capability.
export function cents(value) {
  const text = String(value);
  if (!/^-?\d+(\.\d{1,2})?$/.test(text)) throw new Error('An amount is missing or exceeds the supported two-decimal payroll precision.');
  const negative = text.startsWith('-');
  const [whole, fraction = ''] = text.replace('-', '').split('.');
  const result = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
  return negative ? -result : result;
}

export function decimal(value) {
  const absolute = value < 0n ? -value : value;
  return `${value < 0n ? '-' : ''}${absolute / 100n}.${String(absolute % 100n).padStart(2, '0')}`;
}

export function money(value) {
  const [whole, fraction] = String(value).split('.');
  return `${whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${(fraction || '').padEnd(2, '0')}`;
}

function monthStart(date) {
  return /^\d{4}-\d{2}-01$/.test(date) && !Number.isNaN(Date.parse(date)) && new Date(date).toISOString().slice(0, 10) === date;
}

export function simulate({ employeeId, allowanceType, proposedAmount, effectiveFrom, snapshots, periods, rules, adjustments }) {
  if (!/^\d{1,12}(\.\d{1,2})?$/.test(proposedAmount)) throw new Error('Enter a nonnegative proposed amount with up to 12 integer digits and 2 decimal places.');
  if (!monthStart(effectiveFrom)) throw new Error('Choose the first day of a valid month.');
  const versions = rules.filter(row => row.employeeId === employeeId && row.allowanceType === allowanceType);
  if (!versions.length) throw new Error('Select an allowance with an existing rule version.');
  const proposedVersion = Math.max(...versions.map(row => row.ruleVersion)) + 1;
  const periodMap = new Map(periods.filter(row => row.employeeId === employeeId).map(row => [row.id, row]));
  const seen = new Set();
  const corrected = cents(proposedAmount);
  const rows = snapshots.filter(row => row.employeeId === employeeId && row.allowanceType === allowanceType && row.startsOn >= effectiveFrom && row.ruleVersion < proposedVersion)
    .sort((a, b) => a.startsOn.localeCompare(b.startsOn))
    .flatMap(snapshot => {
      const period = periodMap.get(snapshot.periodId);
      if (!period) throw new Error('A snapshot has no matching pay period. Refresh records before simulating.');
      if (period.status !== 'CLOSED') return [];
      if (!snapshot.statementId || snapshot.startsOn !== period.startsOn || snapshot.endsOn !== period.endsOn || seen.has(snapshot.periodId)) {
        throw new Error('Historical snapshot links are inconsistent. Refresh records before simulating.');
      }
      seen.add(snapshot.periodId);
      const original = cents(snapshot.originalAmount);
      const approved = adjustments.filter(row => row.employeeId === employeeId && row.allowanceType === allowanceType && row.sourcePeriodId === snapshot.periodId && row.statementId === snapshot.statementId && row.status === 'APPROVED');
      return [{ ...snapshot, original: decimal(original), corrected: decimal(corrected), difference: decimal(corrected - original),
        existingApproved: decimal(approved.reduce((sum, row) => sum + cents(row.difference), 0n)), approvedCount: approved.length }];
    });
  return {
    rows, affectedMonths: rows.length, proposedVersion,
    gross: decimal(rows.reduce((sum, row) => sum + cents(row.difference), 0n)),
    existingApproved: decimal(rows.reduce((sum, row) => sum + cents(row.existingApproved), 0n)),
  };
}

export function periodHistory({ employeeId, periodId, periods, snapshots, rules, adjustments }) {
  const period = periods.find(row => row.employeeId === employeeId && String(row.id) === String(periodId));
  if (!period) return null;
  const originals = snapshots.filter(row => row.employeeId === employeeId && row.periodId === period.id);
  return {
    period, originals,
    // Effective dates are business applicability, not creation/publication timestamps.
    versions: rules.filter(row => row.employeeId === employeeId && row.effectiveFrom <= period.endsOn)
      .sort((a, b) => a.allowanceType.localeCompare(b.allowanceType) || a.ruleVersion - b.ruleVersion)
      .map(row => ({ ...row, issued: originals.some(snapshot => snapshot.allowanceType === row.allowanceType && snapshot.ruleVersion === row.ruleVersion) })),
    adjustments: adjustments.filter(row => row.employeeId === employeeId && row.sourcePeriodId === period.id)
      .sort((a, b) => a.newVersion - b.newVersion || a.id - b.id),
  };
}
