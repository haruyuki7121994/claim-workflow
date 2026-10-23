import React, { useEffect, useRef, useState } from 'react';
import { api, label, transitionRequest } from './api.js';

const ACCOUNTS = ['clerk-1', 'lead-1', 'assessor-1', 'assessor-2', 'finance-1', 'system-1'];
const PAGE_SIZE = 20;
const date = value => value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—';
const money = value => value == null ? '—' : new Intl.NumberFormat(undefined, { maximumFractionDigits: 2, minimumFractionDigits: 2 }).format(Number(value));
const shortId = id => id?.slice(0, 8).toUpperCase();

function Icon({ name, size = 20 }) {
  const paths = {
    grid: <><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" /></>,
    arrow: <><path d="M5 12h14m-5-5 5 5-5 5" /></>,
    refresh: <><path d="M20 7v5h-5M4 17v-5h5" /><path d="M6.1 7a7 7 0 0 1 11.6-1L20 9M4 15l2.3 3A7 7 0 0 0 18 17" /></>,
    file: <><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9zM14 3v6h6M8 13h8M8 17h5" /></>,
    clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
    check: <path d="m5 12 4 4L19 6" />,
    plus: <path d="M12 5v14M5 12h14" />,
    search: <><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></>,
    lock: <><rect x="5" y="10" width="14" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" /></>,
    close: <path d="m6 6 12 12M18 6 6 18" />,
    download: <><path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5" /></>,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] || paths.file}</svg>;
}

function Badge({ state }) {
  const tone = ['APPROVED', 'CLOSED', 'COMPLETED'].includes(state) ? 'green' : ['REJECTED', 'FAILED'].includes(state) ? 'red' : state === 'PENDING_INFO' ? 'amber' : 'blue';
  return <span className={`badge ${tone}`}><span />{label(state)}</span>;
}

function Modal({ title, subtitle, onClose, children, locked = false }) {
  const dialog = useRef(null);
  useEffect(() => { dialog.current.showModal(); }, []);
  return <dialog ref={dialog} onCancel={event => { if (locked) event.preventDefault(); else onClose(); }} className="modal">
    <div className="modal-heading"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>{!locked && <button className="icon-button" onClick={onClose} aria-label="Close dialog"><Icon name="close" /></button>}</div>
    {children}
  </dialog>;
}

function AuthDialog({ onClose, onConnect, session }) {
  const [tenantId, setTenant] = useState(session?.tenantId || 'tenant-a');
  const [subject, setSubject] = useState(session?.subject || 'clerk-1');
  const [token, setToken] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  async function submit(event, mode) {
    event.preventDefault(); setPending(true); setError('');
    try {
      let account;
      if (mode === 'token') {
        const raw = token.trim();
        const part = raw.split('.')[1]?.replaceAll('-', '+').replaceAll('_', '/');
        const claims = JSON.parse(atob(part));
        account = { token: raw, subject: claims.sub, tenantId: claims.tenant_id, roles: claims.roles || [], displayName: claims.display_name || claims.sub };
        await api('/claims?limit=1', { token: raw });
      } else account = await api('/auth/login', { method: 'POST', body: { tenantId, subject } });
      onConnect(account);
    } catch (failure) { setError(failure.message); }
    finally { setPending(false); }
  }
  return <Modal title={session ? 'Switch account' : 'Welcome to your workspace'} subtitle="Connect to manage claims with your assigned role." onClose={onClose}>
    {error && <p role="alert" className="inline-error">{error}</p>}
    <form onSubmit={event => submit(event, 'login')} className="stack">
      <label>Tenant<input value={tenantId} onChange={event => setTenant(event.target.value)} required maxLength={100} /></label>
      <label>User ID<input list="accounts" value={subject} onChange={event => setSubject(event.target.value)} required maxLength={100} /><datalist id="accounts">{ACCOUNTS.map(account => <option key={account} value={account} />)}</datalist></label>
      <p className="hint">Local seed users: clerk-1, lead-1, assessor-1, finance-1, system-1.</p>
      <button className="primary" disabled={pending}>{pending ? 'Connecting…' : 'Connect to workspace'}<Icon name="arrow" size={16} /></button>
    </form>
    <details className="token-option"><summary>Use an existing bearer token</summary><form className="stack" onSubmit={event => submit(event, 'token')}><label>Token<textarea value={token} onChange={event => setToken(event.target.value)} rows={3} required autoComplete="off" spellCheck="false" /></label><button className="secondary" disabled={pending}>Connect with token</button></form></details>
  </Modal>;
}

function Preconditions({ claim, session, busy, onUpdate }) {
  const [operation, setOperation] = useState('');
  const [text, setText] = useState('');
  const role = name => session.roles?.includes(name);
  const options = [];
  if (role('document_clerk') && claim.state === 'SUBMITTED') options.push(['upload-documents', 'Upload required documents']);
  if (role('document_clerk') && claim.state === 'PENDING_INFO') options.push(['receive-information', 'Receive additional information']);
  if (role('team_lead') && claim.state === 'DOCUMENTS_VERIFIED') options.push(['assign-assessor', 'Assign an assessor', 'assessorId', 'Assessor user ID']);
  if (role('assessor') && claim.state === 'UNDER_ASSESSMENT' && claim.facts.assignedAssessorId === session.subject) {
    options.push(['complete-assessment', 'Complete assessment report'], ['rejection-reason', 'Provide rejection reason', 'description', 'Rejection reason'], ['missing-information', 'Describe missing information', 'description', 'Information needed']);
  }
  if (role('finance') && claim.state === 'APPROVED') options.push(['create-payment-request', 'Process payment request']);
  if (role('finance') && claim.state === 'PAYMENT_INITIATED') options.push(['process-payment', 'Process payment'], ['confirm-payment', 'Confirm payment reference', 'paymentReference', 'Payment reference']);
  if (role('system') && claim.state === 'REJECTED') options.push(['acknowledge-rejection', 'Record member acknowledgement'], ['expire-appeal', 'Simulate appeal expiry']);
  const choice = options.find(option => option[0] === operation) || options[0];
  if (!choice) return null;
  return <details className="precondition-tools"><summary><span>Prepare claim conditions</span><span className="tag">Local simulation</span></summary>
    <p className="hint">Supply the evidence needed for the next step. Simulation requires the backend local profile.</p>
    <form className="condition-form" onSubmit={async event => { event.preventDefault(); const result = await onUpdate(choice[0], choice[2] ? { [choice[2]]: text } : {}); if (result) setText(''); }}>
      <label>Action<select value={choice[0]} onChange={event => { setOperation(event.target.value); setText(''); }} disabled={busy}>{options.map(option => <option key={option[0]} value={option[0]}>{option[1]}</option>)}</select></label>
      {choice[2] && <label>{choice[3]}<input value={text} onChange={event => setText(event.target.value)} required maxLength={choice[2] === 'description' ? 2000 : 100} disabled={busy} placeholder={choice[2] === 'assessorId' ? 'assessor-1' : choice[3]} /></label>}
      <button className="secondary" disabled={busy}>Apply action<Icon name="check" size={16} /></button>
    </form>
  </details>;
}

export default function App() {
  const [session, setSession] = useState(() => { try { return JSON.parse(sessionStorage.getItem('papaya-session')) || null; } catch { return null; } });
  const [authOpen, setAuthOpen] = useState(false);
  const [claims, setClaims] = useState([]);
  const [claimsLoading, setClaimsLoading] = useState(false);
  const [listPage, setListPage] = useState(0);
  const [listHasMore, setListHasMore] = useState(false);
  const [selectedId, setSelectedId] = useState('');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [revision, setRevision] = useState(0);
  const [search, setSearch] = useState('');
  const [lookup, setLookup] = useState('');
  const [tab, setTab] = useState('workflow');
  const [auditPage, setAuditPage] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  const [target, setTarget] = useState(null);
  const [createOpen, setCreateOpen] = useState(false);
  const command = useRef(null);

  function showError(failure) {
    if (failure.name === 'AbortError') return;
    setError({ message: failure.message || 'Cannot reach the API. Check that Spring Boot is running.', code: failure.code, details: failure.details });
    if (failure.status === 401) { sessionStorage.removeItem('papaya-session'); setSession(null); setData(null); setClaims([]); setAuthOpen(true); }
  }
  useEffect(() => {
    if (!session) return;
    const controller = new AbortController();
    setClaimsLoading(true);
    api(`/claims?limit=30&offset=${listPage * 30}`, { token: session.token, signal: controller.signal })
      .then(rows => { setClaims(rows); setListHasMore(rows.length === 30); })
      .catch(showError).finally(() => { if (!controller.signal.aborted) setClaimsLoading(false); });
    return () => controller.abort();
  }, [session, revision, listPage]);

  useEffect(() => {
    if (!session || !selectedId) return;
    const controller = new AbortController();
    setLoading(true); setData(null);
    const options = { token: session.token, signal: controller.signal };
    Promise.all([
      api(`/claims/${selectedId}`, options),
      api(`/claims/${selectedId}/transitions?includeUnauthorized=true`, options),
      api(`/claims/${selectedId}/audit?limit=${PAGE_SIZE}&offset=${auditPage * PAGE_SIZE}`, options),
      api(`/claims/${selectedId}/workflow`, options),
      api(`/claims/${selectedId}/effects?limit=100`, options),
    ]).then(([claim, transitions, audit, workflow, effects]) => setData({ claim, transitions: transitions.transitions, audit, workflow, effects }))
      .catch(showError).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [session, selectedId, revision, auditPage]);

  function connect(account) {
    if (account.tenantId !== session?.tenantId) { setSelectedId(''); setAuditPage(0); }
    setData(null); setClaims([]); setError(null); setListPage(0);
    sessionStorage.setItem('papaya-session', JSON.stringify(account)); setSession(account); setAuthOpen(false);
  }
  function select(id) { setTarget(null); setSelectedId(id); setAuditPage(0); setError(null); }
  function refresh() { setRevision(value => value + 1); }
  async function mutate(action, success) {
    setBusy(true); setError(null); setNotice('');
    try { const result = await action(); setNotice(success); refresh(); return result; }
    catch (failure) { showError(failure); if (failure.status === 409) { setTarget(null); refresh(); } return null; }
    finally { setBusy(false); }
  }
  async function advance(event) {
    event.preventDefault();
    const reason = new FormData(event.currentTarget).get('reason').trim();
    if (!reason) { setError({ message: 'Enter a reason for this transition.' }); return; }
    const fingerprint = JSON.stringify([session.subject, data.claim.claimId, data.claim.version, target.toState, reason]);
    if (command.current?.fingerprint !== fingerprint) command.current = { fingerprint, key: crypto.randomUUID() };
    const result = await mutate(() => api(`/claims/${data.claim.claimId}/transitions`, {
      token: session.token, ...transitionRequest(data.claim, target.toState, reason, command.current.key),
    }), 'Transition committed. The claim and audit history have been refreshed.');
    if (result) { command.current = null; setTarget(null); }
  }
  async function create(event) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    const result = await mutate(() => api('/claims', { token: session.token, method: 'POST', body: { amount: form.get('amount'), policyLimit: form.get('policyLimit') } }), 'New claim created.');
    if (result) { setCreateOpen(false); setListPage(0); select(result.claimId); }
  }
  async function downloadAudit() {
    await mutate(async () => {
      const rows = []; let page;
      do { page = await api(`/claims/${data.claim.claimId}/audit?limit=200&offset=${rows.length}`, { token: session.token }); rows.push(...page); } while (page.length === 200);
      const url = URL.createObjectURL(new Blob([JSON.stringify(rows, null, 2)], { type: 'application/json' }));
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = `audit-${data.claim.claimId}.json`; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
      return true;
    }, 'Audit history exported.');
  }
  const visibleClaims = claims.filter(claim => `${claim.claimId} ${claim.state} ${label(claim.state)}`.toLowerCase().includes(search.toLowerCase()));
  const mayCreate = session?.roles?.some(role => ['document_clerk', 'team_lead'].includes(role));

  return <div className="app-shell">
    <aside className="sidebar">
      <a className="brand" href="/" aria-label="Papaya claims workspace"><span className="brand-mark">p<span /></span><span>papaya<span className="brand-subtitle">CLAIMS WORKSPACE</span></span></a>
      <span className="nav-label">WORKSPACE</span>
      <button className="nav-item active" onClick={() => setTab('workflow')}><Icon name="grid" />Claims<span className="nav-count">{claims.length}</span></button>
      <button className="nav-item" onClick={() => setTab('audit')}><Icon name="clock" />Audit trail</button>
      <div className="sidebar-note"><Icon name="lock" /><strong>Every step, accounted for.</strong><p>Trace each decision from submission to resolution.</p></div>
      <div className="account"><span className="avatar">{(session?.displayName || session?.subject || '?').slice(0, 1).toUpperCase()}</span><div><strong>{session?.displayName || session?.subject || 'Not connected'}</strong><span>{session?.tenantId || 'Connect your account'}</span></div></div>
      <button className="account-switch" disabled={busy} onClick={() => setAuthOpen(true)}>{session ? 'Switch account' : 'Connect account'}<Icon name="arrow" size={16} /></button>
      {session && <button className="sign-out" disabled={busy} onClick={() => { sessionStorage.removeItem('papaya-session'); setSession(null); setData(null); setClaims([]); setSelectedId(''); }}>Sign out</button>}
    </aside>

    <main className="workspace">
      <header className="topbar"><span>Workspace <span className="slash">/</span> <strong>Claims</strong></span><button className={`connection ${session ? 'connected' : ''}`} disabled={busy} onClick={() => setAuthOpen(true)} title="Connect or switch account"><i />{session ? session.roles.map(label).join(' · ') : 'Connect account'}</button></header>
      <div className="page-content">
        <div className="page-heading"><div><div className="eyebrow">CLAIM OPERATIONS</div><h1>A clearer path to resolution.</h1><p>Manage the next step. Keep the full story.</p></div><button className="primary" disabled={!mayCreate || busy} onClick={() => setCreateOpen(true)}><Icon name="plus" size={17} />New claim</button></div>
        {error && <div className="alert error" role="alert"><div><strong>{error.code ? label(error.code) : 'Something went wrong'}</strong><p>{error.message}</p>{error.code === 'CONCURRENT_MODIFICATION' && <p>The latest claim has been loaded. Review it before trying again.</p>}{error.details?.violations?.map((issue, index) => <p key={index}>{issue.field}: {issue.message}</p>)}</div><button className="icon-button" onClick={() => setError(null)} aria-label="Dismiss error"><Icon name="close" /></button></div>}
        {notice && <div className="alert success" role="status"><Icon name="check" size={18} /><span>{notice}</span><button className="icon-button" onClick={() => setNotice('')} aria-label="Dismiss notification"><Icon name="close" /></button></div>}
        {!session ? <section className="welcome panel"><div className="welcome-art"><Icon name="file" size={46} /><span className="welcome-check"><Icon name="check" size={20} /></span></div><span className="eyebrow">YOUR CLAIMS, IN ONE PLACE</span><h2>One workspace. The whole lifecycle.</h2><p>View a claim’s state, see what can happen next, and trace every completed transition.</p><button className="primary" onClick={() => setAuthOpen(true)}>Connect your account<Icon name="arrow" size={16} /></button><div className="welcome-features"><span><Icon name="grid" size={16} />Guided transitions</span><span><Icon name="lock" size={16} />Role permissions</span><span><Icon name="clock" size={16} />Immutable history</span></div></section> : <>
          <div className="overview"><div><span className="stat-icon blue"><Icon name="file" /></span><div><span>Claims on this page</span><strong>{claimsLoading ? '—' : claims.length.toString().padStart(2, '0')}</strong></div></div><div><span className="stat-icon green"><Icon name="arrow" /></span><div><span>Current state</span><strong className="stat-state">{data ? label(data.claim.state) : 'Select a claim'}</strong></div></div><div><span className="stat-icon amber"><Icon name="clock" /></span><div><span>Recorded transitions</span><strong>{data ? data.workflow.historyCount.toString().padStart(2, '0') : '—'}</strong></div></div></div>
          <div className="claims-layout">
            <section className="panel claim-list"><div className="section-heading"><div><h2>Your claims</h2><p>Select a claim to explore its workflow.</p></div><button className="icon-button" disabled={busy || loading || claimsLoading} onClick={refresh} aria-label="Refresh claims"><Icon name="refresh" /></button></div>
              <label className="search"><Icon name="search" size={17} /><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Filter this page…" aria-label="Filter claims on current page" /></label>
              <form className="claim-lookup" onSubmit={event => { event.preventDefault(); select(lookup.trim()); }}><input value={lookup} onChange={event => setLookup(event.target.value)} placeholder="Or open a full claim ID" aria-label="Claim ID" required pattern="[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}" /><button className="icon-button" disabled={busy} aria-label="Open claim"><Icon name="arrow" size={17} /></button></form>
              <div className="claim-items" aria-busy={claimsLoading}>{claimsLoading ? <div className="empty small">Loading claims…</div> : visibleClaims.length ? visibleClaims.map(claim => <button key={claim.claimId} className={`claim-item ${claim.claimId === selectedId ? 'selected' : ''}`} disabled={busy} onClick={() => select(claim.claimId)}><div className="claim-item-top"><strong>CLM-{shortId(claim.claimId)}</strong><Icon name="arrow" size={15} /></div><Badge state={claim.state} /><div className="claim-item-bottom"><span>{money(claim.facts.amount)}</span><time>{date(claim.updatedAt)}</time></div></button>) : <div className="empty small">{search ? 'No matching claims on this page.' : 'No claims yet. Create one to get started.'}</div>}</div>
              <div className="pagination"><button disabled={!listPage || claimsLoading || busy} onClick={() => setListPage(page => page - 1)}>Previous</button><span>Page {listPage + 1}</span><button disabled={!listHasMore || claimsLoading || busy} onClick={() => setListPage(page => page + 1)}>Next</button></div>
            </section>
            <section className="claim-detail" aria-busy={loading}>{loading ? <div className="panel empty">Loading claim and workflow…</div> : !data ? <div className="panel empty detail-empty"><Icon name="file" size={36} /><h2>{selectedId ? 'Claim could not be loaded' : 'Start with a claim'}</h2><p>{selectedId ? 'Check the error above, then refresh or choose another claim.' : 'Choose a claim on the left or open it by its ID.'}</p>{selectedId && <button className="secondary" onClick={refresh}>Try again</button>}</div> : <>
              <div className="panel claim-summary"><div className="section-heading"><div><span className="eyebrow">CLAIM OVERVIEW</span><h2>CLM-{shortId(data.claim.claimId)}</h2></div><Badge state={data.claim.state} /></div><p className="full-id">{data.claim.claimId}</p><div className="claim-facts"><div><span>Claim amount</span><strong>{money(data.claim.facts.amount)}</strong></div><div><span>Policy limit</span><strong>{money(data.claim.facts.policyLimit)}</strong></div><div><span>Assessor</span><strong>{data.claim.facts.assignedAssessorId || 'Unassigned'}</strong></div><div><span>Info requests</span><strong>{data.claim.informationRequestCount}</strong></div></div></div>
              <div className="tabs" role="tablist" aria-label="Claim details"><button role="tab" aria-selected={tab === 'workflow'} onClick={() => setTab('workflow')}><Icon name="grid" size={16} />Workflow</button><button role="tab" aria-selected={tab === 'audit'} onClick={() => setTab('audit')}><Icon name="clock" size={16} />Audit trail<span>{data.workflow.historyCount}</span></button></div>
              {tab === 'workflow' ? <div className="panel workflow-panel"><div className="section-heading"><div><h2>What happens next</h2><p>Available paths from {label(data.claim.state).toLowerCase()}.</p></div><span className="tag">{data.transitions.length} {data.transitions.length === 1 ? 'path' : 'paths'}</span></div>
                <div className="transition-list">{data.transitions.length ? data.transitions.map(transition => <div key={transition.toState} className={`transition-card ${transition.ready ? 'ready' : ''}`}><div className="transition-icon"><Icon name={transition.ready ? 'arrow' : 'lock'} size={19} /></div><div className="transition-body"><div className="transition-title"><h3>{label(transition.toState)}</h3><span className={`readiness ${transition.ready ? 'available' : ''}`}>{transition.ready ? 'Ready to advance' : transition.authorized === false ? 'Not permitted for this account' : 'Conditions pending'}</span></div><div className="required-roles"><span>Required role</span>{transition.requiredRoles.map(role => <span className="role-chip" key={role}>{label(role)}</span>)}</div>{transition.preconditions.length > 0 && <p className="conditions">{transition.preconditions.map(label).join(' · ')}</p>}{transition.blockedBy && <p className="blocked-reason">{transition.blockedBy.message}</p>}{transition.remainingInformationRequests !== undefined && <p className="hint">{transition.remainingInformationRequests} information requests remaining</p>}<button className={transition.ready ? 'primary compact' : 'secondary compact'} disabled={busy || !transition.ready} onClick={() => setTarget(transition)}>Advance claim<Icon name="arrow" size={15} /></button></div></div>) : <div className="empty"><Icon name="check" size={28} /><h3>{data.claim.state === 'CLOSED' ? 'This claim is resolved.' : 'No outgoing transitions.'}</h3><p>All recorded decisions are available in the audit trail.</p></div>}</div>
                <Preconditions key={`${data.claim.claimId}-${data.claim.state}`} claim={data.claim} session={session} busy={busy} onUpdate={(operation, fields) => mutate(() => api(`/mock/${operation}/${data.claim.claimId}`, { token: session.token, method: 'POST', body: { expectedVersion: data.claim.version, ...fields } }), 'Claim conditions updated. Available transitions have been refreshed.')} />
                {data.effects.length > 0 && <details className="effects"><summary>Side effects <span>{data.workflow.pendingSideEffects} pending</span></summary><ul>{data.effects.map(effect => <li key={effect.eventId}><span>{label(effect.action)}</span><Badge state={effect.status} />{effect.lastError && <small>{effect.lastError}</small>}</li>)}</ul></details>}
              </div> : <div className="panel audit-panel"><div className="section-heading"><div><h2>A complete record</h2><p>Successful transitions, in the order they happened.</p></div><button className="secondary compact" disabled={busy} onClick={downloadAudit}><Icon name="download" size={16} />Export JSON</button></div><div className="audit-notice"><Icon name="lock" size={14} />Append-only history · entries cannot be edited or deleted</div>
                {data.audit.length ? <ol className="timeline" start={auditPage * PAGE_SIZE + 1}>{data.audit.map(entry => <li key={entry.auditId}><span className="timeline-number">{entry.sequenceNumber}</span><div className="timeline-content"><div className="timeline-top"><strong>{label(entry.fromState)}<span>→</span>{label(entry.toState)}</strong><time dateTime={entry.occurredAt}>{date(entry.occurredAt)}</time></div><p>{entry.reason}</p><div className="timeline-actor"><span className="tiny-avatar">{entry.actorId.slice(0, 1).toUpperCase()}</span><strong>{entry.actorId}</strong><span className="role-chip">{label(entry.actorRole)}</span></div></div></li>)}</ol> : <div className="empty"><Icon name="clock" size={30} /><h3>No transitions recorded yet.</h3><p>Advancing the claim will create its first audit entry.</p></div>}
                <div className="pagination"><button disabled={!auditPage || busy} onClick={() => setAuditPage(page => page - 1)}>Previous</button><span>{data.workflow.historyCount} entries · page {auditPage + 1}</span><button disabled={(auditPage + 1) * PAGE_SIZE >= data.workflow.historyCount || busy} onClick={() => setAuditPage(page => page + 1)}>Next</button></div>
              </div>}
            </>}</section>
          </div>
        </>}
        <footer>CLAIMS WORKSPACE<span>Clear decisions. A complete history.</span></footer>
      </div>
    </main>
    {authOpen && <AuthDialog session={session} onClose={() => setAuthOpen(false)} onConnect={connect} />}
    {createOpen && <Modal title="Create a claim" subtitle="Start a new claim in the submitted state." onClose={() => setCreateOpen(false)} locked={busy}>{error && <p role="alert" className="inline-error">{error.message}</p>}<form className="stack" onSubmit={create}><label>Claim amount<input name="amount" type="number" min="0.01" max="999999999999.99" step="0.01" required autoFocus /></label><label>Policy limit<input name="policyLimit" type="number" min="0.01" max="999999999999.99" step="0.01" required /></label><button className="primary" disabled={busy}>{busy ? 'Creating…' : 'Create claim'}<Icon name="plus" size={16} /></button></form></Modal>}
    {target && data && <Modal title={`Advance to ${label(target.toState).toLowerCase()}`} subtitle={`CLM-${shortId(data.claim.claimId)} · ${label(data.claim.state)}`} onClose={() => setTarget(null)} locked={busy}>{error && <p role="alert" className="inline-error">{error.message}</p>}<form className="stack" onSubmit={advance}><label>Reason for this decision<textarea name="reason" placeholder="Describe why this claim is ready for the next step…" rows={4} maxLength={2000} required autoFocus /></label><p className="hint">Your user ID, role, reason and time will be recorded in the audit trail.</p><button className="primary" disabled={busy}>{busy ? 'Committing…' : 'Confirm transition'}<Icon name="arrow" size={17} /></button></form></Modal>}
  </div>;
}
