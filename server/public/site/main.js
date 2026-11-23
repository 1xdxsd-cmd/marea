(() => {
  document.documentElement.classList.remove('no-js');
  const cfg = Object.assign(
    { apkUrl: '/downloads/marea.apk', version: '', contactUrl: '', owner: 'Anthony' },
    window.MAREA_SITE || {}
  );
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // ---------- Configuración ----------
  $$('.js-download').forEach((a) => a.setAttribute('href', cfg.apkUrl));
  if (cfg.version) $$('.js-version').forEach((el) => (el.textContent = `Versión ${cfg.version}`));
  $$('.js-owner').forEach((el) => (el.textContent = cfg.owner));
  $$('.js-year').forEach((el) => (el.textContent = new Date().getFullYear()));
  if (cfg.contactUrl) {
    $$('.js-contact').forEach((a) => {
      a.href = cfg.contactUrl;
      a.hidden = false;
    });
  }

  // ---------- Descarga: avisa si el APK todavía no está en el servidor ----------
  const toast = $('#toast');
  let toastTimer;
  const showToast = (msg) => {
    toast.textContent = msg;
    toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('show'), 3800);
  };

  let apkAvailable = null; // null = no se sabe (otro dominio o sin red)
  const sameOrigin = new URL(cfg.apkUrl, location.href).origin === location.origin;
  if (sameOrigin && location.protocol.startsWith('http')) {
    fetch(cfg.apkUrl, { method: 'HEAD', cache: 'no-store' })
      .then((r) => {
        apkAvailable = r.ok;
        const len = Number(r.headers.get('content-length'));
        if (r.ok && len > 0) {
          const mb = (len / 1048576).toFixed(len > 10485760 ? 0 : 1);
          $$('.js-size').forEach((el) => (el.textContent = `Gratis · ${mb} MB`));
        }
      })
      .catch(() => {});
  }
  $$('.js-download').forEach((a) =>
    a.addEventListener('click', (e) => {
      if (apkAvailable === false) {
        e.preventDefault();
        showToast('El APK estará disponible muy pronto. ¡Vuelve en un rato!');
        return;
      }
      showToast('Descargando Marea… Abre el archivo para instalarla.');
    })
  );

  // ---------- Entrada del hero ----------
  const words = $$('.hero-title .word');
  words.forEach((w, i) => (w.style.transitionDelay = `${0.1 + i * 0.09}s`));
  requestAnimationFrame(() => requestAnimationFrame(() => document.documentElement.classList.add('loaded')));

  // ---------- Aparición al hacer scroll ----------
  const io = new IntersectionObserver(
    (entries) => {
      entries.forEach((en) => {
        if (en.isIntersecting) {
          en.target.classList.add('in');
          io.unobserve(en.target);
        }
      });
    },
    { threshold: 0.15, rootMargin: '0px 0px -40px 0px' }
  );
  $$('.reveal, .steps').forEach((el) => io.observe(el));

  // ---------- Navegación ----------
  const nav = $('#nav');
  const onScroll = () => nav.classList.toggle('scrolled', window.scrollY > 24);
  onScroll();
  window.addEventListener('scroll', onScroll, { passive: true });

  const links = $$('.nav-links a');
  const spy = new IntersectionObserver(
    (entries) => {
      entries.forEach((en) => {
        if (!en.isIntersecting) return;
        links.forEach((l) => l.classList.toggle('active', l.getAttribute('href') === `#${en.target.id}`));
      });
    },
    { rootMargin: '-45% 0px -50% 0px' }
  );
  $$('section[id]').forEach((s) => spy.observe(s));

  // ---------- Letra que avanza (teléfono y tarjeta) ----------
  const lyricBox = $('.lyrics');
  if (lyricBox) {
    const inner = document.createElement('div');
    inner.className = 'lyrics-inner';
    $$('p', lyricBox).forEach((p) => inner.appendChild(p));
    lyricBox.appendChild(inner);
    const lines = $$('p', inner);
    let i = 0;
    const step = () => {
      lines.forEach((p, k) => p.classList.toggle('on', k === i));
      const lineH = lines[0].offsetHeight || 24;
      inner.style.transform = `translateY(${lineH - i * lineH}px)`;
      i = (i + 1) % lines.length;
    };
    step();
    if (!reduced) setInterval(step, 2200);
  }
  const art = $$('.art-lyrics p');
  if (art.length && !reduced) {
    let j = 1;
    setInterval(() => {
      j = (j + 1) % art.length;
      art.forEach((p, k) => p.classList.toggle('on', k === j));
    }, 2600);
  }

  if (reduced) return;
  const fine = window.matchMedia('(pointer: fine)').matches;

  // ---------- Teléfono que se inclina con el cursor ----------
  const phone = $('#phone');
  const visual = $('.hero-visual');
  if (phone && visual && fine) {
    visual.addEventListener('pointermove', (e) => {
      const r = visual.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width - 0.5;
      const y = (e.clientY - r.top) / r.height - 0.5;
      phone.style.setProperty('--ry', `${x * 18}deg`);
      phone.style.setProperty('--rx', `${-y * 14}deg`);
    });
    visual.addEventListener('pointerleave', () => {
      phone.style.setProperty('--ry', '0deg');
      phone.style.setProperty('--rx', '0deg');
    });
  }

  // ---------- Brillo que sigue al cursor en tarjetas ----------
  if (fine) {
    $$('.glow').forEach((card) =>
      card.addEventListener('pointermove', (e) => {
        const r = card.getBoundingClientRect();
        card.style.setProperty('--x', `${e.clientX - r.left}px`);
        card.style.setProperty('--y', `${e.clientY - r.top}px`);
      })
    );

    // Botones "magnéticos"
    $$('.magnetic').forEach((btn) => {
      btn.addEventListener('pointermove', (e) => {
        const r = btn.getBoundingClientRect();
        btn.style.setProperty('--mx', `${(e.clientX - r.left - r.width / 2) * 0.18}px`);
        btn.style.setProperty('--my', `${(e.clientY - r.top - r.height / 2) * 0.3}px`);
      });
      btn.addEventListener('pointerleave', () => {
        btn.style.setProperty('--mx', '0px');
        btn.style.setProperty('--my', '0px');
      });
    });
  }

  // ---------- Parallax suave del fondo ----------
  const blobs = $$('.blob');
  let ticking = false;
  window.addEventListener(
    'scroll',
    () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => {
        const y = window.scrollY;
        blobs.forEach((b, k) => (b.style.translate = `0 ${y * (0.06 + k * 0.04)}px`));
        ticking = false;
      });
    },
    { passive: true }
  );
})();
