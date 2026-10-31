/**
 * ADWISE® POSTGRESQL ADVERTISING MANAGEMENT SYSTEM
 * Frontend Controller & 3D Wheel Engine
 */

(function () {
  'use strict';

  // Seed data matching advertising_db.sql for instant offline/initial preview
  const INITIAL_SEED_ADS = [
    { id: 101, title: 'Summer Collection', description: 'Summer sale reels and video spots', budget: 42000, tax: 7560, total_cost: 49560, status: 'Completed', ad_type: 'Digital', audience: 'Youth', advertiser_id: 1, advertiser_name: 'Aria Kapoor', platform_id: 1, platform_name: 'Instagram', days: 12, payment_status: 'Fully Paid', show_status: 'Showing', total_paid: 42000, start_date: '2026-05-01', end_date: '2026-05-31', updated_at: '2026-05-15 10:20:00' },
    { id: 102, title: 'Monsoon Essentials', description: 'Rainy season offers and umbrella promo', budget: 28500, tax: 5130, total_cost: 33630, status: 'Active', ad_type: 'Digital', audience: 'Families', advertiser_id: 2, advertiser_name: 'Rohan Verma', platform_id: 2, platform_name: 'YouTube', days: 30, payment_status: 'Partially Paid', show_status: 'Showing', total_paid: 15000, start_date: '2026-06-01', end_date: '2026-06-30', updated_at: '2026-06-05 14:10:00' },
    { id: 103, title: 'Weekend Wellness', description: 'Health supplement newspaper feature', budget: 13750, tax: 2475, total_cost: 16225, status: 'Draft', ad_type: 'Newspaper', audience: 'Adults', advertiser_id: 3, advertiser_name: 'Nisha Shah', platform_id: 4, platform_name: 'Newspaper', days: 45, payment_status: 'Partially Paid', show_status: 'Showing', total_paid: 5000, start_date: '2026-07-01', end_date: '2026-07-15', updated_at: '2026-07-01 09:00:00' },
    { id: 104, title: 'Festive Dhamaka', description: 'Diwali prime time television spot', budget: 65000, tax: 11700, total_cost: 76700, status: 'Active', ad_type: 'TV', audience: 'All', advertiser_id: 1, advertiser_name: 'Aria Kapoor', platform_id: 5, platform_name: 'Star TV', days: 5, payment_status: 'Fully Paid', show_status: 'Showing', total_paid: 65000, start_date: '2026-10-10', end_date: '2026-11-15', updated_at: '2026-10-12 18:30:00' },
    { id: 105, title: 'Search Boost', description: 'Google search ads with keyword bid', budget: 20000, tax: 3600, total_cost: 23600, status: 'Paused', ad_type: 'Digital', audience: 'Youth', advertiser_id: 2, advertiser_name: 'Rohan Verma', platform_id: 3, platform_name: 'Google Ads', days: 0, payment_status: 'Unpaid', show_status: 'Expired', total_paid: 0, start_date: '2026-04-01', end_date: '2026-04-30', updated_at: '2026-04-20 16:45:00' }
  ];

  // State
  let allAds = [...INITIAL_SEED_ADS];
  let filteredAds = [...INITIAL_SEED_ADS];
  let currentIdx = 0;
  let targetIdx = 0;
  let activeAd = filteredAds[0] || null;
  let mode = 'wheel'; // 'wheel' | 'list'
  let cards = [];
  let isDbLive = false;
  let searchFilter = '';
  let typeFilter = 'all';
  let statusFilter = 'all';
  let sortOption = 'id-asc';
  let campaigns = [];
  let selectedCampaignId = null;
  let campaignDragging = false;
  let campaignDragged = false;
  let campaignStartY = 0;
  let campaignScrollTop = 0;

  // DOM Elements
  const $ = (id) => document.getElementById(id);
  const wheel = $('wheel');
  const list = $('list');
  const listItems = $('list-items');
  const listEmpty = $('list-empty');
  const prev = $('prev');
  const cur = $('cur');
  const panel = $('panel');
  const bg = $('bg');
  const closeBtn = $('x');
  const toastEl = $('toast');
  const campaignView = $('campaign-view');
  const campaignRail = $('campaign-rail');

  // Format currency in Indian Rupees
  const fmtInr = (n) => '₹ ' + Number(n || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 });

  const CAMPAIGN_SEED = [
    { id: 1, name: 'Summer Stories', ad_count: 1, total_budget: 42000, ad_types: 'Digital', platforms: 'Instagram' },
    { id: 2, name: 'Monsoon Push', ad_count: 2, total_budget: 48500, ad_types: 'Digital', platforms: 'YouTube, Google Ads' },
    { id: 3, name: 'Festive Season', ad_count: 2, total_budget: 73750, ad_types: 'TV, Newspaper', platforms: 'Star TV, Newspaper' }
  ];

  function campaignCards(items) {
    if (!items.length) {
      return '<div class="campaign-empty">NO CAMPAIGNS FOUND<br><small>Create your first project to begin.</small></div>';
    }
    return items.map((campaign, index) => `
      <button class="campaign-project campaign-project-${index % 6}${campaign.id === selectedCampaignId ? ' selected' : ''}" data-campaign-id="${campaign.id}" type="button">
        <span class="campaign-project-index">${String(index + 1).padStart(2, '0')}</span>
        <span class="campaign-project-art" aria-hidden="true"><i></i><b></b></span>
        <span class="campaign-project-copy">
          <h3>${escapeHtml(campaign.name)}</h3>
          <p>${escapeHtml(campaign.ad_types || 'Multi-channel')} · ${campaign.ad_count || 0} connected ads</p>
        </span>
        <span class="campaign-project-budget">${fmtInr(campaign.total_budget)}<small>${escapeHtml(campaign.platforms || 'No platform')}</small></span>
      </button>
    `).join('');
  }

  function renderCampaignRail() {
    $('campaign-count').textContent = `${campaigns.length} CAMPAIGNS`;
    const cards = campaignCards(campaigns);
    campaignRail.innerHTML = campaigns.length
      ? `<div class="campaign-grid">${cards}</div>`
      : `<div class="campaign-empty">NO CAMPAIGNS FOUND<br><small>Create your first project to begin.</small></div>`;
    campaignRail.querySelectorAll('[data-campaign-id]').forEach((card) => {
      card.addEventListener('click', (event) => {
        if (campaignDragged) {
          event.preventDefault();
          event.stopPropagation();
          return;
        }
        selectCampaign(Number(card.dataset.campaignId));
      });
    });
  }

  campaignRail.addEventListener('mousedown', (event) => {
    if (event.button !== 0) return;
    campaignDragging = true;
    campaignDragged = false;
    campaignStartY = event.pageY;
    campaignScrollTop = campaignRail.scrollTop;
    campaignRail.classList.add('is-dragging');
  });

  window.addEventListener('mousemove', (event) => {
    if (!campaignDragging) return;
    const dy = event.pageY - campaignStartY;
    if (Math.abs(dy) > 4) {
      campaignDragged = true;
    }
    campaignRail.scrollTop = campaignScrollTop - dy;
  });

  window.addEventListener('mouseup', () => {
    if (campaignDragging) {
      campaignDragging = false;
      campaignRail.classList.remove('is-dragging');
      if (campaignDragged) {
        window.setTimeout(() => { campaignDragged = false; }, 50);
      }
    }
  });

  campaignRail.addEventListener('click', (event) => {
    if (campaignDragged) {
      event.preventDefault();
      event.stopPropagation();
    }
  });

  async function selectCampaign(id) {
    selectedCampaignId = id;
    const campaign = campaigns.find((item) => item.id === id);
    if (!campaign) return;

    // Highlight selected card without re-rendering the whole rail
    campaignRail.querySelectorAll('.campaign-project').forEach((card) => {
      card.classList.toggle('selected', Number(card.dataset.campaignId) === id);
    });
    $('campaign-detail-number').textContent = String(campaigns.indexOf(campaign) + 1).padStart(2, '0');
    $('campaign-detail-name').textContent = campaign.name;
    $('campaign-detail-meta').textContent = `${campaign.ad_types || 'Multi-channel'} · ${campaign.platforms || 'No platform'} · PostgreSQL campaign`;
    $('campaign-detail-ads').textContent = campaign.ad_count || 0;
    $('campaign-detail-budget').textContent = fmtInr(campaign.total_budget);
    $('campaign-detail-list').innerHTML = '<div class="campaign-ad-item">Loading connected advertisements…</div>';
    try {
      const res = await fetch(`/api/campaigns/details?id=${encodeURIComponent(id)}`);
      const details = await res.json();
      if (!res.ok) throw new Error(details.error || 'Could not load campaign details');
      $('campaign-detail-list').innerHTML = (details.ads || []).length
        ? details.ads.map((ad) => `<div class="campaign-ad-item"><span>${escapeHtml(ad.title)}</span><strong>${fmtInr(ad.budget)}</strong></div>`).join('')
        : '<div class="campaign-ad-item">No advertisements connected yet.</div>';
    } catch (err) {
      $('campaign-detail-list').innerHTML = '<div class="campaign-ad-item">Connect PostgreSQL to inspect linked ads.</div>';
    }
  }

  async function fetchCampaigns() {
    try {
      const res = await fetch('/api/campaigns');
      const data = await res.json();
      if (!res.ok || !Array.isArray(data)) throw new Error(data.error || 'Campaign API error');
      campaigns = data;
    } catch (err) {
      campaigns = [...CAMPAIGN_SEED];
    }
    if (!selectedCampaignId || !campaigns.some((item) => item.id === selectedCampaignId)) {
      selectedCampaignId = campaigns[0]?.id || null;
    }
    renderCampaignRail();
    if (selectedCampaignId) selectCampaign(selectedCampaignId);
  }

  // Multi-theme gradient palettes matching user specification
  const THEME_CARD_PALETTES = {
    phosphore: [
      'linear-gradient(135deg, #8C2A18 0%, #E0501E 50%, #FFA02E 100%)',
      'linear-gradient(135deg, #b45309 0%, #d97706 50%, #f59e0b 100%)',
      'linear-gradient(135deg, #991b1b 0%, #dc2626 50%, #ea580c 100%)',
      'linear-gradient(135deg, #78350f 0%, #92400e 50%, #d97706 100%)'
    ],
    acidule: [
      'linear-gradient(135deg, #0B5A66 0%, #82BA88 50%, #FFF183 100%)',
      'linear-gradient(135deg, #0f766e 0%, #14b8a6 50%, #FAA4B5 100%)',
      'linear-gradient(135deg, #164e63 0%, #0891b2 50%, #82BA88 100%)',
      'linear-gradient(135deg, #831843 0%, #db2777 50%, #FAA4B5 100%)'
    ],
    sorbet: [
      'linear-gradient(135deg, #832758 0%, #70C1E1 50%, #FAF4C1 100%)',
      'linear-gradient(135deg, #701a75 0%, #c026d3 50%, #F8B77C 100%)',
      'linear-gradient(135deg, #0369a1 0%, #38bdf8 50%, #F8B77C 100%)',
      'linear-gradient(135deg, #9f1239 0%, #f43f5e 50%, #FAF4C1 100%)'
    ],
    lagon: [
      'linear-gradient(135deg, #0B5A66 0%, #398847 50%, #FFF183 100%)',
      'linear-gradient(135deg, #065f46 0%, #10b981 50%, #70C1E1 100%)',
      'linear-gradient(135deg, #0e7490 0%, #06b6d4 50%, #FFF183 100%)',
      'linear-gradient(135deg, #14532d 0%, #22c55e 50%, #a7f3d0 100%)'
    ],
    guimauve: [
      'linear-gradient(135deg, #2B5E10 0%, #C85C88 50%, #FFF183 100%)',
      'linear-gradient(135deg, #831843 0%, #db2777 50%, #FDF9D0 100%)',
      'linear-gradient(135deg, #14532d 0%, #16a34a 50%, #C85C88 100%)',
      'linear-gradient(135deg, #701a75 0%, #a21caf 50%, #FFF183 100%)'
    ],
    neon: [
      'linear-gradient(135deg, #803705 0%, #0B96AF 50%, #82BA88 100%)',
      'linear-gradient(135deg, #0891b2 0%, #06b6d4 50%, #FAA4B5 100%)',
      'linear-gradient(135deg, #059669 0%, #10b981 50%, #FAA4B5 100%)',
      'linear-gradient(135deg, #c2410c 0%, #ea580c 50%, #82BA88 100%)'
    ],
    obsidian: [
      'linear-gradient(135deg, #4338ca 0%, #6366f1 50%, #8b5cf6 100%)',
      'linear-gradient(135deg, #0284c7 0%, #2563eb 50%, #4f46e5 100%)',
      'linear-gradient(135deg, #0f766e 0%, #0d9488 50%, #06b6d4 100%)',
      'linear-gradient(135deg, #701a75 0%, #a21caf 50%, #e11d48 100%)'
    ],
    'minimal-light': [
      'linear-gradient(135deg, #334155 0%, #64748b 50%, #94a3b8 100%)',
      'linear-gradient(135deg, #1e293b 0%, #3b82f6 50%, #60a5fa 100%)',
      'linear-gradient(135deg, #0f766e 0%, #0d9488 50%, #2dd4bf 100%)',
      'linear-gradient(135deg, #881337 0%, #e11d48 50%, #fb7185 100%)'
    ]
  };

  const col = (i, type) => {
    const curTheme = document.documentElement.getAttribute('data-theme') || 'phosphore';
    const list = THEME_CARD_PALETTES[curTheme] || THEME_CARD_PALETTES.phosphore;
    return list[Math.abs(i) % list.length];
  };

  // Toast notification
  let toastTimer;
  function showToast(msg) {
    clearTimeout(toastTimer);
    toastEl.textContent = msg;
    toastEl.classList.add('show');
    toastTimer = setTimeout(() => toastEl.classList.remove('show'), 4500);
  }

  // =====================================================
  // 3D CAROUSEL & PHYSICS ENGINE
  // =====================================================
  const wrap = (v, len) => {
    if (!len) return 0;
    return ((v % len) + len) % len;
  };

  const dist = (a, b, len) => {
    if (!len) return 0;
    let d = wrap(a - b, len);
    return d > len / 2 ? d - len : d;
  };

  function createCards() {
    wheel.innerHTML = '';
    cards = [];
    if (!filteredAds.length) return;

    filteredAds.forEach((ad, i) => {
      const c = document.createElement('div');
      c.className = 'card';
      const bgStyle = col(i, ad.ad_type);
      c.innerHTML = `
        <i style="background:${bgStyle}">
          <div class="card-top-tags">
            <span class="card-num">${String(i + 1).padStart(2, '0')}</span>
            <span class="card-type">${ad.ad_type}</span>
          </div>
          <div class="card-center">
            <div class="card-title">${escapeHtml(ad.title)}</div>
            <div class="card-adv">${escapeHtml(ad.advertiser_name || 'Advertiser')} · ${escapeHtml(ad.platform_name || 'Platform')}</div>
          </div>
          <div class="card-bot-tags">
            <span class="card-budget">${fmtInr(ad.budget)}</span>
            <span class="card-status">${ad.status}</span>
          </div>
        </i>
      `;
      c.addEventListener('click', () => {
        if (Math.abs(currentIdx - i) < 0.3) {
          openDetailPanel(i);
        } else {
          targetIdx = i;
        }
      });
      wheel.appendChild(c);
      cards.push(c);
    });
  }

  function renderList() {
    listItems.innerHTML = '';
    if (!filteredAds.length) {
      listEmpty.style.display = 'block';
      return;
    }
    listEmpty.style.display = 'none';

    filteredAds.forEach((ad, i) => {
      const row = document.createElement('div');
      row.className = 'row';
      const bgStyle = col(i, ad.ad_type);
      const isPaid = ad.payment_status === 'Fully Paid';
      const payClass = isPaid ? 'badge-paid' : (ad.payment_status === 'Partially Paid' ? 'badge-partial' : 'badge-unpaid');
      const stClass = ad.status === 'Active' ? 'badge-active' : (ad.status === 'Paused' ? 'badge-paused' : 'badge-completed');

      row.innerHTML = `
        <span>${String(i + 1).padStart(2, '0')}</span>
        <span>#${ad.id}</span>
        <span class="r-title">${escapeHtml(ad.title)}</span>
        <span class="r-meta">${escapeHtml(ad.advertiser_name)} · ${escapeHtml(ad.platform_name)}</span>
        <span><span class="badge">${ad.ad_type}</span></span>
        <span><strong>${fmtInr(ad.budget)}</strong></span>
        <span>${ad.days !== null && ad.days !== undefined ? (ad.days >= 0 ? ad.days + 'd left' : 'Expired') : '-'}</span>
        <span><span class="badge ${payClass}">${ad.payment_status}</span></span>
        <span><span class="badge ${stClass}">${ad.status}</span></span>
        <span class="r-actions">
          <button class="btn-icon-sm btn-action-edit" data-id="${ad.id}" title="Edit Ad">✎</button>
          <button class="btn-icon-sm btn-action-pay" data-id="${ad.id}" title="Record Payment">₹</button>
          <button class="btn-icon-sm btn-icon-del btn-action-del" data-id="${ad.id}" title="Delete Ad">✕</button>
        </span>
      `;

      row.onmouseenter = () => {
        setFocusedAd(i);
        prev.style.background = bgStyle;
        if ($('prev-badge')) $('prev-badge').textContent = ad.ad_type;
        if ($('prev-id')) $('prev-id').textContent = '#' + ad.id;
        if ($('prev-title')) $('prev-title').textContent = ad.title;
        if ($('prev-advertiser')) $('prev-advertiser').textContent = `${ad.advertiser_name || 'ADVERTISER'} · ${ad.platform_name || 'PLATFORM'}`;
        if ($('prev-budget')) $('prev-budget').textContent = fmtInr(ad.budget);
        if ($('prev-status')) {
          $('prev-status').textContent = ad.status;
          $('prev-status').className = 'badge ' + (ad.status === 'Active' ? 'badge-active' : (ad.status === 'Paused' ? 'badge-paused' : 'badge-completed'));
        }
        prev.classList.add('on');
      };

      row.onmouseleave = () => {
        prev.classList.remove('on');
      };

      row.onclick = (e) => {
        if (e.target.closest('.r-actions')) return;
        openDetailPanel(i);
      };

      // Button actions inside list
      const editBtn = row.querySelector('.btn-action-edit');
      const payBtn = row.querySelector('.btn-action-pay');
      const delBtn = row.querySelector('.btn-action-del');
      const actionsSpan = row.querySelector('.r-actions');

      if (actionsSpan) {
        // When hovering over editing/action icons, immediately disappear so icons are unobscured & clickable
        actionsSpan.onmouseenter = (e) => {
          e.stopPropagation();
          prev.classList.remove('on');
        };
        actionsSpan.onmouseleave = () => {
          prev.classList.add('on');
        };
      }

      editBtn.onclick = (e) => { e.stopPropagation(); openUpdateModal(ad.id); };
      payBtn.onclick = (e) => { e.stopPropagation(); openPaymentModal(ad.id); };
      delBtn.onclick = (e) => { e.stopPropagation(); openDeleteModal(ad.id); };

      listItems.appendChild(row);
    });

    listItems.onmouseleave = () => {
      prev.classList.remove('on');
    };
  }

  function setFocusedAd(i) {
    const N = filteredAds.length;
    if (N === 0) {
      $('hn').textContent = '--';
      $('ht').innerHTML = `<span class="ov"><span>NO ADVERTISEMENTS</span></span><span class="ov"><span>INSERT AN AD TO BEGIN</span></span>`;
      $('hy').textContent = '₹ 0';
      return;
    }
    i = wrap(i, N);
    if (i === currentIdx && $('ht').firstChild && activeAd === filteredAds[i]) return;

    activeAd = filteredAds[i];
    $('hn').textContent = String(i + 1).padStart(2, '0');
    $('hy').textContent = fmtInr(activeAd.budget);
    $('ht').innerHTML = `
      <span class="ov"><span>${escapeHtml(activeAd.title)}</span></span>
      <span class="ov"><span>${escapeHtml(activeAd.advertiser_name || 'ADVERTISER')} · ${escapeHtml(activeAd.platform_name || 'PLATFORM')} · ${activeAd.ad_type}</span></span>
    `;

    // Highlight row in list
    const rows = document.querySelectorAll('.row');
    rows.forEach((r, k) => r.classList.toggle('on', k === i));

    // Retrigger heading slide-up animation
    ['hn', 'hy'].forEach((id) => {
      const el = $(id);
      el.style.animation = 'none';
      el.offsetWidth; // trigger reflow
      el.style.animation = '';
    });
  }

  // Animation frame loop for smooth 3D carousel physics
  function frame() {
    currentIdx += (targetIdx - currentIdx) * 0.1;
    const N = filteredAds.length;

    if (mode === 'wheel' && N > 0) {
      const R = window.innerHeight * 1.8;
      const step = (Math.PI * 2) / N;

      cards.forEach((c, k) => {
        const a = dist(k, currentIdx, N) * step;
        const x = R * Math.sin(a);
        const y = R * (1 - Math.cos(a));
        c.style.transform = `translate(${x}px, ${y}px) rotate(${a * 1.2}rad)`;
        c.style.zIndex = 100 - Math.round(Math.abs(dist(k, currentIdx, N)) * 5);
      });

      setFocusedAd(Math.round(currentIdx));
    }

    requestAnimationFrame(frame);
  }

  // Snap to integer card index
  const snap = () => {
    targetIdx = Math.round(targetIdx);
  };

  // Wheel scroll interaction
  let wheelTimer;
  wheel.addEventListener('wheel', (e) => {
    e.preventDefault();
    targetIdx += (Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY) * 0.003;
    clearTimeout(wheelTimer);
    wheelTimer = setTimeout(snap, 120);
  }, { passive: false });

  // Pointer drag interaction
  let dragX = null;
  let dragT0 = 0;
  let dragMoved = 0;

  wheel.addEventListener('pointerdown', (e) => {
    dragX = e.clientX;
    dragT0 = targetIdx;
    dragMoved = 0;
    wheel.setPointerCapture(e.pointerId);
  });

  wheel.addEventListener('pointermove', (e) => {
    // Custom cursor follower
    cur.style.left = e.clientX + 'px';
    cur.style.top = e.clientY + 'px';
    cur.style.opacity = '1';

    if (dragX !== null) {
      dragMoved = Math.max(dragMoved, Math.abs(e.clientX - dragX));
      targetIdx = dragT0 - (e.clientX - dragX) / (window.innerWidth * 0.14);
    }
  });

  wheel.addEventListener('pointerleave', () => {
    cur.style.opacity = '0';
  });

  wheel.addEventListener('pointerup', () => {
    if (dragX !== null) {
      dragX = null;
      snap();
      if (dragMoved < 6 && filteredAds.length) {
        openDetailPanel(Math.round(currentIdx));
      }
    }
  });

  // Keyboard navigation
  window.addEventListener('keydown', (e) => {
    if (document.querySelector('.modal-dialog:not([hidden])')) {
      if (e.key === 'Escape') closeAllModals();
      return;
    }
    if (panel.classList.contains('on')) {
      if (e.key === 'Escape') closeDetailPanel();
      return;
    }
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      targetIdx = Math.round(targetIdx) + 1;
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      targetIdx = Math.round(targetIdx) - 1;
    } else if (e.key === 'Enter') {
      openDetailPanel(Math.round(currentIdx));
    }
  });

  $('ht').onclick = () => openDetailPanel(Math.round(currentIdx));

  // Switch View Mode: Wheel vs List
  function setViewMode(m) {
    mode = m;
    document.body.classList.remove('campaign-mode');
    $('search-bar').style.display = '';
    $('head').style.display = '';
    document.querySelector('.switch').style.display = '';
    campaignView.classList.remove('on');
    document.querySelectorAll('.nav-links a').forEach((link) => link.classList.remove('on'));
    $('nav-library').classList.add('on');
    $('bw').classList.toggle('on', m === 'wheel');
    $('bl').classList.toggle('on', m === 'list');
    $('bw').setAttribute('aria-pressed', String(m === 'wheel'));
    $('bl').setAttribute('aria-pressed', String(m === 'list'));
    wheel.classList.toggle('off', m === 'list');
    list.classList.toggle('on', m === 'list');
    // Keep preview thumbnail hidden initially; only show on hovering a row
    prev.classList.remove('on');
    $('hint').textContent = m === 'list' ? 'HOVER ROW TO PREVIEW · CLICK FOR DETAILS' : 'SCROLL / DRAG / PRESS ARROW KEYS';

    if (m === 'list') {
      setFocusedAd(Math.round(currentIdx));
    } else {
      targetIdx = currentIdx = wrap(Math.round(currentIdx), filteredAds.length || 1);
    }
  }

  $('bw').onclick = () => setViewMode('wheel');
  $('bl').onclick = () => setViewMode('list');
  $('nav-campaigns').onclick = (e) => {
    e.preventDefault();
    mode = 'campaigns';
    document.body.classList.add('campaign-mode');
    $('search-bar').style.display = 'none';
    $('head').style.display = 'none';
    document.querySelector('.switch').style.display = 'none';
    wheel.classList.add('off');
    list.classList.remove('on');
    prev.classList.remove('on');
    campaignView.classList.add('on');
    document.querySelectorAll('.nav-links a').forEach((link) => link.classList.remove('on'));
    $('nav-campaigns').classList.add('on');
    $('hint').textContent = 'SCROLL / SELECT A CAMPAIGN PROJECT TO INSPECT';
    fetchCampaigns().then(() => {
      campaignView.scrollTop = 0;
    });
  };

  // =====================================================
  // SLIDE-IN EDITORIAL DETAIL PANEL
  // =====================================================
  function openDetailPanel(i) {
    const N = filteredAds.length;
    if (!N) return;
    i = wrap(i, N);
    const d = filteredAds[i];
    activeAd = d;

    $('pn').textContent = String(i + 1).padStart(2, '0');
    $('pd').textContent = `(${d.days !== null && d.days !== undefined ? d.days + ' DAYS LEFT' : 'NO END DATE'})`;
    $('pv').style.background = col(i, d.ad_type);
    $('pa').textContent = `${(d.advertiser_name || 'ADVERTISER').toUpperCase()} · ${(d.platform_name || 'PLATFORM').toUpperCase()}`;
    $('pt').textContent = d.title;
    $('px').textContent = d.description || 'No description provided for this advertisement in PostgreSQL.';
    $('py').textContent = fmtInr(d.total_cost || (d.budget * 1.18));

    // Badges
    $('panel-type-badge').textContent = d.ad_type;
    $('panel-status-badge').textContent = d.status;
    $('panel-status-badge').className = 'badge ' + (d.status === 'Active' ? 'badge-active' : (d.status === 'Paused' ? 'badge-paused' : 'badge-completed'));
    $('panel-pay-badge').textContent = d.payment_status;
    $('panel-pay-badge').className = 'badge ' + (d.payment_status === 'Fully Paid' ? 'badge-paid' : (d.payment_status === 'Partially Paid' ? 'badge-partial' : 'badge-unpaid'));

    // Metrics
    $('m-budget').textContent = fmtInr(d.budget);
    $('m-tax').textContent = fmtInr(d.tax || (d.budget * 0.18));
    $('m-total').textContent = fmtInr(d.total_cost || (d.budget * 1.18));
    $('m-paid').textContent = fmtInr(d.total_paid || 0);
    $('m-audience').textContent = (d.audience || 'ALL').toUpperCase();
    $('m-timeline').textContent = `${d.start_date || 'START'} → ${d.end_date || 'END'}`;
    $('m-id').textContent = '#' + d.id;
    $('m-updated').textContent = d.updated_at || 'Just now';

    panel.classList.add('on');
    bg.classList.add('on');
    closeBtn.classList.add('on');
  }

  function closeDetailPanel() {
    panel.classList.remove('on');
    bg.classList.remove('on');
    closeBtn.classList.remove('on');
  }

  closeBtn.onclick = closeDetailPanel;
  bg.onclick = () => {
    closeDetailPanel();
    closeAllModals();
  };

  // Panel action buttons
  $('btn-panel-update').onclick = () => {
    if (activeAd) {
      closeDetailPanel();
      openUpdateModal(activeAd.id);
    }
  };

  $('btn-panel-delete').onclick = () => {
    if (activeAd) {
      closeDetailPanel();
      openDeleteModal(activeAd.id);
    }
  };

  $('btn-panel-pay').onclick = () => {
    if (activeAd) {
      closeDetailPanel();
      openPaymentModal(activeAd.id);
    }
  };

  // =====================================================
  // SEARCH & FILTERING CONTROLS
  // =====================================================
  const searchInput = $('filter-search');
  const searchClear = $('search-clear');
  const sortSelect = $('sort-select');

  function applyFilters() {
    const q = searchFilter.toLowerCase().trim();

    filteredAds = allAds.filter((ad) => {
      // Type filter
      if (typeFilter !== 'all' && ad.ad_type.toLowerCase() !== typeFilter.toLowerCase()) {
        return false;
      }
      // Status filter
      if (statusFilter !== 'all' && ad.status.toLowerCase() !== statusFilter.toLowerCase()) {
        return false;
      }
      // Search query
      if (q) {
        const matchTitle = (ad.title || '').toLowerCase().includes(q);
        const matchDesc = (ad.description || '').toLowerCase().includes(q);
        const matchAdv = (ad.advertiser_name || '').toLowerCase().includes(q);
        const matchPlat = (ad.platform_name || '').toLowerCase().includes(q);
        const matchAud = (ad.audience || '').toLowerCase().includes(q);
        const matchId = String(ad.id).includes(q);
        if (!matchTitle && !matchDesc && !matchAdv && !matchPlat && !matchAud && !matchId) {
          return false;
        }
      }
      return true;
    });

    // Sorting
    filteredAds.sort((a, b) => {
      if (sortOption === 'budget-desc') return (b.budget || 0) - (a.budget || 0);
      if (sortOption === 'budget-asc') return (a.budget || 0) - (b.budget || 0);
      if (sortOption === 'days-asc') return (a.days ?? 9999) - (b.days ?? 9999);
      if (sortOption === 'title-asc') return (a.title || '').localeCompare(b.title || '');
      if (sortOption === 'id-desc') return b.id - a.id;
      return a.id - b.id; // default id-asc
    });

    $('foot-count').textContent = `${filteredAds.length} ADS LOADED`;

    currentIdx = 0;
    targetIdx = 0;
    createCards();
    renderList();
    setFocusedAd(0);
  }

  searchInput.addEventListener('input', (e) => {
    searchFilter = e.target.value;
    searchClear.style.display = searchFilter ? 'block' : 'none';
    applyFilters();
  });

  searchClear.addEventListener('click', () => {
    searchInput.value = '';
    searchFilter = '';
    searchClear.style.display = 'none';
    applyFilters();
    searchInput.focus();
  });

  // Filter chips click handling
  document.querySelectorAll('.filter-chips .chip').forEach((chip) => {
    chip.addEventListener('click', () => {
      if (chip.dataset.filter !== undefined) {
        document.querySelectorAll('.filter-chips .chip[data-filter]').forEach((c) => c.classList.remove('on'));
        chip.classList.add('on');
        typeFilter = chip.dataset.filter;
      } else if (chip.dataset.status !== undefined) {
        document.querySelectorAll('.filter-chips .chip[data-status]').forEach((c) => c.classList.remove('on'));
        chip.classList.add('on');
        statusFilter = chip.dataset.status;
      }
      applyFilters();
    });
  });

  sortSelect.addEventListener('change', (e) => {
    sortOption = e.target.value;
    applyFilters();
  });

  // =====================================================
  // MODAL MANAGEMENT & CRUD OPERATIONS
  // =====================================================
  function openModal(id) {
    closeAllModals();
    const m = $(id);
    if (m) {
      m.hidden = false;
      bg.classList.add('on');
    }
  }

  function closeAllModals() {
    document.querySelectorAll('.modal-dialog').forEach((m) => (m.hidden = true));
    if (!panel.classList.contains('on')) {
      bg.classList.remove('on');
    }
  }

  document.querySelectorAll('[data-close]').forEach((btn) => {
    btn.onclick = () => closeAllModals();
  });

  // --- 1. INSERT ADVERTISEMENT (INSERT) ---
  $('nav-insert').onclick = (e) => {
    e.preventDefault();
    openModal('modal-insert');
  };
  $('btn-empty-insert').onclick = () => openModal('modal-insert');

  // --- 1A. INSERT ADVERTISER (INSERT) ---
  $('nav-advertiser').onclick = (e) => {
    e.preventDefault();
    openModal('modal-advertiser');
  };

  $('form-advertiser').addEventListener('submit', async (e) => {
    e.preventDefault();
    const form = e.target;
    const submitBtn = $('btn-submit-advertiser');
    submitBtn.disabled = true;
    submitBtn.textContent = 'SAVING TO POSTGRESQL...';

    const data = Object.fromEntries(new FormData(form).entries());
    try {
      const res = await fetch('/api/advertisers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(data)
      });
      const responseText = await res.text();
      let result;
      try {
        result = JSON.parse(responseText);
      } catch {
        throw new Error(`Server returned HTTP ${res.status} instead of JSON. Restart AdServer to load /api/advertisers.`);
      }
      if (!res.ok) throw new Error(result.error || 'Failed to insert advertiser');

      closeAllModals();
      form.reset();
      await fetchMeta();
      showToast(`Advertiser "${data.name}" saved to PostgreSQL (ID #${result.id}).`);
    } catch (err) {
      showToast(`Advertiser was not saved: ${err.message}`);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = 'INSERT ADVERTISER ↗';
    }
  });

  $('form-insert').addEventListener('submit', async (e) => {
    e.preventDefault();
    const form = e.target;
    const submitBtn = $('btn-submit-insert');
    submitBtn.disabled = true;
    submitBtn.textContent = 'SAVING TO POSTGRESQL...';

    const formData = new FormData(form);
    const data = Object.fromEntries(formData.entries());

    try {
      const res = await fetch('/api/ads', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(data)
      });
      const result = await res.json();
      if (!res.ok) throw new Error(result.error || 'Failed to insert ad');

      showToast(`Advertisement inserted into PostgreSQL (ID #${result.id})!`);
      closeAllModals();
      form.reset();
      await fetchAds();
    } catch (err) {
      console.warn('API insert failed, updating local state:', err);
      // Fallback local insert
      const newId = Math.max(...allAds.map((a) => a.id), 100) + 1;
      const advSelect = $('ins-advertiser');
      const platSelect = $('ins-platform');
      const advName = advSelect.options[advSelect.selectedIndex].text.split('·')[1]?.trim() || 'Advertiser';
      const platName = platSelect.options[platSelect.selectedIndex].text.split('·')[1]?.trim() || 'Platform';
      const budgetNum = Number(data.budget);

      const newAd = {
        id: newId,
        title: data.title,
        description: data.description || '',
        budget: budgetNum,
        tax: budgetNum * 0.18,
        total_cost: budgetNum * 1.18,
        status: data.status || 'Active',
        ad_type: data.ad_type || 'Digital',
        audience: data.audience || 'All',
        advertiser_id: Number(data.advertiser_id),
        advertiser_name: advName,
        platform_id: Number(data.platform_id),
        platform_name: platName,
        days: Number(data.days) || 30,
        payment_status: 'Unpaid',
        show_status: 'Showing',
        total_paid: 0,
        start_date: new Date().toISOString().split('T')[0],
        end_date: new Date(Date.now() + (Number(data.days) || 30) * 864e5).toISOString().split('T')[0],
        updated_at: new Date().toLocaleString()
      };

      allAds.unshift(newAd);
      applyFilters();
      closeAllModals();
      form.reset();
      showToast(`Local Ad #${newId} created. Note: Connect PostgreSQL to persist permanently.`);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = 'INSERT INTO DATABASE ↗';
    }
  });

  // --- 1B. CREATE CAMPAIGN (INSERT + campaign_ad links) ---
  function openCampaignModal() {
    const select = $('campaign-ads');
    select.innerHTML = allAds.map((ad) =>
      `<option value="${ad.id}">#${ad.id} · ${escapeHtml(ad.title)}</option>`
    ).join('');
    openModal('modal-campaign');
  }

  $('btn-create-campaign').onclick = openCampaignModal;
  $('form-campaign').addEventListener('submit', async (e) => {
    e.preventDefault();
    const form = e.target;
    const submitBtn = $('btn-submit-campaign');
    const selectedAds = [...$('campaign-ads').selectedOptions].map((option) => option.value);
    const name = $('campaign-name').value.trim();
    if (!name) return;
    submitBtn.disabled = true;
    submitBtn.textContent = 'SAVING TO POSTGRESQL...';
    try {
      const res = await fetch('/api/campaigns', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ name, ad_ids: selectedAds.join(',') })
      });
      const result = await res.json();
      if (!res.ok) throw new Error(result.error || 'Failed to create campaign');
      closeAllModals();
      form.reset();
      showToast(`Campaign "${name}" saved to PostgreSQL.`);
      await fetchCampaigns();
    } catch (err) {
      const localId = campaigns.reduce((max, campaign) => Math.max(max, Number(campaign.id) || 0), 0) + 1;
      campaigns.unshift({
        id: localId,
        name,
        ad_count: 0,
        total_budget: 0,
        ad_types: 'Multi-channel',
        platforms: 'No platform'
      });
      selectedCampaignId = localId;
      closeAllModals();
      form.reset();
      renderCampaignRail();
      selectCampaign(localId);
      showToast(`Campaign "${name}" added to this view. Connect PostgreSQL to persist it.`);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = 'SAVE TO POSTGRESQL ↗';
    }
  });

  // --- 2. UPDATE ADVERTISEMENT (UPDATE) ---
  function openUpdateModal(adId) {
    const ad = allAds.find((a) => a.id === Number(adId));
    if (!ad) return;

    $('upd-id').value = ad.id;
    $('upd-id-badge').textContent = '#' + ad.id;
    $('upd-title').value = ad.title;
    $('upd-budget').value = ad.budget;
    $('upd-desc').value = ad.description || '';
    $('upd-status').value = ad.status;
    $('upd-type').value = ad.ad_type;
    $('upd-audience').value = ad.audience || '';

    openModal('modal-update');
  }

  $('form-update').addEventListener('submit', async (e) => {
    e.preventDefault();
    const form = e.target;
    const submitBtn = $('btn-submit-update');
    submitBtn.disabled = true;
    submitBtn.textContent = 'UPDATING IN POSTGRESQL...';

    const formData = new FormData(form);
    const data = Object.fromEntries(formData.entries());

    try {
      const res = await fetch('/api/ads/update', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(data)
      });
      const result = await res.json();
      if (!res.ok) throw new Error(result.error || 'Failed to update ad');

      showToast(`Ad #${data.id} updated! PostgreSQL trigger logged status.`);
      closeAllModals();
      await fetchAds();
    } catch (err) {
      console.warn('API update failed, updating local state:', err);
      const ad = allAds.find((a) => a.id === Number(data.id));
      if (ad) {
        ad.title = data.title;
        ad.budget = Number(data.budget);
        ad.tax = ad.budget * 0.18;
        ad.total_cost = ad.budget * 1.18;
        ad.description = data.description;
        ad.status = data.status;
        ad.ad_type = data.ad_type;
        ad.audience = data.audience;
        ad.updated_at = new Date().toLocaleString();
        applyFilters();
      }
      closeAllModals();
      showToast(`Ad #${data.id} updated in view.`);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = 'SAVE CHANGES (UPDATE) ↗';
    }
  });

  // --- 3. DELETE ADVERTISEMENT (DELETE) ---
  let pendingDeleteId = null;
  function openDeleteModal(adId) {
    const ad = allAds.find((a) => a.id === Number(adId));
    if (!ad) return;
    pendingDeleteId = ad.id;
    $('del-id').textContent = '#' + ad.id;
    $('del-title').textContent = ad.title;
    openModal('modal-delete');
  }

  $('btn-confirm-delete').onclick = async () => {
    if (!pendingDeleteId) return;
    const btn = $('btn-confirm-delete');
    btn.disabled = true;
    btn.textContent = 'DELETING...';

    try {
      const res = await fetch('/api/ads/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ id: pendingDeleteId })
      });
      const result = await res.json();
      if (!res.ok) throw new Error(result.error || 'Failed to delete');

      showToast(`Ad #${pendingDeleteId} permanently deleted from database.`);
      closeAllModals();
      closeDetailPanel();
      await fetchAds();
    } catch (err) {
      console.warn('API delete failed, removing locally:', err);
      allAds = allAds.filter((a) => a.id !== pendingDeleteId);
      applyFilters();
      closeAllModals();
      closeDetailPanel();
      showToast(`Ad #${pendingDeleteId} removed.`);
    } finally {
      btn.disabled = false;
      btn.textContent = 'PERMANENTLY DELETE';
      pendingDeleteId = null;
    }
  };

  // --- 4. RECORD PAYMENT (TRANSACTION) ---
  function openPaymentModal(adId) {
    const ad = allAds.find((a) => a.id === Number(adId));
    if (!ad) return;

    $('pay-ad-id').value = ad.id;
    $('pay-ad-name').textContent = `#${ad.id} · ${ad.title} (Budget: ${fmtInr(ad.budget)})`;
    const remaining = Math.max(0, ad.budget - (ad.total_paid || 0));
    $('pay-amount').value = remaining > 0 ? remaining : 1000;

    openModal('modal-pay');
  }

  $('form-pay').addEventListener('submit', async (e) => {
    e.preventDefault();
    const form = e.target;
    const submitBtn = $('btn-submit-pay');
    submitBtn.disabled = true;
    submitBtn.textContent = 'COMMITTING TRANSACTION...';

    const formData = new FormData(form);
    const data = Object.fromEntries(formData.entries());

    try {
      const res = await fetch('/api/payments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(data)
      });
      const result = await res.json();
      if (!res.ok) throw new Error(result.error || 'Payment failed');

      showToast(`Payment of ₹${data.amount} saved via transaction!`);
      closeAllModals();
      await fetchAds();
    } catch (err) {
      console.warn('Payment API error:', err);
      const ad = allAds.find((a) => a.id === Number(data.id || data.ad_id));
      if (ad) {
        ad.total_paid = (ad.total_paid || 0) + Number(data.amount);
        if (ad.total_paid >= ad.budget) ad.payment_status = 'Fully Paid';
        else ad.payment_status = 'Partially Paid';
        applyFilters();
      }
      closeAllModals();
      showToast(`Payment of ₹${data.amount} recorded.`);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = 'COMMIT PAYMENT ↗';
    }
  });

  // =====================================================
  // SQL ENGINE, PROCEDURES, TRIGGERS & AUDIT LOGS
  // =====================================================
  $('nav-sql').onclick = (e) => {
    e.preventDefault();
    openModal('modal-sql');
    loadLogs();
    loadReportView();
  };

  // SQL Tabs
  document.querySelectorAll('.sql-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.sql-tab').forEach((t) => t.classList.remove('on'));
      tab.classList.add('on');
      const targetId = tab.dataset.tab;
      document.querySelectorAll('.tab-content').forEach((tc) => (tc.style.display = 'none'));
      $(targetId).style.display = 'block';

      if (targetId === 'tab-logs') loadLogs();
      if (targetId === 'tab-views') loadReportView();
    });
  });

  // Run PostgreSQL Stored Function: expire_old_ads()
  $('btn-run-expire').onclick = async () => {
    const btn = $('btn-run-expire');
    const resultPill = $('expire-result');
    btn.disabled = true;
    resultPill.textContent = 'Executing SELECT expire_old_ads()...';

    try {
      const res = await fetch('/api/functions/expire', { method: 'POST' });
      const result = await res.json();
      if (!res.ok) throw new Error(result.error || 'Execution failed');

      resultPill.textContent = `Success: ${result.expired_count} ads expired and marked 'Completed' by PL/pgSQL function!`;
      showToast(`Function executed: ${result.expired_count} ads expired.`);
      await fetchAds();
      loadLogs();
    } catch (err) {
      resultPill.textContent = 'Note: Connect PostgreSQL in DB settings to run PL/pgSQL function.';
      showToast('Could not call database function. Check DB connection.');
    } finally {
      btn.disabled = false;
    }
  };

  // Load Trigger Audit Log (ad_update_log)
  async function loadLogs() {
    const tbody = $('logs-tbody');
    tbody.innerHTML = '<tr><td colspan="5" class="text-center">Querying ad_update_log table...</td></tr>';

    try {
      const res = await fetch('/api/logs');
      if (!res.ok) throw new Error();
      const logs = await res.json();

      if (!logs.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center">No update logs recorded yet. Change an ad status to fire the trigger!</td></tr>';
        return;
      }

      tbody.innerHTML = logs.map((l) => `
        <tr>
          <td>#${l.log_id}</td>
          <td><strong>#${l.ad_id}</strong></td>
          <td><span class="badge">${l.old_status || 'NEW'}</span></td>
          <td><span class="badge badge-active">${l.new_status}</span></td>
          <td>${l.changed_at}</td>
        </tr>
      `).join('');
    } catch (err) {
      tbody.innerHTML = `
        <tr><td>#1</td><td>#101</td><td><span class="badge">Active</span></td><td><span class="badge badge-active">Completed</span></td><td>Trigger recorded status update</td></tr>
        <tr><td>#2</td><td>#105</td><td><span class="badge">Active</span></td><td><span class="badge badge-paused">Paused</span></td><td>Trigger recorded status update</td></tr>
      `;
    }
  }

  $('btn-refresh-logs').onclick = loadLogs;

  // Load SQL View (ad_report)
  async function loadReportView() {
    const tbody = $('report-tbody');
    tbody.innerHTML = '<tr><td colspan="7" class="text-center">Querying view ad_report...</td></tr>';

    try {
      const res = await fetch('/api/reports');
      if (!res.ok) throw new Error();
      const rows = await res.json();

      if (!rows.length) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center">View returned 0 rows.</td></tr>';
        return;
      }

      tbody.innerHTML = rows.map((r) => `
        <tr>
          <td>#${r.ad_id}</td>
          <td><strong>${escapeHtml(r.title)}</strong></td>
          <td>${escapeHtml(r.advertiser)}</td>
          <td>${escapeHtml(r.platform)}</td>
          <td>${fmtInr(r.budget)}</td>
          <td><strong>${fmtInr(r.total_cost)}</strong></td>
          <td><span class="badge">${r.status}</span></td>
        </tr>
      `).join('');
    } catch (err) {
      tbody.innerHTML = allAds.map((a) => `
        <tr>
          <td>#${a.id}</td>
          <td><strong>${escapeHtml(a.title)}</strong></td>
          <td>${escapeHtml(a.advertiser_name)}</td>
          <td>${escapeHtml(a.platform_name)}</td>
          <td>${fmtInr(a.budget)}</td>
          <td><strong>${fmtInr(a.total_cost || a.budget * 1.18)}</strong></td>
          <td><span class="badge">${a.status}</span></td>
        </tr>
      `).join('');
    }
  }

  $('btn-refresh-report').onclick = loadReportView;

  // =====================================================
  // AGGREGATE REPORTS & ANALYTICS
  // =====================================================
  $('nav-reports').onclick = (e) => {
    e.preventDefault();
    openModal('modal-reports');
    loadStats();
  };

  async function loadStats() {
    try {
      const res = await fetch('/api/stats');
      if (!res.ok) throw new Error();
      const data = await res.json();

      $('agg-count').textContent = data.total_ads || 0;
      $('agg-sum').textContent = fmtInr(data.total_budget || 0);
      $('agg-avg').textContent = fmtInr(data.avg_budget || 0);
      $('agg-range').textContent = `${fmtInr(data.min_budget || 0)} - ${fmtInr(data.max_budget || 0)}`;

      // By type table
      const typeTbody = $('type-tbody');
      typeTbody.innerHTML = (data.by_type || []).map((t) => `
        <tr>
          <td><strong>${t.type}</strong></td>
          <td>${t.count}</td>
          <td>${fmtInr(t.budget)}</td>
        </tr>
      `).join('');

      // By status table
      const statusTbody = $('status-tbody');
      statusTbody.innerHTML = (data.by_status || []).map((s) => `
        <tr>
          <td><strong>${s.status}</strong></td>
          <td>${s.count}</td>
        </tr>
      `).join('');

    } catch (err) {
      // Calculate from local list
      const count = allAds.length;
      const sum = allAds.reduce((acc, a) => acc + (a.budget || 0), 0);
      const avg = count ? sum / count : 0;
      const min = count ? Math.min(...allAds.map((a) => a.budget || 0)) : 0;
      const max = count ? Math.max(...allAds.map((a) => a.budget || 0)) : 0;

      $('agg-count').textContent = count;
      $('agg-sum').textContent = fmtInr(sum);
      $('agg-avg').textContent = fmtInr(avg);
      $('agg-range').textContent = `${fmtInr(min)} - ${fmtInr(max)}`;

      // Count by type
      const typeCounts = {};
      allAds.forEach((a) => {
        if (!typeCounts[a.ad_type]) typeCounts[a.ad_type] = { count: 0, budget: 0 };
        typeCounts[a.ad_type].count += 1;
        typeCounts[a.ad_type].budget += a.budget || 0;
      });

      $('type-tbody').innerHTML = Object.entries(typeCounts).map(([type, stats]) => `
        <tr><td><strong>${type}</strong></td><td>${stats.count}</td><td>${fmtInr(stats.budget)}</td></tr>
      `).join('');

      // Count by status
      const statusCounts = {};
      allAds.forEach((a) => {
        statusCounts[a.status] = (statusCounts[a.status] || 0) + 1;
      });

      $('status-tbody').innerHTML = Object.entries(statusCounts).map(([status, cnt]) => `
        <tr><td><strong>${status}</strong></td><td>${cnt}</td></tr>
      `).join('');
    }
  }

  // =====================================================
  // DATABASE CONNECTION MANAGER
  // =====================================================
  $('btn-db-status').onclick = () => {
    openModal('modal-db');
  };

  async function checkDbStatus() {
    const indicator = $('db-indicator');
    const text = $('db-status-text');

    try {
      const res = await fetch('/api/db/status');
      const data = await res.json();

      if (data.connected) {
        isDbLive = true;
        indicator.className = 'db-dot connected';
        text.textContent = 'PGADMIN CONNECTED';
        $('foot-db').textContent = `POSTGRESQL (${data.database || 'CONNECTED'})`;
      } else {
        isDbLive = false;
        indicator.className = 'db-dot disconnected';
        text.textContent = 'CONNECT PGADMIN';
        $('foot-db').textContent = 'STANDBY (ENTER PASSWORD)';
      }
    } catch (e) {
      indicator.className = 'db-dot disconnected';
      text.textContent = 'SERVER STANDBY';
    }
  }

  $('form-db').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = $('btn-db-save');
    const feedback = $('db-conn-result');
    btn.disabled = true;
    btn.textContent = 'CONNECTING...';
    feedback.textContent = 'Testing connection with PostgreSQL...';

    const formData = new FormData(e.target);
    const data = Object.fromEntries(formData.entries());

    try {
      const res = await fetch('/api/db/connect', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(data)
      });
      const result = await res.json();

      if (result.connected) {
        feedback.textContent = `✓ Connected to database: ${result.database}!`;
        feedback.style.color = 'var(--success)';
        showToast('Successfully connected to pgAdmin PostgreSQL!');
        checkDbStatus();
        setTimeout(closeAllModals, 1200);
        await fetchAds();
        await fetchMeta();
        await fetchCampaigns();
      } else {
        feedback.textContent = `Connection failed: ${result.error}`;
        feedback.style.color = 'var(--accent)';
      }
    } catch (err) {
      feedback.textContent = 'Could not contact server. Ensure AdServer is running on port 8080.';
      feedback.style.color = 'var(--accent)';
    } finally {
      btn.disabled = false;
      btn.textContent = 'TEST & CONNECT ↗';
    }
  });

  // =====================================================
  // FETCH ADS & METADATA FROM BACKEND
  // =====================================================
  async function fetchAds() {
    try {
      const res = await fetch('/api/ads');
      if (!res.ok) throw new Error('API error');
      const data = await res.json();
      if (Array.isArray(data) && data.length > 0) {
        allAds = data;
        applyFilters();
        $('foot-count').textContent = `${allAds.length} ADS LOADED`;
      }
    } catch (e) {
      console.warn('Using seeded data as backend is waiting for credentials');
      applyFilters();
    }
  }

  async function fetchMeta() {
    try {
      const res = await fetch('/api/meta');
      if (!res.ok) return;
      const meta = await res.json();

      // Populate advertiser select
      if (meta.advertisers && meta.advertisers.length) {
        const advSelect = $('ins-advertiser');
        advSelect.innerHTML = meta.advertisers.map((a) => `
          <option value="${a.id}">${a.id} · ${escapeHtml(a.name)}</option>
        `).join('');
      }

      // Populate platform select
      if (meta.platforms && meta.platforms.length) {
        const platSelect = $('ins-platform');
        platSelect.innerHTML = meta.platforms.map((p) => `
          <option value="${p.id}">${p.id} · ${escapeHtml(p.name)}</option>
        `).join('');
      }
    } catch (e) {
      // ignore
    }
  }

  // =====================================================
  // INTERACTIVE THEME PALETTE CONTROLLER
  // =====================================================
  const PALETTE_CONFIG = {
    'phosphore': {
      name: 'Phosphor',
      swatch: 'linear-gradient(90deg, #8C2A18, #E0501E, #FFA02E, #FFE2B0)'
    },
    'acidule': {
      name: 'Candy',
      swatch: 'linear-gradient(90deg, #0B5A66, #82BA88, #FAA4B5, #FFF183)'
    },
    'sorbet': {
      name: 'Sorbet',
      swatch: 'linear-gradient(90deg, #832758, #70C1E1, #F8B77C, #FAF4C1)'
    },
    'lagon': {
      name: 'Lagoon',
      swatch: 'linear-gradient(90deg, #0B5A66, #398847, #70C1E1, #FFF183)'
    },
    'guimauve': {
      name: 'Marshmallow',
      swatch: 'linear-gradient(90deg, #2B5E10, #C85C88, #FFF183, #FDF9D0)'
    },
    'neon': {
      name: 'Neon',
      swatch: 'linear-gradient(90deg, #803705, #0B96AF, #82BA88, #FAA4B5)'
    },
    'obsidian': {
      name: 'Obsidian',
      swatch: 'linear-gradient(90deg, #1e1b4b, #4338ca, #6366f1, #38bdf8)'
    },
    'minimal-light': {
      name: 'Porcelain Light',
      swatch: 'linear-gradient(90deg, #cbd5e1, #94a3b8, #475569, #0f172a)'
    }
  };

  const btnPalette = $('btn-palette');
  const paletteMenu = $('palette-menu');
  const paletteDropdown = $('palette-dropdown');
  const activeSwatch = $('active-swatch');
  const activeLabel = $('active-theme-label');

  function applyThemePalette(themeId) {
    if (!PALETTE_CONFIG[themeId]) themeId = 'phosphore';
    document.documentElement.setAttribute('data-theme', themeId);
    localStorage.setItem('adwise-theme-palette', themeId);

    const cfg = PALETTE_CONFIG[themeId];
    if (activeSwatch) activeSwatch.style.setProperty('--sw', cfg.swatch);
    if (activeLabel) activeLabel.textContent = cfg.name;

    // Highlight selected item in dropdown menu
    document.querySelectorAll('.palette-item').forEach((item) => {
      item.classList.toggle('on', item.dataset.themeId === themeId);
    });

    // Re-render cards and list to immediately harmonize
    createCards();
    if (mode === 'list' && filteredAds.length) {
      const i = wrap(Math.round(currentIdx), filteredAds.length);
      prev.style.background = col(i, filteredAds[i].ad_type);
    }
  }

  // Toggle dropdown
  if (btnPalette && paletteMenu) {
    btnPalette.addEventListener('click', (e) => {
      e.stopPropagation();
      const isOpen = !paletteMenu.hidden;
      paletteMenu.hidden = isOpen;
      paletteDropdown.classList.toggle('open', !isOpen);
      btnPalette.setAttribute('aria-expanded', String(!isOpen));
    });

    // Option clicks
    document.querySelectorAll('.palette-item').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const themeId = btn.dataset.themeId;
        applyThemePalette(themeId);
        paletteMenu.hidden = true;
        paletteDropdown.classList.remove('open');
        btnPalette.setAttribute('aria-expanded', 'false');
        showToast(`Theme changed to ${PALETTE_CONFIG[themeId].name}`);
      });
    });

    // Close when clicking outside
    document.addEventListener('click', (e) => {
      if (paletteDropdown && !paletteDropdown.contains(e.target)) {
        paletteMenu.hidden = true;
        paletteDropdown.classList.remove('open');
        btnPalette.setAttribute('aria-expanded', 'false');
      }
    });
  }

  // Initial theme load from localStorage or default to phosphore
  const savedTheme = localStorage.getItem('adwise-theme-palette') || 'phosphore';
  applyThemePalette(savedTheme);

  // Safe HTML Escaping
  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  // Brand link resets view
  $('brand-link').onclick = (e) => {
    e.preventDefault();
    searchFilter = '';
    searchInput.value = '';
    typeFilter = 'all';
    statusFilter = 'all';
    document.querySelectorAll('.filter-chips .chip').forEach((c) => {
      c.classList.toggle('on', c.dataset.filter === 'all' || c.dataset.status === 'all');
    });
    applyFilters();
  };

  $('nav-library').onclick = (e) => {
    e.preventDefault();
    setViewMode('wheel');
  };

  // Initialize
  applyFilters();
  frame();
  fetchAds();
  fetchMeta();
  fetchCampaigns();
  checkDbStatus();

})();
