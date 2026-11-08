'use strict';

const state = { csrf: null, user: null, documents: [], selectedDocument: null, view: 'dashboard' };
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
}
function formatDate(value) {
  if (!value) return '—';
  return new Intl.DateTimeFormat(undefined, {dateStyle:'medium', timeStyle:'short'}).format(new Date(value));
}
function showToast(message, error = false) {
  const toast = $('#toast'); toast.textContent = message; toast.classList.toggle('error', error); toast.classList.remove('hidden');
  window.clearTimeout(showToast.timer); showToast.timer = window.setTimeout(() => toast.classList.add('hidden'), 3600);
}
async function refreshCsrf() {
  const response = await fetch('/api/auth/csrf', {credentials:'same-origin'});
  if (!response.ok) throw new Error('Could not initialise secure session. Refresh the page.');
  state.csrf = await response.json();
}
async function api(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = new Headers(options.headers || {});
  if (method !== 'GET' && method !== 'HEAD' && state.csrf) headers.set(state.csrf.headerName, state.csrf.token);
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, {...options, method, headers, credentials:'same-origin'});
  if (response.status === 401) { showLogin(); throw new Error('Your session has ended. Please sign in again.'); }
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try { const data = await response.json(); message = data.message || data.error || data.detail || message; } catch (_) { /* use generic status */ }
    throw new Error(message);
  }
  if (response.status === 204) return null;
  const type = response.headers.get('content-type') || '';
  return type.includes('application/json') ? response.json() : response;
}
function showLogin() {
  $('#app-screen').classList.add('hidden'); $('#login-screen').classList.remove('hidden'); state.user = null;
}
function showApp() { $('#login-screen').classList.add('hidden'); $('#app-screen').classList.remove('hidden'); }
function setView(view) {
  state.view = view;
  $$('.view-section').forEach(section => section.classList.toggle('hidden', section.id !== `view-${view}`));
  $$('.nav-item').forEach(button => button.classList.toggle('active', button.dataset.view === view));
  const titles = {dashboard:'Overview', documents:'Documents', upload:'Upload document', users:'User accounts', audit:'Audit activity'};
  $('#view-title').textContent = titles[view] || 'Overview';
  if (view === 'documents') loadDocuments();
  if (view === 'users') loadUsers();
  if (view === 'audit') loadAudit();
  if (view === 'dashboard') loadDashboard();
}
function applyRoleVisibility() {
  const role = state.user.role;
  $$('.owner-only').forEach(el => el.classList.toggle('hidden', role !== 'OWNER'));
  $$('.admin-only').forEach(el => el.classList.toggle('hidden', role !== 'ADMIN'));
  const docsNav = $('[data-view="documents"]');
  docsNav.classList.toggle('hidden', role === 'ADMIN');
  $('#owner-dashboard').classList.toggle('hidden', role === 'ADMIN');
  $('#admin-dashboard').classList.toggle('hidden', role !== 'ADMIN');
}
async function enterApp() {
  const user = await api('/api/me');
  state.user = user;
  showApp(); applyRoleVisibility();
  $('#user-name').textContent = user.displayName; $('#user-role').textContent = user.role;
  $('#avatar').textContent = (user.displayName || user.email).slice(0,1).toUpperCase();
  $('#welcome-title').textContent = `Welcome back, ${user.displayName.split(' ')[0]}`;
  setView('dashboard');
}
async function signIn(event) {
  event.preventDefault(); $('#login-error').textContent = '';
  const email = $('#login-email').value.trim(); const password = $('#login-password').value;
  try {
    await refreshCsrf();
    await api('/api/auth/login', {method:'POST', body:JSON.stringify({email,password})});
    await refreshCsrf();
    await enterApp();
  } catch (error) { $('#login-error').textContent = error.message; }
}
async function signOut() {
  try { await api('/api/auth/logout', {method:'POST'}); } catch (_) { /* clear local UI regardless */ }
  state.user = null; showLogin(); $('#login-password').value = '';
  await refreshCsrf().catch(() => {});
}
async function loadDashboard() {
  if (!state.user) return;
  if (state.user.role === 'ADMIN') {
    try {
      const data = await api('/api/admin/overview');
      $('#admin-users').textContent = data.users; $('#admin-docs').textContent = data.documents;
      $('#admin-audit').textContent = data.auditEvents; $('#admin-grants').textContent = data.grants;
    } catch (error) { showToast(error.message, true); }
  } else {
    try {
      const docs = await api('/api/documents');
      $('#metric-docs').textContent = docs.length; $('#metric-role').textContent = state.user.role;
    } catch (error) { showToast(error.message, true); }
  }
}
function accessClass(permission) {
  if (permission === 'OWNER') return 'owner'; if (permission === 'EDIT') return 'edit'; return 'read';
}
async function loadDocuments() {
  if (!state.user || state.user.role === 'ADMIN') return;
  const params = new URLSearchParams();
  const mappings = [['q','search-q'],['owner','search-owner'],['category','search-category'],['from','search-from'],['to','search-to']];
  for (const [key,id] of mappings) { const value = $(`#${id}`).value.trim(); if (value) params.set(key,value); }
  const body = $('#documents-table'); body.innerHTML = '<tr><td colspan="6" class="empty-state">Loading documents…</td></tr>';
  try {
    const query = params.toString();
    const docs = await api(`/api/documents${query ? `?${query}` : ''}`); state.documents = docs;
    $('#document-count').textContent = docs.length;
    if (!docs.length) { body.innerHTML = '<tr><td colspan="6" class="empty-state">No documents match this search.</td></tr>'; return; }
    body.innerHTML = docs.map(d => `<tr>
      <td><div class="doc-name">${escapeHtml(d.title)}</div><div class="doc-meta">${escapeHtml(d.originalFilename)} · ${formatBytes(d.sizeBytes)}</div></td>
      <td>${escapeHtml(d.ownerEmail)}</td><td>${escapeHtml(d.category)}</td>
      <td><span class="access-tag ${accessClass(d.permission)}">${escapeHtml(d.permission)}</span></td>
      <td>${formatDate(d.updatedAt)}</td><td><button class="row-link" data-open-doc="${escapeHtml(d.id)}">Open →</button></td>
    </tr>`).join('');
  } catch (error) { body.innerHTML = `<tr><td colspan="6" class="empty-state">${escapeHtml(error.message)}</td></tr>`; }
}
function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`; if (bytes < 1024*1024) return `${(bytes/1024).toFixed(1)} KB`; return `${(bytes/1024/1024).toFixed(1)} MB`;
}
async function uploadDocument(event) {
  event.preventDefault(); $('#upload-error').textContent = '';
  const form = event.currentTarget; const data = new FormData(form);
  const file = data.get('file');
  if (!file || !file.name) { $('#upload-error').textContent = 'Choose a file first.'; return; }
  try {
    await api('/api/documents', {method:'POST', body:data});
    form.reset(); showToast('Document uploaded and kept private.'); setView('documents');
  } catch (error) { $('#upload-error').textContent = error.message; }
}
async function openDocument(id) {
  try {
    const doc = await api(`/api/documents/${encodeURIComponent(id)}`); state.selectedDocument = doc;
    const permissions = doc.canManage ? await api(`/api/documents/${encodeURIComponent(id)}/permissions`) : [];
    renderDocumentModal(doc, permissions); $('#document-modal').classList.remove('hidden');
  } catch (error) { showToast(error.message, true); }
}
function renderDocumentModal(doc, permissions) {
  $('#modal-title').textContent = doc.title;
  const permissionSection = doc.canManage ? `
    <div class="modal-section"><h3>Grant access to a named account</h3>
      <form id="grant-form" class="inline-form"><input name="email" type="email" placeholder="person@company.com" required><select name="permission"><option value="READ">READ</option><option value="EDIT">EDIT</option></select><button class="btn primary" type="submit">Grant access</button></form>
      <div id="permission-list">${permissions.length ? permissions.map(p => `<div class="permission-row"><span><b>${escapeHtml(p.email)}</b><br><small class="muted">${escapeHtml(p.displayName)} · ${escapeHtml(p.permission)}</small></span><button class="danger-link" data-revoke="${escapeHtml(p.id)}">Revoke</button></div>`).join('') : '<p class="muted tiny">No one else has access.</p>'}</div>
    </div>` : '';
  const editSection = doc.canEdit ? `
    <div class="modal-section"><h3>Edit metadata</h3><form id="metadata-form" class="modal-edit-form"><label>Title<input name="title" maxlength="180" value="${escapeHtml(doc.title)}" required></label><label>Category<input name="category" maxlength="80" value="${escapeHtml(doc.category)}" required></label><label class="wide">Description<textarea name="description" maxlength="1000" rows="2">${escapeHtml(doc.description || '')}</textarea></label><button class="btn dark wide" type="submit">Save metadata</button></form></div>
    <div class="modal-section"><h3>Replace file content</h3><form id="replace-form" class="inline-form"><input type="file" name="file" accept=".pdf,.png,.jpg,.jpeg,.txt" required><button class="btn ghost" type="submit">Replace current file</button></form></div>` : '';
  const actions = `<div class="modal-section modal-actions"><button class="btn primary" data-download="${escapeHtml(doc.id)}">Download securely</button>${doc.canManage ? `<button class="btn danger-btn" data-delete="${escapeHtml(doc.id)}">Delete document</button>` : ''}</div>`;
  const info = `
    <div class="detail-grid">
      <div class="detail-item"><small>Filename</small><b>${escapeHtml(doc.originalFilename)}</b></div>
      <div class="detail-item"><small>Category</small><b>${escapeHtml(doc.category)}</b></div>
      <div class="detail-item"><small>Owner</small><b>${escapeHtml(doc.ownerEmail)}</b></div>
      <div class="detail-item"><small>Your access</small><b>${escapeHtml(doc.permission)}</b></div>
      <div class="detail-item"><small>Size</small><b>${formatBytes(doc.sizeBytes)}</b></div>
      <div class="detail-item"><small>Updated</small><b>${formatDate(doc.updatedAt)}</b></div>
    </div><p class="muted tiny">${escapeHtml(doc.description || 'No description provided.')}</p>${actions}${editSection}${permissionSection}`;
  $('#modal-body').innerHTML = info;
}
function closeModal() { $('#document-modal').classList.add('hidden'); state.selectedDocument = null; }
async function downloadDocument(id) {
  try {
    const response = await fetch(`/api/documents/${encodeURIComponent(id)}/download`, {credentials:'same-origin'});
    if (response.status === 401) { showLogin(); throw new Error('Please sign in again.'); }
    if (!response.ok) { const data = await response.json().catch(() => ({})); throw new Error(data.message || `Download denied (${response.status})`); }
    const blob = await response.blob();
    const disposition = response.headers.get('content-disposition') || '';
    const match = disposition.match(/filename\*=UTF-8''([^;]+)/i) || disposition.match(/filename="?([^";]+)"?/i);
    const filename = match ? decodeURIComponent(match[1].replace(/"/g,'')) : 'trustvault-document';
    const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = filename; document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url);
    showToast('Download authorised and recorded.');
  } catch (error) { showToast(error.message, true); }
}
async function submitGrant(event) {
  event.preventDefault(); const form = event.currentTarget; const data = new FormData(form); const doc = state.selectedDocument;
  try {
    await api(`/api/documents/${encodeURIComponent(doc.id)}/permissions`, {method:'POST', body:JSON.stringify({email:data.get('email'),permission:data.get('permission')})});
    showToast('Permission saved.'); await openDocument(doc.id);
  } catch (error) { showToast(error.message, true); }
}
async function revokeGrant(grantId) {
  const doc = state.selectedDocument; if (!doc || !confirm('Revoke this person’s access immediately?')) return;
  try { await api(`/api/documents/${encodeURIComponent(doc.id)}/permissions/${encodeURIComponent(grantId)}`, {method:'DELETE'}); showToast('Access revoked.'); await openDocument(doc.id); }
  catch (error) { showToast(error.message, true); }
}
async function updateMetadata(event) {
  event.preventDefault(); const form = event.currentTarget; const data = new FormData(form); const doc = state.selectedDocument;
  try {
    await api(`/api/documents/${encodeURIComponent(doc.id)}`, {method:'PUT', body:JSON.stringify({title:data.get('title'),category:data.get('category'),description:data.get('description')})});
    showToast('Document details updated.'); await openDocument(doc.id); loadDocuments();
  } catch (error) { showToast(error.message, true); }
}
async function replaceContent(event) {
  event.preventDefault(); const form = event.currentTarget; const data = new FormData(form); const doc = state.selectedDocument;
  try { await api(`/api/documents/${encodeURIComponent(doc.id)}/content`, {method:'PUT', body:data}); showToast('File content replaced.'); await openDocument(doc.id); loadDocuments(); }
  catch (error) { showToast(error.message, true); }
}
async function deleteDocument(id) {
  if (!confirm('Move this document to the deleted state? Its private blob is retained.')) return;
  try { await api(`/api/documents/${encodeURIComponent(id)}`, {method:'DELETE'}); closeModal(); showToast('Document deleted.'); loadDocuments(); }
  catch (error) { showToast(error.message, true); }
}
async function loadUsers() {
  const body = $('#users-table'); body.innerHTML = '<tr><td colspan="4" class="empty-state">Loading…</td></tr>';
  try {
    const users = await api('/api/admin/users');
    body.innerHTML = users.map(user => `<tr><td><div class="doc-name">${escapeHtml(user.displayName)}</div><div class="doc-meta">${escapeHtml(user.email)}</div></td><td><span class="role-tag">${escapeHtml(user.role)}</span></td><td><span class="status-tag ${user.enabled?'':'disabled'}">${user.enabled?'ACTIVE':'DISABLED'}</span></td><td>${user.id && user.email !== state.user.email ? `<button class="row-link" data-toggle-user="${user.id}" data-enabled="${!user.enabled}">${user.enabled?'Disable':'Enable'}</button>` : '<span class="tiny muted">Current account</span>'}</td></tr>`).join('');
  } catch (error) { body.innerHTML = `<tr><td colspan="4" class="empty-state">${escapeHtml(error.message)}</td></tr>`; }
}
async function createUser(event) {
  event.preventDefault(); $('#user-error').textContent = '';
  const form = event.currentTarget; const data = new FormData(form);
  try {
    await api('/api/admin/users', {method:'POST', body:JSON.stringify({displayName:data.get('displayName'),email:data.get('email'),password:data.get('password'),role:data.get('role')})});
    form.reset(); showToast('Account created.'); loadUsers();
  } catch (error) { $('#user-error').textContent = error.message; }
}
async function toggleUser(id, enabled) {
  const verb = enabled ? 'enable' : 'disable'; if (!confirm(`Are you sure you want to ${verb} this account?`)) return;
  try { await api(`/api/admin/users/${id}/status`, {method:'PATCH',body:JSON.stringify({enabled})}); showToast(`Account ${verb}d.`); loadUsers(); }
  catch (error) { showToast(error.message,true); }
}
async function loadAudit() {
  const body = $('#audit-table'); body.innerHTML = '<tr><td colspan="6" class="empty-state">Loading…</td></tr>';
  try {
    const events = await api('/api/admin/audit?limit=200');
    body.innerHTML = events.map(e => `<tr title="${escapeHtml(e.details || '')}"><td>${formatDate(e.createdAt)}</td><td>${escapeHtml(e.actorEmail)}</td><td><b>${escapeHtml(e.action)}</b></td><td>${escapeHtml(e.resourceType)} ${escapeHtml(e.resourceId || '')}</td><td><span class="status-tag ${e.outcome==='SUCCESS'?'':'disabled'}">${escapeHtml(e.outcome)}</span></td><td><code>${escapeHtml((e.eventHash || '').slice(0,16))}…</code></td></tr>`).join('') || '<tr><td colspan="6" class="empty-state">No audit events.</td></tr>';
  } catch (error) { body.innerHTML = `<tr><td colspan="6" class="empty-state">${escapeHtml(error.message)}</td></tr>`; }
}
async function verifyAudit() {
  const box = $('#verify-result'); box.classList.remove('hidden','invalid'); box.textContent = 'Verifying chain…';
  try {
    const result = await api('/api/admin/audit/verify');
    box.classList.toggle('invalid', !result.valid);
    box.innerHTML = `<strong>${result.valid ? '✓ Chain verified' : '⚠ Integrity problem detected'}</strong>${escapeHtml(result.message)} · ${result.checkedEvents} events checked`;
  } catch (error) { box.classList.add('invalid'); box.textContent = error.message; }
}

$('#login-form').addEventListener('submit', signIn);
$('#logout-btn').addEventListener('click', signOut);
$('#main-nav').addEventListener('click', event => { const button = event.target.closest('[data-view]'); if (button && !button.classList.contains('hidden')) setView(button.dataset.view); });
document.addEventListener('click', event => {
  const goto = event.target.closest('[data-goto]'); if (goto) setView(goto.dataset.goto);
  const open = event.target.closest('[data-open-doc]'); if (open) openDocument(open.dataset.openDoc);
  const close = event.target.closest('[data-close-modal]'); if (close) closeModal();
  const download = event.target.closest('[data-download]'); if (download) downloadDocument(download.dataset.download);
  const revoke = event.target.closest('[data-revoke]'); if (revoke) revokeGrant(revoke.dataset.revoke);
  const remove = event.target.closest('[data-delete]'); if (remove) deleteDocument(remove.dataset.delete);
  const toggle = event.target.closest('[data-toggle-user]'); if (toggle) toggleUser(toggle.dataset.toggleUser, toggle.dataset.enabled === 'true');
});
$('#search-btn').addEventListener('click', loadDocuments);
$('#clear-search').addEventListener('click', () => { ['search-q','search-owner','search-category','search-from','search-to'].forEach(id => $(`#${id}`).value=''); loadDocuments(); });
$('#upload-form').addEventListener('submit', uploadDocument);
$('#create-user-form').addEventListener('submit', createUser);
$('#verify-audit').addEventListener('click', verifyAudit);
$('#modal-body').addEventListener('submit', event => {
  if (event.target.id === 'grant-form') submitGrant(event);
  if (event.target.id === 'metadata-form') updateMetadata(event);
  if (event.target.id === 'replace-form') replaceContent(event);
});
document.addEventListener('keydown', event => { if (event.key === 'Escape') closeModal(); });

(async function initialise() {
  try {
    await refreshCsrf();
    const response = await fetch('/api/me', {credentials:'same-origin'});
    if (response.ok) await enterApp();
  } catch (_) { /* first visit: show sign-in */ }
})();
