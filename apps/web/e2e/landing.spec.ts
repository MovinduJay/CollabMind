import { expect, test } from "@playwright/test";

test("landing page exposes room creation without horizontal clipping", async ({
  page,
}) => {
  await page.goto("/", { waitUntil: "domcontentloaded" });
  await expect(page.getByText("CollabMind", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Think better, together." }),
  ).toBeVisible();
  await expect(page.getByLabel("Your name")).toBeVisible();
  await expect(page.getByLabel("Room name")).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Create and enter room" }),
  ).toBeVisible();
  const overflow = await page.evaluate(
    () =>
      document.documentElement.scrollWidth -
      document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(1);
});

test("room entry controls remain keyboard accessible", async ({ page }) => {
  await page.goto("/", { waitUntil: "domcontentloaded" });
  await page.getByLabel("Your name").focus();
  await page.keyboard.press("Tab");
  await expect(page.getByLabel("Room name")).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(
    page.getByRole("button", { name: "Create and enter room" }),
  ).toBeFocused();
});
