import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const buildDir = path.join(root, "build");
const distDir = path.join(buildDir, "distributions");
const bpDir = path.join(root, "bedrock", "AIPlayer_BP");
const rpDir = path.join(root, "bedrock", "AIPlayer_RP");

function fail(message) {
  console.error(`[AI Player package] ERROR: ${message}`);
  process.exit(1);
}
function run(command, args, options = {}) {
  try {
    execFileSync(command, args, { stdio: "inherit", ...options });
  } catch (error) {
    fail(`Command failed: ${command} ${args.join(" ")} (exit ${error.status ?? "unknown"})`);
  }
}
function readJson(file) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (error) {
    fail(`Invalid or unreadable JSON ${path.relative(root, file)}: ${error.message}`);
  }
}
function walk(dir) {
  if (!fs.existsSync(dir)) fail(`Required directory missing: ${path.relative(root, dir)}`);
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) files.push(...walk(full));
    else if (entry.isFile() && entry.name.endsWith(".json")) files.push(full);
  }
  return files;
}
function assertPack(packDir, kind) {
  const manifest = readJson(path.join(packDir, "manifest.json"));
  if (manifest.format_version !== 2 || !manifest.header?.uuid || !Array.isArray(manifest.modules)) {
    fail(`Invalid ${kind} manifest structure.`);
  }
  for (const file of walk(packDir)) readJson(file);
  return manifest;
}

fs.mkdirSync(distDir, { recursive: true });
const bp = assertPack(bpDir, "Behavior Pack");
const rp = assertPack(rpDir, "Resource Pack");
const bpDependsOnRp = bp.dependencies?.some((dep) => dep.uuid === rp.header.uuid);
const rpDependsOnBp = rp.dependencies?.some((dep) => dep.uuid === bp.header.uuid);
if (!bpDependsOnRp || !rpDependsOnBp) fail("Behavior/Resource Pack manifest UUID dependencies do not match.");
if (!fs.existsSync(path.join(bpDir, "scripts", "main.js"))) fail("Bedrock script entrypoint is missing.");
if (!fs.existsSync(path.join(rpDir, "textures", "entity", "ai_player.png"))) fail("Bedrock entity texture is missing.");
if (!fs.existsSync(path.join(rpDir, "entity", "ai_player.entity.json"))) fail("Bedrock client entity definition is missing.");

const gradleProperties = fs.readFileSync(path.join(root, "gradle.properties"), "utf8");
const version = /^mod_version=(.+)$/m.exec(gradleProperties)?.[1]?.trim();
if (!version) fail("Could not read mod_version from gradle.properties.");
const jarPath = path.join(buildDir, "libs", `ai-player-${version}.jar`);
if (!fs.existsSync(jarPath) || fs.statSync(jarPath).size < 1000) fail(`Built Fabric JAR not found or unexpectedly small: ${path.relative(root, jarPath)}`);

const bpPack = path.join(distDir, "AIPlayer_BP.mcpack");
const rpPack = path.join(distDir, "AIPlayer_RP.mcpack");
const addon = path.join(distDir, "AIPlayer-Bedrock-0.2.0.mcaddon");
const sourceZip = path.join(distDir, "minecraft-ai-player-dual-edition-source.zip");

for (const output of [bpPack, rpPack, addon, sourceZip]) {
  try { fs.rmSync(output, { force: true }); } catch {}
}
run("zip", ["-qr", bpPack, "."], { cwd: bpDir });
run("zip", ["-qr", rpPack, "."], { cwd: rpDir });
run("zip", ["-q", addon, "AIPlayer_BP.mcpack", "AIPlayer_RP.mcpack"], { cwd: distDir });
run("zip", ["-qr", sourceZip, ".", "-x", ".git/*", ".git/**", ".gradle/*", ".gradle/**", "build/*", "build/**", "dist/*", "dist/**", "latest/ai-player.jar"], { cwd: root });
run("unzip", ["-tq", bpPack]);
run("unzip", ["-tq", rpPack]);
run("unzip", ["-tq", addon]);
run("unzip", ["-tq", sourceZip]);

for (const file of [jarPath, bpPack, rpPack, addon, sourceZip]) {
  console.log(`[AI Player package] ${path.relative(root, file)} — ${fs.statSync(file).size.toLocaleString("en-US")} bytes`);
}
