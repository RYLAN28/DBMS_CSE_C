const reveals = document.querySelectorAll('.reveal');
const observer = new IntersectionObserver((entries) => {
  entries.forEach((entry) => {
    if (entry.isIntersecting) {
      entry.target.classList.add('visible');
      observer.unobserve(entry.target);
    }
  });
}, { threshold: 0.12 });
reveals.forEach((section) => observer.observe(section));

const menuToggle = document.querySelector('.menu-toggle');
const mainNav = document.querySelector('.main-nav');
menuToggle.addEventListener('click', () => {
  const isOpen = mainNav.classList.toggle('open');
  menuToggle.setAttribute('aria-expanded', String(isOpen));
});

document.querySelectorAll('.main-nav a').forEach((link) => {
  link.addEventListener('click', () => {
    mainNav.classList.remove('open');
    menuToggle.setAttribute('aria-expanded', 'false');
  });
});

const searchInput = document.querySelector('#campaign-search');
const filterButtons = document.querySelectorAll('.filter');
const campaignRows = document.querySelectorAll('.campaign-row');
const emptyState = document.querySelector('#empty-state');
let selectedFilter = 'all';

function filterCampaigns() {
  const query = searchInput.value.toLowerCase().trim();
  let visibleCount = 0;
  document.querySelectorAll('.campaign-row').forEach((row) => {
    const matchesFilter = selectedFilter === 'all' || row.dataset.status === selectedFilter;
    const matchesSearch = row.dataset.name.includes(query);
    const shouldShow = matchesFilter && matchesSearch;
    row.style.display = shouldShow ? 'grid' : 'none';
    if (shouldShow) visibleCount += 1;
  });
  emptyState.style.display = visibleCount ? 'none' : 'block';
}

searchInput.addEventListener('input', filterCampaigns);
filterButtons.forEach((button) => {
  button.addEventListener('click', () => {
    filterButtons.forEach((item) => item.classList.remove('active'));
    button.classList.add('active');
    selectedFilter = button.dataset.filter;
    filterCampaigns();
  });
});

const modal = document.querySelector('#campaign-modal');
document.querySelector('#open-campaign').addEventListener('click', () => {
  modal.hidden = false;
  modal.querySelector('input').focus();
});
document.querySelector('#close-campaign').addEventListener('click', () => { modal.hidden = true; });
modal.addEventListener('click', (event) => {
  if (event.target === modal) modal.hidden = true;
});
document.querySelector('#campaign-form').addEventListener('submit', (event) => {
  event.preventDefault();
  modal.hidden = true;
  const f = new FormData(event.target);
  addCampaignRow(f.get('name'), f.get('channel'), f.get('budget'));
  event.target.reset();
  showToast('Campaign created successfully.');
  // save the campaign name in PostgreSQL (works when the Java server is running)
  fetch('/api/campaigns', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ name: f.get('name') }) }).catch(() => {});
});

/* ===== UI upgrade ===== */
const esc = (t) => String(t).replace(/[&<>"']/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

// live date in the hero
document.querySelector('#today').textContent =
  new Date().toLocaleDateString('en-IN', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });

// toast message (replaces alert)
const toast = document.createElement('div');
toast.className = 'toast';
document.body.appendChild(toast);
function showToast(message) {
  toast.textContent = message;
  toast.classList.add('show');
  setTimeout(() => toast.classList.remove('show'), 2500);
}

// add a new campaign row from the modal form
function addCampaignRow(name, channel, budget) {
  const row = document.createElement('article');
  row.className = 'campaign-row';
  row.dataset.status = 'Active';
  row.dataset.name = name.toLowerCase();
  row.innerHTML =
    '<div class="campaign-thumb thumb-monsoon"><span>' + esc(name.slice(0, 2).toUpperCase()) + '</span></div>' +
    '<div class="campaign-info"><h3>' + esc(name) + '</h3><p>' + esc(channel) + '</p></div>' +
    '<div class="campaign-owner"><span class="small-avatar avatar-pink">JD</span><span>You</span></div>' +
    '<div class="campaign-budget"><span>Budget</span><strong>Rs. ' + Number(budget).toLocaleString('en-IN') + '</strong></div>' +
    '<span class="status active-status">Active</span><button class="row-menu" aria-label="Campaign options">•••</button>';
  document.querySelector('#campaign-list').prepend(row);
  filterCampaigns();
}

// count-up animation for whole-number stats
document.querySelectorAll('.stat-card > strong').forEach((el) => {
  if (!/^\d+$/.test(el.textContent)) return;
  const target = Number(el.textContent);
  let n = 0;
  const timer = setInterval(() => {
    n += Math.max(1, Math.round(target / 25));
    if (n >= target) { n = target; clearInterval(timer); }
    el.textContent = n;
  }, 40);
});

// Ad status table: real data from the Java server (/api/ads); sample data if the server is not running
const SAMPLE_ADS = [
  { id: 101, title: 'Summer Collection',  days: 12, pay: 'Fully Paid',     status: 'Completed' },
  { id: 102, title: 'Monsoon Essentials', days: 30, pay: 'Partially Paid', status: 'Active' },
  { id: 103, title: 'Weekend Wellness',   days: 45, pay: 'Fully Paid',     status: 'Active' },
  { id: 104, title: 'Festive Dhamaka',    days: 5,  pay: 'Partially Paid', status: 'Active' },
  { id: 105, title: 'Search Boost',       days: -1, pay: 'Not Paid',       status: 'Expired' },
  { id: 201, title: 'Java Added Ad',      days: 60, pay: 'Not Paid',       status: 'Active' }
];
const payClass = { 'Fully Paid': 'pay-full', 'Partially Paid': 'pay-part', 'Not Paid': 'pay-none' };

function renderAds(list) {
  document.querySelector('#ad-table-body').innerHTML = list.map((ad) => {
    const hasEnd = ad.days !== null;
    const end = hasEnd ? new Date(Date.now() + ad.days * 864e5).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) : '-';
    const left = !hasEnd ? 'No end date' : ad.days < 0 ? 'Expired' : ad.days + ' days left';
    const pct = hasEnd ? Math.max(0, Math.min(100, (ad.days / 60) * 100)) : 0;
    const cls = payClass[ad.pay] || 'pay-none';
    return '<tr><td>' + ad.id + '</td><td><strong>' + esc(ad.title) + '</strong></td><td>' + end + '</td>' +
      '<td>' + left + '<div class="left-bar"><i style="width:' + pct + '%"></i></div></td>' +
      '<td><span class="badge ' + cls + '">' + esc(ad.pay) + '</span></td>' +
      '<td><span class="badge st-' + esc(ad.status.toLowerCase()) + '">' + esc(ad.status) + '</span></td></tr>';
  }).join('');
}

function loadAds() {
  fetch('/api/ads')
    .then((r) => (r.ok ? r.json() : Promise.reject()))
    .then(renderAds)
    .catch(() => {});
}
renderAds(SAMPLE_ADS);
loadAds();

// New advertisement form: sends the details to the Java server, which saves them in PostgreSQL
const adModal = document.querySelector('#ad-modal');
document.querySelector('#open-ad').addEventListener('click', () => { adModal.hidden = false; });
document.querySelector('#close-ad').addEventListener('click', () => { adModal.hidden = true; });
adModal.addEventListener('click', (e) => { if (e.target === adModal) adModal.hidden = true; });
document.querySelector('#ad-form').addEventListener('submit', (event) => {
  event.preventDefault();
  const form = event.target;
  fetch('/api/ads', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(new FormData(form))
  })
    .then((r) => (r.ok ? r.text() : Promise.reject()))
    .then(() => {
      adModal.hidden = true;
      form.reset();
      showToast('Advertisement saved to the database.');
      loadAds();
    })
    .catch(() => showToast('Could not save. Is the Java server running?'));
});

/* ===== working chart, sort button and row menus ===== */

// small popup menu used by all three buttons
function closeMenu() {
  document.querySelectorAll('.popup').forEach((m) => m.remove());
}
function openMenu(anchor, items) {
  const menu = document.createElement('div');
  menu.className = 'popup';
  items.forEach(([label, action]) => {
    const b = document.createElement('button');
    b.textContent = label;
    b.addEventListener('click', () => { closeMenu(); action(); });
    menu.appendChild(b);
  });
  document.body.appendChild(menu);
  const r = anchor.getBoundingClientRect();
  menu.style.top = r.bottom + window.scrollY + 6 + 'px';
  menu.style.left = Math.max(10, Math.min(r.left + window.scrollX, window.innerWidth - 170)) + 'px';
}

// 1. Performance chart: Weekly / Monthly / 6 Months (sample numbers, in thousands of reach)
const DAY = 864e5;
const dayName = (ago, opt) => new Date(Date.now() - ago * DAY).toLocaleDateString('en-IN', opt);
const PERIODS = {
  'Weekly':   { label: 'Last 7 days',   values: [14, 18, 16, 24, 22, 30, 27],
                names: () => [6, 5, 4, 3, 2, 1, 0].map((a) => dayName(a, { weekday: 'short' })) },
  'Monthly':  { label: 'Last 30 days',  values: [9, 17, 14, 26, 34],
                names: () => [30, 22, 15, 8, 0].map((a) => dayName(a, { month: 'short', day: '2-digit' })) },
  '6 Months': { label: 'Last 6 months', values: [12, 20, 18, 29, 25, 36],
                names: () => [150, 120, 90, 60, 30, 0].map((a) => dayName(a, { month: 'short' })) }
};
const MAX = 40;

const chartArea = document.querySelector('.chart-area');
const dots = document.createElement('div');
dots.className = 'dots';
chartArea.appendChild(dots);
chartArea.querySelectorAll('svg circle').forEach((c) => c.remove());

function drawChart(name) {
  const p = PERIODS[name];
  const n = p.values.length;
  const names = p.names();
  const pts = p.values.map((v, i) => [(i / (n - 1)) * 700, 250 - (v / MAX) * 250]);

  let d = 'M' + pts[0][0].toFixed(1) + ',' + pts[0][1].toFixed(1);
  for (let i = 1; i < n; i++) {
    const mx = ((pts[i - 1][0] + pts[i][0]) / 2).toFixed(1);
    d += ' C' + mx + ',' + pts[i - 1][1].toFixed(1) + ' ' + mx + ',' + pts[i][1].toFixed(1) +
         ' ' + pts[i][0].toFixed(1) + ',' + pts[i][1].toFixed(1);
  }
  document.querySelector('.chart-line').setAttribute('d', d);
  document.querySelector('.chart-fill').setAttribute('d', d + ' L700,250 L0,250 Z');

  dots.innerHTML = pts.map((pt, i) =>
    '<i class="dot" style="left:' + (pt[0] / 7).toFixed(1) + '%;top:' + (pt[1] / 2.5).toFixed(1) +
    '%" data-tip="' + names[i] + ': ' + p.values[i] + 'k reach"></i>').join('');

  document.querySelectorAll('.chart-y span').forEach((s, i) => {
    s.textContent = i === 4 ? '0' : (MAX * (4 - i)) / 4 + 'k';
  });
  document.querySelector('.chart-x').innerHTML = names.map((t) => '<span>' + t + '</span>').join('');
  document.querySelector('.performance-panel .eyebrow').textContent = p.label;
  document.querySelector('.period-button').textContent = name + '⌄';
}
drawChart('Monthly');

// 2. Sort button: Recent / Name A-Z / Budget
document.querySelectorAll('.campaign-row').forEach((r, i) => { r.dataset.order = i; });

function sortCampaigns(mode) {
  const list = document.querySelector('#campaign-list');
  const budget = (r) => Number(r.querySelector('.campaign-budget strong').textContent.replace(/\D/g, ''));
  const order = (r) => (r.dataset.order === undefined ? -1 : Number(r.dataset.order));
  [...list.children]
    .sort((a, b) => mode === 'Name A-Z' ? a.dataset.name.localeCompare(b.dataset.name)
                  : mode === 'Budget'   ? budget(b) - budget(a)
                  : order(a) - order(b))
    .forEach((r) => list.appendChild(r));
  document.querySelector('.sort-button strong').textContent = mode;
}

// 3. Row menu (the ••• button on every campaign)
function setRowStatus(row, status) {
  row.dataset.status = status;
  const badge = row.querySelector('.status');
  badge.textContent = status;
  badge.className = 'status ' + (status === 'Active' ? 'active-status' : 'draft-status');
  filterCampaigns();
  showToast('Campaign marked ' + status + '.');
}

// one click listener for all three buttons
document.addEventListener('click', (e) => {
  const rowBtn = e.target.closest('.row-menu');
  const periodBtn = e.target.closest('.period-button');
  const sortBtn = e.target.closest('.sort-button');
  closeMenu();

  if (rowBtn) {
    const row = rowBtn.closest('.campaign-row');
    openMenu(rowBtn, [
      ['Mark Active', () => setRowStatus(row, 'Active')],
      ['Mark Draft', () => setRowStatus(row, 'Draft')],
      ['Delete', () => { row.remove(); filterCampaigns(); showToast('Campaign removed.'); }]
    ]);
  } else if (periodBtn) {
    openMenu(periodBtn, Object.keys(PERIODS).map((k) => [k, () => drawChart(k)]));
  } else if (sortBtn) {
    openMenu(sortBtn, ['Recent', 'Name A-Z', 'Budget'].map((m) => [m, () => sortCampaigns(m)]));
  }
});
