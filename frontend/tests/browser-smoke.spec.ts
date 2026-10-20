import { test, expect } from "@playwright/test";
import fs from "node:fs";
const env = Object.fromEntries(
  fs
    .readFileSync("../.env", "utf8")
    .split("\n")
    .filter((l) => l.includes("="))
    .map((l) => l.split("=")),
);
test("real login, dashboard, event explorer, schema lifecycle and credentials", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("demo-admin@localhost");
  await page.getByLabel("Password", { exact: true }).fill(env.DEMO_PASSWORD);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("button", { name: /Application overview/ }).click();
  await expect(page.locator(".stat>span").first()).not.toHaveText("0");
  await expect(page.getByText("Recent events", { exact: true })).toBeVisible();
  await expect(page.locator(".recharts-bar-rectangle").first()).toBeVisible();
  await page.mouse.move(10, 10);
  await page.screenshot({ path: "test-results/dashboard.png", fullPage: true });
  await page
    .getByRole("button", { name: "Edit dashboard", exact: true })
    .click();
  await page.getByLabel("Dashboard title").fill("Application overview");
  const handle = page.locator(".drag-handle").first();
  const box = await handle.boundingBox();
  if (box) {
    await page.mouse.move(box.x + 5, box.y + 5);
    await page.mouse.down();
    await page.mouse.move(box.x + 70, box.y + 230, { steps: 12 });
    await page.mouse.up();
  }
  const resize = page.locator(".react-resizable-handle").first();
  const resizeBox = await resize.boundingBox();
  if (resizeBox) {
    await page.mouse.move(resizeBox.x + 5, resizeBox.y + 5);
    await page.mouse.down();
    await page.mouse.move(resizeBox.x + 100, resizeBox.y + 100, { steps: 12 });
    await page.mouse.up();
  }
  await page
    .getByRole("button", { name: "Save dashboard", exact: true })
    .click();
  await expect(page.getByLabel("Dashboard title")).toHaveCount(0);
  await page.getByRole("button", { name: "Explore", exact: true }).click();
  await expect(page.locator(".event-row").first()).toBeVisible();
  await page.locator(".event-row").first().click();
  await expect(page.locator("pre")).toContainText("eventId");
  await page.getByLabel("event Type", { exact: true }).fill("ORDER_CREATED");
  await page.getByRole("button", { name: "Run query", exact: true }).click();
  await expect(page.locator(".event-row").first()).toContainText(
    "ORDER_CREATED",
  );
  await page.getByRole("button", { name: "Schemas", exact: true }).click();
  await page.getByRole("button", { name: "New schema", exact: true }).click();
  const type = "BROWSER_" + Date.now();
  await page.getByLabel("Event type", { exact: true }).fill(type);
  await page.getByRole("button", { name: "Create draft", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Save draft", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Example payload").fill('{"name":42}');
  await page.getByRole("button", { name: "Validate saved schema" }).click();
  await expect(page.locator(".schema-editor pre")).toContainText("string");
  await page.getByRole("button", { name: "Publish immutable version" }).click();
  await expect(page.getByText("Published version · read only")).toBeVisible();
  await page.getByRole("button", { name: "Applications", exact: true }).click();
  await page.getByRole("button", { name: /Order Service/ }).click();
  await expect(
    page.getByText("Ingestion outcomes", { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("alert")).toContainText("Last ingestion issue");
  await page.getByRole("button", { name: "Create key", exact: true }).click();
  await expect(page.locator(".key-reveal code")).not.toBeEmpty();
  await page.getByRole("button", { name: "Dismiss", exact: true }).click();
  const activeRow = page
    .locator("tr")
    .filter({ has: page.getByRole("button", { name: "Revoke", exact: true }) })
    .last();
  await activeRow.getByRole("button", { name: "Revoke", exact: true }).click();
  await expect(page.locator(".key-reveal")).toHaveCount(0);
  expect(errors).toEqual([]);
});
