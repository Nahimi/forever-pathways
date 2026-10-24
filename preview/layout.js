// Ring layout for Forever Pathways. Mirrors client/RingLayout.java line for line,
// so the preview and the game place every node in the same spot.
(function (root) {
  const RADII = [0, 70, 125, 180, 235, 290, 345, 400, 455, 510, 565, 620, 675, 730, 785, 870];
  const SPACING = 22;
  const SPINE = 30;

  function layout(tree) {
    const nodes = tree.nodes;
    const byId = {};
    nodes.forEach((n) => (byId[n.id] = n));
    const children = {};
    const isSeal = (n) => n.path === "seal";
    const pathOrder = tree.paths.map((p) => p.id);
    let core = null;
    nodes.forEach((n) => {
      if (!n.requires && !isSeal(n)) core = n;
    });
    nodes.forEach((n) => {
      if (isSeal(n) || !n.requires) return;
      (children[n.requires] = children[n.requires] || []).push(n);
    });
    if (children[core.id]) {
      children[core.id].sort((a, b) => pathOrder.indexOf(a.path) - pathOrder.indexOf(b.path));
    }
    const req = {};
    function need(n) {
      if (req[n.id] !== undefined) return req[n.id];
      const own = n.ring > 0 ? SPACING / RADII[Math.min(n.ring, RADII.length - 1)] : 0;
      let sum = 0;
      (children[n.id] || []).forEach((c) => (sum += need(c)));
      req[n.id] = Math.max(own, sum);
      return req[n.id];
    }
    need(core);
    const angle = {};
    const radius = {};
    angle[core.id] = -Math.PI / 2;
    radius[core.id] = 0;
    function place(n, start, width) {
      angle[n.id] = start + width / 2;
      radius[n.id] = RADII[Math.min(n.ring, RADII.length - 1)];
      const kids = children[n.id] || [];
      let total = 0;
      kids.forEach((c) => (total += req[c.id]));
      if (total <= 0) return;
      let cursor = start;
      kids.forEach((c) => {
        const w = (width * req[c.id]) / total;
        place(c, cursor, w);
        cursor += w;
      });
    }
    const spine = SPINE / RADII[2];
    const top = children[core.id] || [];
    let total = 0;
    top.forEach((c) => (total += req[c.id]));
    let cursor = -Math.PI / 2 + spine / 2;
    const avail = Math.PI * 2 - spine;
    top.forEach((c) => {
      const w = (avail * req[c.id]) / total;
      place(c, cursor, w);
      cursor += w;
    });
    // The eye ring: spread the outermost nodes evenly, keeping their order.
    const outer = RADII.length - 1;
    const eyes = nodes.filter((n) => n.ring === outer && !isSeal(n));
    if (eyes.length > 1) {
      const norm = (a) => {
        let v = a + Math.PI / 2;
        v = ((v % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
        return v;
      };
      eyes.sort((a, b) => norm(angle[a.id]) - norm(angle[b.id]));
      const step = (Math.PI * 2) / eyes.length;
      let sx = 0;
      let sy = 0;
      eyes.forEach((e, i) => {
        const d = norm(angle[e.id]) - i * step;
        sx += Math.cos(d);
        sy += Math.sin(d);
      });
      const offset = Math.atan2(sy, sx);
      eyes.forEach((e, i) => (angle[e.id] = -Math.PI / 2 + offset + i * step));
    }
    // Seals are not placed as nodes: each is a band over its rings. Its anchor (used to centre the
    // view on it) is the inner edge of the band at twelve o'clock, where its lock badge sits.
    nodes.forEach((n) => {
      if (!isSeal(n)) return;
      angle[n.id] = -Math.PI / 2;
      radius[n.id] = band(n)[0];
    });
    const pos = {};
    nodes.forEach((n) => {
      pos[n.id] = {
        x: Math.cos(angle[n.id]) * radius[n.id],
        y: Math.sin(angle[n.id]) * radius[n.id],
        angle: angle[n.id],
        radius: radius[n.id],
      };
    });
    return { pos, radii: RADII, core: core.id };
  }

  /** Inner and outer radius of the band a seal covers: halfway to the neighbouring rings. */
  function band(seal) {
    const a = seal.seal.opensRing;
    const b = seal.seal.throughRing || a;
    const last = RADII.length - 1;
    const inner = (RADII[a - 1] + RADII[a]) / 2;
    const outer = b >= last ? RADII[last] + (RADII[last] - RADII[last - 1]) / 2 : (RADII[b] + RADII[b + 1]) / 2;
    return [inner, outer];
  }

  const api = { layout, band, RADII, SPACING };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.RingLayout = api;
})(typeof window !== "undefined" ? window : this);
