// Turns ../docs/privacy-policy.md into public/privacy.html. No dependencies: it understands only the Markdown used there
// (headings, paragraphs, bullet lists, one table, bold-free inline code and links).
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const source = join(here, "..", "..", "docs", "privacy-policy.md");
const target = join(here, "..", "public", "privacy.html");

const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const inline = (s) =>
  esc(s)
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/_([^_]+)_/g, "<em>$1</em>")
    .replace(/\[([^\]]+)\]\((https?:[^)]+)\)/g, '<a href="$2">$1</a>');

export function render(md) {
  const lines = md.replace(/\r\n/g, "\n").split("\n");
  const out = [];
  let i = 0;
  let title = "CareCircle privacy policy";
  while (i < lines.length) {
    const line = lines[i];
    if (!line.trim()) { i++; continue; }
    const h = /^(#{1,3})\s+(.*)$/.exec(line);
    if (h) {
      if (h[1].length === 1) title = h[2];
      out.push(`<h${h[1].length}>${inline(h[2])}</h${h[1].length}>`);
      i++;
    } else if (line.startsWith("|")) {
      const rows = [];
      while (i < lines.length && lines[i].startsWith("|")) { rows.push(lines[i]); i++; }
      const cells = (r) => r.replace(/^\||\|$/g, "").split("|").map((c) => c.trim());
      const [head, , ...body] = rows;
      out.push("<table><thead><tr>" + cells(head).map((c) => `<th>${inline(c)}</th>`).join("") + "</tr></thead><tbody>");
      for (const r of body) out.push("<tr>" + cells(r).map((c) => `<td>${inline(c)}</td>`).join("") + "</tr>");
      out.push("</tbody></table>");
    } else if (/^[-*]\s+/.test(line)) {
      out.push("<ul>");
      while (i < lines.length && /^[-*]\s+/.test(lines[i])) { out.push(`<li>${inline(lines[i].replace(/^[-*]\s+/, ""))}</li>`); i++; }
      out.push("</ul>");
    } else {
      const para = [];
      while (i < lines.length && lines[i].trim() && !/^(#|\||[-*]\s)/.test(lines[i])) { para.push(lines[i].trim()); i++; }
      out.push(`<p>${inline(para.join(" "))}</p>`);
    }
  }
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)}</title>
<style>body{font:16px/1.55 system-ui,sans-serif;max-width:46rem;margin:0 auto;padding:1rem;color:#111}table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccc;padding:.4rem;text-align:left;vertical-align:top}code{background:#f1f1f3;padding:0 .2rem}@media(prefers-color-scheme:dark){body{background:#111;color:#eee}th,td{border-color:#444}code{background:#222}}</style>
</head><body>
${out.join("\n")}
</body></html>
`;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, render(readFileSync(source, "utf8")));
  console.log(`wrote ${target}`);
}
