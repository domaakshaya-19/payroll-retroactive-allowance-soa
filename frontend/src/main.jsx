import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { request, readSession, validateBackdate, GATEWAY } from './api.js';
import './style.css';
import PayrollInsights from './PayrollInsights.jsx';

const amount = value => value == null ? '—' : Number(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const timestamp = value => value ? new Date(value).toLocaleString() : '—';
const labelRole = role => role === 'payrollApprover' ? 'Payroll approver' : 'Payroll clerk';
function Status({ value }) { return <span className={`badge status-${String(value).toLowerCase()}`}>{String(value).replaceAll('_', ' ')}</span>; }
function Notice({ children, error = false }) { return children ? <div className={`notice ${error ? 'error' : ''}`} role={error ? 'alert' : 'status'}>{children}</div> : null; }
function Table({ headings, rows, empty = 'No records found.', render }) {
  if (!rows.length) return <p className="empty">{empty}</p>;
  return <div className="table-scroll" tabIndex="0" aria-label="Scrollable data table"><table><thead><tr>{headings.map(h => <th key={h}>{h}</th>)}</tr></thead><tbody>{rows.map(render)}</tbody></table></div>;
}
function Panel({ title, subtitle, error, children }) { return <section className="panel"><div className="section-heading"><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div><Notice error>{error}</Notice>{children}</section>; }

function Login({ onLogin, message }) {
  const [username, setUsername] = useState('clerk');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault(); setError(''); setBusy(true);
    try { const data = await request('/api/auth/token', { method: 'POST', body: { username, password } }); onLogin(readSession(data.accessToken)); }
    catch (e) { setError(e.message); } finally { setPassword(''); setBusy(false); }
  }
  return <main className="login-layout"><div className="login-intro"><span className="eyebrow">PAYROLL / RETRO</span><h1>Allowance changes.<br />A clear record.</h1><p>Review historical payroll, follow retroactive adjustments and approve the difference with confidence.</p><div className="intro-note">Versioned rules · Preserved statements · Traceable adjustments</div></div>
    <section className="panel login-card"><span className="eyebrow">YOUR WORKSPACE</span><h2>Sign in</h2><p className="muted">Use your local demo account credentials.</p><Notice>{message}</Notice><Notice error>{error}</Notice>
      <form onSubmit={submit}><label>Account<select value={username} onChange={e => setUsername(e.target.value)} disabled={busy} autoComplete="username"><option value="clerk">clerk — Payroll clerk</option><option value="approver">approver — Payroll approver</option></select></label><label>Password<input type="password" autoComplete="current-password" required maxLength={72} value={password} onChange={e => setPassword(e.target.value)} disabled={busy} /></label><button disabled={busy}>{busy ? 'Signing in…' : 'Sign in to workspace →'}</button></form>
      <p className="small muted">Session tokens stay in memory. Reloading this page signs you out.</p><details><summary>Local certificate or connection help</summary><p>Trust the generated <code>.local/localhost.crt</code> for your Windows user, then restart your browser. Check Gateway access at <a href={GATEWAY} target="_blank" rel="noreferrer">https://localhost:8080</a> (an unauthenticated 401/403 is normal). See README for HTTPS and CORS setup. Do not disable certificate validation.</p></details>
    </section></main>;
}

function BackdateForm({ rules, submit, busy }) {
  const latest = [...rules.reduce((map, row) => { if (!map.has(row.allowanceType) || row.ruleVersion > map.get(row.allowanceType).ruleVersion) map.set(row.allowanceType, row); return map; }, new Map()).values()];
  const [type, setType] = useState('');
  const [version, setVersion] = useState('');
  const [monthlyAmount, setAmount] = useState('');
  const [date, setDate] = useState('');
  const [error, setError] = useState('');
  const selected = latest.find(row => row.allowanceType === type);
  useEffect(() => { if (latest.length && !type) setType(latest[0].allowanceType); }, [rules, type]);
  useEffect(() => { setVersion(selected ? String(selected.ruleVersion) : ''); }, [selected?.ruleVersion, type]);
  async function send(event) {
    event.preventDefault(); const problem = validateBackdate(monthlyAmount, date, version); setError(problem); if (problem) return;
    const ok = await submit(type, { expectedVersion: Number(version), monthlyAmount: Number(monthlyAmount), effectiveFrom: date });
    if (ok) { setAmount(''); setDate(''); }
  }
  return <Panel title="Backdate an allowance" subtitle="Append a new version. Previously issued statements stay unchanged."><Notice error>{error}</Notice>
    {!latest.length ? <p className="empty">No allowance rules available to backdate.</p> : <form className="backdate-form" onSubmit={send}><label>Allowance type<select value={type} onChange={e => setType(e.target.value)} disabled={busy}>{latest.map(row => <option key={row.allowanceType}>{row.allowanceType}</option>)}</select></label><label>Expected version<input type="number" min="1" step="1" required value={version} onChange={e => setVersion(e.target.value)} disabled={busy} /></label><label>New monthly amount<input inputMode="decimal" placeholder="0.00" required value={monthlyAmount} onChange={e => setAmount(e.target.value)} disabled={busy} /></label><label>Effective from<input type="date" required value={date} onChange={e => setDate(e.target.value)} disabled={busy} /></label><div className="form-footer"><p className="small muted">Use the first day of a month. The server enforces its configured payroll business date and rejects stale versions.</p><button disabled={busy || !selected}>{busy ? 'Please wait…' : 'Create backdated version'}</button></div></form>}
  </Panel>;
}

const sections = ['rules', 'periods', 'snapshots', 'workflows', 'adjustments', 'approved'];
function Workspace({ session, employee, onUnauthorized }) {
  const [data, setData] = useState({ rules: [], periods: [], snapshots: [], workflows: [], adjustments: [], approved: null });
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');
  const [actionError, setActionError] = useState('');
  const [updated, setUpdated] = useState(null);
  const [periodFilter, setPeriodFilter] = useState('');
  const lifetime = useRef(new AbortController());
  const pending = useRef(false);
  const clerk = session.roles.includes('payrollClerk');
  const approver = session.roles.includes('payrollApprover');
  const suffix = `/employees/${encodeURIComponent(employee)}`;
  const retro = `/api/retro-adjustments${suffix}`;
  const call = (path, options = {}) => request(path, { ...options, token: session.token, signal: lifetime.current.signal, onUnauthorized });
  async function refresh() {
    if (pending.current) return; pending.current = true; setLoading(true);
    const paths = [`/api/allowances${suffix}`, `/api/pay-periods${suffix}`, `/api/pay-periods${suffix}/snapshots`, `${retro}/workflows`, retro, `${retro}/approved/current-open`];
    try {
      const results = await Promise.allSettled(paths.map(path => call(path)));
      if (lifetime.current.signal.aborted) return;
      const nextErrors = {};
      const next = {};
      results.forEach((result, index) => { const key = sections[index]; if (result.status === 'fulfilled') next[key] = result.value; else { nextErrors[key] = result.reason.message; next[key] = key === 'approved' ? null : []; } });
      setData(next);
      setErrors(nextErrors); setUpdated(new Date());
    } finally { pending.current = false; if (!lifetime.current.signal.aborted) setLoading(false); }
  }
  useEffect(() => { refresh(); return () => lifetime.current.abort(); }, []);
  async function mutate(path, body, message) {
    if (saving) return false; setSaving(true); setActionError(''); setNotice('');
    try { await call(path, { method: 'POST', body }); if (lifetime.current.signal.aborted) return false; setNotice(message); await refresh(); return true; }
    catch (e) { if (!lifetime.current.signal.aborted) setActionError(e.message); return false; }
    finally { if (!lifetime.current.signal.aborted) setSaving(false); }
  }
  const periods = new Map(data.periods.map(row => [row.id, row]));
  const periodLabel = id => { const period = periods.get(id); return period ? `${period.startsOn} – ${period.endsOn} (#${id})` : `Period #${id}`; };
  const open = data.periods.find(row => row.status === 'OPEN');
  const snapshots = periodFilter ? data.snapshots.filter(row => String(row.periodId) === periodFilter) : data.snapshots;
  return <>
    <div className="workspace-heading"><div><span className="eyebrow">EMPLOYEE PAYROLL</span><h1>{employee}<span className="heading-dot"> / </span>Allowance workspace</h1><p className="muted">Review rule history and follow each adjustment through approval.</p></div><div className="refresh"><button className="secondary" onClick={refresh} disabled={loading || saving}>{loading ? 'Loading records…' : 'Refresh records ↻'}</button><span className="small muted">{updated ? `Last checked ${updated.toLocaleTimeString()}` : 'Connecting to Gateway…'}</span></div></div>
    <Notice>{loading ? 'Loading employee records from Gateway…' : notice}</Notice><Notice error>{actionError}</Notice>
    <div className="summary-grid"><div className="summary"><span>Current open payroll</span><strong>{open ? open.startsOn.slice(0, 7) : loading ? 'Loading…' : 'Unavailable'}</strong><small>{open ? `Period #${open.id}` : errors.periods || 'No open period'}</small></div><div className="summary accent"><span>Approved for current open payroll</span><strong>{data.approved ? amount(data.approved.total) : loading ? 'Loading…' : 'Unavailable'}</strong><small>{errors.approved || (data.approved ? `${data.approved.adjustments.length} approved adjustments · Period #${data.approved.paymentPeriodId}` : 'No total available')}</small></div><div className="summary"><span>Adjustment review</span><strong>{loading ? '…' : data.adjustments.filter(row => row.status === 'DRAFT').length}</strong><small>Draft adjustments awaiting an approver</small></div></div>
    <PayrollInsights data={data} employee={employee} loading={loading || saving} errors={errors} />
    {clerk && <BackdateForm rules={data.rules} busy={saving || loading} submit={(type, body) => mutate(`/api/allowances${suffix}/${encodeURIComponent(type)}/backdate`, body, 'Rule version created. Retro processing is asynchronous; refresh records to follow its workflow and adjustments.')} />}
    <Panel title="Allowance versions" subtitle="Every version is retained. Amounts are monthly." error={errors.rules}><Table headings={['Allowance', 'Version', 'Monthly amount', 'Effective from']} rows={[...data.rules].sort((a, b) => a.allowanceType.localeCompare(b.allowanceType) || b.ruleVersion - a.ruleVersion)} render={row => <tr key={row.id}><td className="emphasis">{row.allowanceType}</td><td>v{row.ruleVersion}</td><td className="numeric">{amount(row.monthlyAmount)}</td><td>{row.effectiveFrom}</td></tr>} /></Panel>
    <div className="two-column"><Panel title="Pay periods" subtitle="Closed periods are preserved." error={errors.periods}><Table headings={['Period', 'Dates', 'Status']} rows={data.periods} render={row => <tr key={row.id}><td>#{row.id}</td><td>{row.startsOn}<br /><span className="muted">to {row.endsOn}</span></td><td><Status value={row.status} /></td></tr>} /></Panel><Panel title="Retro workflows" subtitle="Refresh to follow event processing." error={errors.workflows}><Table headings={['Event / allowance', 'Version', 'Status', 'Attempts']} rows={data.workflows} render={row => <tr key={row.eventId}><td><code className="identifier">{row.eventId}</code><span className="small muted">{row.allowanceType}</span><details><summary>Trace details</summary><p className="small">Aggregate: {row.aggregateId}<br />Correlation: <code className="identifier">{row.correlationId}</code>{row.errorCode && <>Error: {row.errorCode}</>}</p></details></td><td>v{row.newVersion}</td><td><Status value={row.status} /></td><td>{row.attempts}</td></tr>} /></Panel></div>
    <Panel title="Issued statement snapshots" subtitle="Read only · Original paid amounts never change when a rule is backdated." error={errors.snapshots}><label className="filter">Pay period<select value={periodFilter} onChange={e => setPeriodFilter(e.target.value)}><option value="">All issued periods</option>{data.periods.map(row => <option value={row.id} key={row.id}>{periodLabel(row.id)}</option>)}</select></label><Table headings={['Statement', 'Period', 'Allowance', 'Original version', 'Originally paid', 'Issued at']} rows={snapshots} empty="No issued snapshots for this selection. Open payroll has no issued statement." render={row => <tr key={`${row.statementId}-${row.allowanceType}`}><td>#{row.statementId}</td><td>{row.startsOn} – {row.endsOn}</td><td>{row.allowanceType}</td><td>v{row.ruleVersion}</td><td className="numeric">{amount(row.originalAmount)}</td><td>{timestamp(row.issuedAt)}</td></tr>} /></Panel>
    <Panel title="Retro adjustments" subtitle={approver ? 'Approve each draft for its linked open payroll. Approval does not modify the source statement.' : 'Approval requires the payroll approver role.'} error={errors.adjustments}><Table headings={['Adjustment / event', 'Source period', 'Allowance / versions', 'Originally paid', 'Corrected amount', 'Difference', 'Payment period', 'Status', ...(approver ? ['Action'] : [])]} rows={data.adjustments} render={row => <tr key={row.id}><td className="emphasis">#{row.id}<code className="identifier">{row.eventId}</code><span className="small muted">Statement #{row.statementId}</span></td><td>{periodLabel(row.sourcePeriodId)}</td><td>{row.allowanceType}<br /><span className="muted">v{row.previousVersion} → v{row.newVersion}</span></td><td className="numeric">{amount(row.originallyPaid)}</td><td className="numeric">{amount(row.newAmount)}</td><td className="numeric emphasis">{amount(row.difference)}</td><td>{row.paymentPeriodId ? periodLabel(row.paymentPeriodId) : 'Unassigned'}</td><td><Status value={row.status} /></td>{approver && <td>{row.status === 'DRAFT' ? <button disabled={saving || loading} onClick={() => mutate(`${retro}/${row.id}/approve`, undefined, `Adjustment #${row.id} approved. Current-open totals refreshed.`)}>Approve #{row.id}</button> : '—'}</td>}</tr>} /></Panel>
    <p className="small muted footer-note">Amounts use the backend payroll units. All displayed records come from the HTTPS API Gateway. Refresh after asynchronous processing; no payroll records are edited locally.</p>
  </>;
}

function App() {
  const [session, setSession] = useState(null);
  const [employee, setEmployee] = useState('EMP001');
  const [message, setMessage] = useState('');
  function logout(reason = '') { setSession(null); setMessage(reason); }
  useEffect(() => {
    if (!session) return;
    const expire = () => { if (Date.now() >= session.expiresAt) logout('Your session has expired. Sign in again to continue.'); };
    const timer = setTimeout(expire, Math.max(0, session.expiresAt - Date.now()));
    window.addEventListener('focus', expire); return () => { clearTimeout(timer); window.removeEventListener('focus', expire); };
  }, [session]);
  function login(next) { setEmployee(next.employees.includes('EMP001') ? 'EMP001' : next.employees[0] || ''); setMessage(''); setSession(next); }
  return <><header className="topbar"><a className="brand" href="#" aria-label="Payroll Retro workspace"><span className="brand-mark">P</span><span>Payroll<span className="brand-light"> / Retro</span></span></a>{session && <div className="session"><span>{session.username}<small>{session.roles.filter(role => ['payrollClerk', 'payrollApprover'].includes(role)).map(labelRole).join(' · ')}</small></span><button className="secondary" onClick={() => logout()}>Sign out</button></div>}</header>
    {!session ? <Login onLogin={login} message={message} /> : <main className="workspace"><div className="employee-bar"><label>Employee<select value={employee} onChange={e => setEmployee(e.target.value)} disabled={!session.employees.length}>{session.employees.map(id => <option key={id}>{id}</option>)}</select></label><span className="small muted">Access limited to your permitted employees · Session expires {new Date(session.expiresAt).toLocaleTimeString()}</span></div>{employee ? <Workspace key={`${session.token}:${employee}`} session={session} employee={employee} onUnauthorized={() => logout('Your session expired or was rejected. Sign in again.')} /> : <Notice error>Your account has no permitted employees. Contact the local administrator.</Notice>}</main>}
  </>;
}

createRoot(document.getElementById('root')).render(<App />);
