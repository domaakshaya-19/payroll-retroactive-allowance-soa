import React, { useState } from 'react';
import { simulate, periodHistory, money } from './payrollInsights.js';

function Grid({ headers, rows, render, empty }) {
  if (!rows.length) return <p className="empty">{empty}</p>;
  return <div className="table-scroll" tabIndex="0" aria-label="Read-only payroll history"><table><thead><tr>{headers.map(header => <th key={header}>{header}</th>)}</tr></thead><tbody>{rows.map(render)}</tbody></table></div>;
}
function Badge({ value }) { return <span className={`badge status-${value.toLowerCase()}`}>{value}</span>; }
function Simulator({ data, employee }) {
  const types = [...new Set(data.rules.filter(row => row.employeeId === employee).map(row => row.allowanceType))].sort();
  const [type, setType] = useState('');
  const [proposedAmount, setAmount] = useState('');
  const [effectiveFrom, setDate] = useState('');
  const [proposal, setProposal] = useState(null);
  const selectedType = type || types[0] || '';
  let result, error;
  if (proposal) {
    try { result = simulate({ ...data, employeeId: employee, ...proposal }); }
    catch (e) { error = e.message; }
  }
  return <>
    <p className="muted small">Try a different monthly allowance against real, closed issued snapshots. This preview stays in your browser: it creates no rule, adjustment, approval or Kafka event.</p>
    <form className="insight-form" onSubmit={event => { event.preventDefault(); setProposal({ allowanceType: selectedType, proposedAmount, effectiveFrom }); }} onChange={() => setProposal(null)}>
      <label>Allowance<select required value={selectedType} onChange={event => setType(event.target.value)}>{!types.length && <option value="">No rules available</option>}{types.map(value => <option key={value}>{value}</option>)}</select></label>
      <label>Proposed monthly amount<input required inputMode="decimal" placeholder="0.00" value={proposedAmount} onChange={event => setAmount(event.target.value)} /></label>
      <label>Effective from<input required type="date" value={effectiveFrom} onChange={event => setDate(event.target.value)} /></label>
      <button disabled={!types.length}>Preview only</button>
    </form>
    {error && <div className="notice error" role="alert">{error}</div>}
    {result && <div aria-live="polite">
      <div className="summary-grid"><div className="summary"><span>Affected closed months</span><strong>{result.affectedMonths}</strong><small>{result.rows.map(row => row.startsOn.slice(0, 7)).join(', ') || 'None on or after this effective date'}</small></div><div className="summary accent"><span>Proposed gross adjustment</span><strong>{money(result.gross)}</strong><small>Corrected amount minus original issued allowance</small></div><div className="summary"><span>Existing approved differences</span><strong>{money(result.existingApproved)}</strong><small>For the same source statements · Not proof of payment</small></div></div>
      <Grid headers={['Affected month', 'Original statement', 'Original amount', 'Proposed corrected', 'Monthly difference', 'Existing approved']} rows={result.rows} empty="No closed issued snapshots are affected. The gross preview is zero; open payroll is excluded." render={row => <tr key={`${row.statementId}-${row.allowanceType}`}><td>{row.startsOn.slice(0, 7)}</td><td>#{row.statementId} · v{row.ruleVersion}</td><td className="numeric">{money(row.original)}</td><td className="numeric">{money(row.corrected)}</td><td className="numeric emphasis">{money(row.difference)}</td><td className="numeric">{money(row.existingApproved)}<span className="small muted">{row.approvedCount} approvals</span></td></tr>} />
      <p className="insight-explanation">Gross is a fresh comparison to each original issued allowance, not an additional amount due after prior corrections. Existing approved differences are shown separately and are neither treated as paid nor subtracted. These APIs contain no settlement, tax or deduction data, so accurate net payable and outstanding balance are unavailable.</p>
    </div>}
    <details className="insight-explanation"><summary>How this relates to the real workflow</summary><p>SOA gives each service its own job: PayPeriod owns original statements, AllowanceRule owns versions, and RetroAdjustment owns corrections. A real saved backdate publishes a Kafka event through an outbox; RetroAdjustment reacts independently. This local preview sends nothing and does not test the server's business-date, concurrency or approval eligibility rules.</p></details>
  </>;
}

function TimeMachine({ data, employee, workflowError }) {
  const periods = [...data.periods].filter(row => row.employeeId === employee).sort((a, b) => b.startsOn.localeCompare(a.startsOn));
  const [selected, setSelected] = useState('');
  const id = selected || periods.find(row => row.status === 'CLOSED')?.id || periods[0]?.id;
  const history = periodHistory({ ...data, employeeId: employee, periodId: id });
  const workflows = new Map(data.workflows.map(row => [row.eventId, row]));
  const paymentPeriod = id => periods.find(row => row.id === id)?.startsOn.slice(0, 7) || `#${id}`;
  return <>
    <p className="muted small">Select a payroll month to place its untouched issued statement beside known rule versions and later corrections. This is a period lens over current records, not a reconstruction of what was known on a historical date.</p>
    <label className="filter">Payroll month<select value={id || ''} onChange={event => setSelected(event.target.value)}>{!periods.length && <option value="">No periods available</option>}{periods.map(row => <option key={row.id} value={row.id}>{row.startsOn.slice(0, 7)} · {row.status} · #{row.id}</option>)}</select></label>
    {history && <>
      <div className="history-columns"><section><h3>1. Original issued statement</h3><p className="small muted">Owned by PayPeriod · Immutable source record</p>
        <Grid headers={['Statement / issued at', 'Allowance', 'Issued version', 'Original amount']} rows={history.originals} empty={history.period.status === 'OPEN' ? 'Open period: no issued statement yet. No original paid amount is assumed.' : 'No issued snapshots returned for this period.'} render={row => <tr key={`${row.statementId}-${row.allowanceType}`}><td>#{row.statementId}<span className="small muted">{new Date(row.issuedAt).toLocaleString()}</span></td><td>{row.allowanceType}</td><td>v{row.ruleVersion}</td><td className="numeric">{money(row.originalAmount)}</td></tr>} />
      </section><section><h3>2. Known allowance versions</h3><p className="small muted">Owned by AllowanceRule · Effective on or before period end</p>
        <Grid headers={['Allowance / version', 'Effective from', 'Monthly amount', 'Statement link']} rows={history.versions} empty="No known versions effective by this period's end." render={row => <tr key={row.id}><td>{row.allowanceType} · v{row.ruleVersion}</td><td>{row.effectiveFrom}</td><td className="numeric">{money(row.monthlyAmount)}</td><td>{row.issued ? 'Used in original statement' : 'Not used in original statement'}</td></tr>} />
      </section></div>
      <h3>3. Corrections linked to this source month</h3><p className="small muted">Owned by RetroAdjustment · Additive history, never a statement rewrite</p>
      {workflowError && <div className="notice error" role="alert">Workflow status unavailable: {workflowError}</div>}
      <Grid headers={['Adjustment / source statement', 'Allowance / versions', 'Original → corrected', 'Difference', 'Status / target payroll', 'Trigger event / workflow']} rows={history.adjustments} empty="No retro adjustments returned for this source period." render={row => <tr key={row.id}><td>#{row.id}<span className="small muted">Statement #{row.statementId}</span></td><td>{row.allowanceType}<span className="small muted">v{row.previousVersion} → v{row.newVersion}</span></td><td className="numeric">{money(row.originallyPaid)} → {money(row.newAmount)}</td><td className="numeric">{money(row.difference)}</td><td><Badge value={row.status} /><span className="small muted">Target {row.paymentPeriodId ? paymentPeriod(row.paymentPeriodId) : 'unassigned'}</span></td><td><code className="identifier">{row.eventId}</code><span className="small muted">{workflows.get(row.eventId)?.status || 'Workflow unavailable'}</span></td></tr>} />
      <p className="insight-explanation">The trigger event links a saved rule change delivered through Kafka to its independently processed corrections. A backdated rule can have an effective date before it was created. Rule creation and adjustment settlement timestamps are not exposed here. An APPROVED adjustment is authorized for a target payroll period; it is not evidence of cash paid. Original issued allowances remain unchanged.</p>
    </>}
  </>;
}

export default function PayrollInsights({ data, employee, loading, errors }) {
  const [view, setView] = useState('simulator');
  const failed = ['rules', 'periods', 'snapshots', 'adjustments'].filter(key => errors[key]);
  return <section className="panel" aria-label="Read-only payroll exploration">
    <div className="section-heading"><span className="eyebrow">EXPLORE · READ ONLY</span><h2>Understand the change before taking action</h2></div>
    <div className="insight-switch" role="group" aria-label="Payroll exploration view"><button type="button" className={view === 'simulator' ? '' : 'secondary'} aria-pressed={view === 'simulator'} onClick={() => setView('simulator')}>What-If Payroll Simulator</button><button type="button" className={view === 'history' ? '' : 'secondary'} aria-pressed={view === 'history'} onClick={() => setView('history')}>Payroll Time Machine</button></div>
    {loading ? <p role="status">Loading source records…</p> : failed.length ? <div className="notice error" role="alert">Exploration needs complete source records. {failed.map(key => `${key}: ${errors[key]}`).join(' ')} Use Refresh records to try again; missing data is not treated as zero.</div> : view === 'simulator' ? <Simulator data={data} employee={employee} /> : <TimeMachine data={data} employee={employee} workflowError={errors.workflows} />}
  </section>;
}
