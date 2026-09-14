import { expect, test } from "@playwright/test";

const roomId = "11111111-1111-1111-1111-111111111111";

test.beforeEach(async ({ page }) => {
  await page.addInitScript(session => {
    localStorage.setItem("collabmind.session", JSON.stringify(session));
  }, {
    accessToken: "browser-test-token",
    userId: "22222222-2222-2222-2222-222222222222",
    displayName: "Maya",
    email: "maya@example.com"
  });
  await page.route(`http://localhost:8081/api/conversations/${roomId}`, async route => {
    if (route.request().method() === "PATCH") {
      const body = route.request().postDataJSON();
      await route.fulfill({ json: { id: roomId, name: body.name } });
      return;
    }
    await route.continue();
  });
  await page.goto(`/r/${roomId}`, { waitUntil: "domcontentloaded" });
});

test("menus, participant drawer, and emoji picker toggle cleanly", async ({ page }) => {
  const conversationMenu = page.getByRole("button", { name: "Conversation options" });
  await conversationMenu.click();
  await expect(page.getByRole("button", { name: "Copy invite link" })).toBeVisible();
  await conversationMenu.click();
  await expect(page.getByRole("button", { name: "Copy invite link" })).toBeHidden();

  await page.getByRole("button", { name: /Participants/ }).click();
  await expect(page.getByRole("complementary", { name: "Room participants" })).toBeVisible();
  await expect(page.getByText("Maya (you)")).toBeVisible();
  await page.getByRole("complementary", { name: "Room participants" })
    .getByRole("button", { name: "Close participants" }).click();

  await page.getByRole("button", { name: "Choose emoji" }).click();
  await expect(page.getByRole("dialog", { name: "Emoji picker" })).toBeVisible();
  await page.getByRole("button", { name: "Insert 😀" }).click();
  await expect(page.getByPlaceholder("Enter the room to start chatting")).toHaveValue("😀");
  await expect(page.getByRole("dialog", { name: "Emoji picker" })).toBeHidden();
});

test("room title editing and AI mention selection work", async ({ page }) => {
  await page.getByTitle("Edit room title").click();
  const title = page.getByLabel("Room title");
  await title.fill("Launch planning");
  await title.press("Enter");
  await expect(page.getByTitle("Edit room title")).toContainText("Launch planning");

  const composer = page.getByPlaceholder("Enter the room to start chatting");
  await composer.fill("@");
  await expect(page.getByRole("button", { name: /@ai Ask the AI assistant/ })).toBeVisible();
  await page.getByRole("button", { name: /@kapruka Search live Kapruka products/ }).click();
  await expect(composer).toHaveValue("@kapruka ");
});
