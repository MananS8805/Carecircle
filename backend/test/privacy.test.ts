import { describe, expect, it } from "vitest";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
// @ts-expect-error plain .mjs script without types
import { render } from "../scripts/build-privacy.mjs";

const root = join(__dirname, "..", "..");
const policy = readFileSync(join(root, "docs", "privacy-policy.md"), "utf8");

describe("privacy page", () => {
  it("renders headings, the table and escapes HTML", () => {
    const html = render("# Title\n\nSome `code` <b>\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n- one\n- two\n");
    expect(html).toContain("<h1>Title</h1>");
    expect(html).toContain("<code>code</code>");
    expect(html).toContain("&lt;b&gt;");
    expect(html).toContain("<th>a</th>");
    expect(html).toContain("<li>two</li>");
  });

  it("renders the real policy and keeps its endpoint table", () => {
    const html = render(policy);
    expect(html).toContain("<table>");
    expect(html).toContain("/api/policy-question");
  });

  it("every backend endpoint that takes user input is described in the policy", () => {
    const endpoints = readdirSync(join(__dirname, "..", "api")).map((f) => f.replace(/\.ts$/, "")).filter((n) => n !== "health");
    for (const e of endpoints) expect(policy, `${e} missing from docs/privacy-policy.md`).toContain(`/api/${e}`);
  });
});
