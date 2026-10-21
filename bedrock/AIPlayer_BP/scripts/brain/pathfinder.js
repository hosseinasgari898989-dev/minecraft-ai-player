/**
 * Bounded 3D A* pathfinder and StuckDetector for Bedrock Edition companion movement.
 */

const DIRS = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]];
const keyOf = (p) => `${p.x},${p.y},${p.z}`;
const dist3d = (a, b) => Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);

function canStandAt(grid, x, y, z) {
  if (grid.isHazard(x, y, z) || grid.isHazard(x, y - 1, z) || grid.isHazard(x, y + 1, z)) return false;
  if (!grid.isPassableBody(x, y, z) || !grid.isPassableBody(x, y + 1, z)) return false;
  return grid.isSolidGround(x, y - 1, z) || grid.isWater(x, y - 1, z);
}

function resolveVerticalStep(grid, from, nx, nz) {
  if (canStandAt(grid, nx, from.y, nz)) return { x: nx, y: from.y, z: nz };
  if (grid.isPassableBody(from.x, from.y + 2, from.z) && canStandAt(grid, nx, from.y + 1, nz)) return { x: nx, y: from.y + 1, z: nz };
  if (grid.isPassableBody(nx, from.y, nz) && grid.isPassableBody(nx, from.y + 1, nz)) {
    for (let drop = 1; drop <= 3; drop++) {
      const ty = from.y - drop;
      if (grid.isHazard(nx, ty, nz) || grid.isHazard(nx, ty - 1, nz)) return null;
      if (canStandAt(grid, nx, ty, nz)) return { x: nx, y: ty, z: nz };
      if (!grid.isPassableBody(nx, ty, nz)) break;
    }
  }
  return null;
}

export function findLocalPath(grid, start, goal, acceptRadius = 1.5, maxNodes = 512) {
  const open = [{ pos: start, g: 0, f: dist3d(start, goal) }];
  const cameFrom = new Map();
  const gScore = new Map([[keyOf(start), 0]]);
  const closed = new Set();
  let bestClosest = start;
  let bestDist = dist3d(start, goal);
  let expanded = 0;

  while (open.length > 0 && expanded < maxNodes) {
    open.sort((a, b) => a.f - b.f);
    const current = open.shift();
    const cKey = keyOf(current.pos);
    if (closed.has(cKey)) continue;
    closed.add(cKey);
    expanded++;
    const dGoal = dist3d(current.pos, goal);
    if (dGoal < bestDist) { bestDist = dGoal; bestClosest = current.pos; }
    if (dGoal <= acceptRadius) return { reachable: true, partial: false, waypoints: reconstruct(cameFrom, current.pos), nodesExpanded: expanded };

    for (const [dx, dz] of DIRS) {
      const nx = current.pos.x + dx, nz = current.pos.z + dz;
      const diagonal = dx !== 0 && dz !== 0;
      if (diagonal && (!grid.isPassableBody(current.pos.x + dx, current.pos.y, current.pos.z) || !grid.isPassableBody(current.pos.x, current.pos.y, current.pos.z + dz))) continue;
      const next = resolveVerticalStep(grid, current.pos, nx, nz);
      if (!next) continue;
      const nKey = keyOf(next);
      if (closed.has(nKey)) continue;
      let cost = diagonal ? 1.4142 : 1.0;
      if (next.y > current.pos.y) cost += 0.65;
      if (grid.isWater(next.x, next.y, next.z)) cost += 2.0;
      const tentativeG = current.g + cost;
      if (tentativeG < (gScore.get(nKey) ?? Infinity)) {
        cameFrom.set(nKey, current.pos);
        gScore.set(nKey, tentativeG);
        open.push({ pos: next, g: tentativeG, f: tentativeG + dist3d(next, goal) });
      }
    }
  }
  return { reachable: false, partial: keyOf(bestClosest) !== keyOf(start), waypoints: keyOf(bestClosest) !== keyOf(start) ? reconstruct(cameFrom, bestClosest) : [], nodesExpanded: expanded };
}

function reconstruct(cameFrom, end) {
  const path = [];
  let cur = end;
  while (cur) { path.push(cur); cur = cameFrom.get(keyOf(cur)); }
  return path.reverse();
}
