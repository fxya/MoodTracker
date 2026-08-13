import { test, expect } from '@playwright/test';
import { registerAndLogin, addMood } from './helpers.js';

// Most of these tests share one logged-in session/user (serial, not parallel,
// within this file) purely to conserve the app's own POST /login rate limit
// (20 attempts/5min per IP - see AuthRateLimitFilter) - a full e2e suite
// registering+logging in fresh for every single test adds up fast. Sharing is
// safe here because none of these assertions depend on the account's total
// mood count, only on locating a specific mood card by its own text.
test.describe.configure({ mode: 'serial' });

let sharedPage;

test.beforeAll(async ({ browser }) => {
    sharedPage = await browser.newPage();
    await registerAndLogin(sharedPage);
});

test.afterAll(async () => {
    await sharedPage.close();
});

test('rating tiers map to low/mid/high badge classes', async () => {
    await addMood(sharedPage, { text: 'Low tier mood', rating: 2 });
    await addMood(sharedPage, { text: 'Mid tier mood', rating: 5, notes: 'Right in the middle' });
    await addMood(sharedPage, { text: 'High tier mood', rating: 9 });

    const lowCard = sharedPage.locator('.mood-card', { hasText: 'Low tier mood' });
    const midCard = sharedPage.locator('.mood-card', { hasText: 'Mid tier mood' });
    const highCard = sharedPage.locator('.mood-card', { hasText: 'High tier mood' });

    await expect(lowCard.locator('.rating-badge')).toHaveClass(/rating-low/);
    await expect(midCard.locator('.rating-badge')).toHaveClass(/rating-mid/);
    await expect(midCard.locator('.mood-notes')).toContainText('Right in the middle');
    await expect(highCard.locator('.rating-badge')).toHaveClass(/rating-high/);
});

test('editing a mood persists the change', async () => {
    await addMood(sharedPage, { text: 'Original text', rating: 5 });

    await sharedPage
        .locator('.mood-card', { hasText: 'Original text' })
        .getByRole('link', { name: 'Edit', exact: true })
        .click();
    await sharedPage.waitForURL('**/moodtracker/*/edit');

    await sharedPage.fill('#mood-text', 'Edited text');
    await sharedPage.fill('#mood-rating', '9');
    await sharedPage.click('button:has-text("Save Changes")');

    await sharedPage.waitForURL('**/moodtracker');
    await expect(sharedPage.locator('.alert-success')).toContainText('Mood updated');
    const card = sharedPage.locator('.mood-card', { hasText: 'Edited text' });
    await expect(card).toBeVisible();
    await expect(card.locator('.rating-badge')).toHaveClass(/rating-high/);
    await expect(sharedPage.locator('.mood-card', { hasText: 'Original text' })).toHaveCount(0);
});

test('deleting a mood removes it from the list', async () => {
    await addMood(sharedPage, { text: 'To be deleted', rating: 4 });

    sharedPage.once('dialog', (dialog) => dialog.accept());
    await sharedPage
        .locator('.mood-card', { hasText: 'To be deleted' })
        .getByRole('button', { name: 'Delete', exact: true })
        .click();

    await sharedPage.waitForURL('**/moodtracker');
    await expect(sharedPage.locator('.alert-success')).toContainText('Mood deleted');
    await expect(sharedPage.locator('.mood-card', { hasText: 'To be deleted' })).toHaveCount(0);
});

// Needs its own fresh user - the assertion depends on there being zero moods
// logged yet, which the shared session above no longer satisfies.
test('weekly summary stat card appears after logging a mood', async ({ page }) => {
    await registerAndLogin(page);

    await expect(page.locator('.stat-card-empty')).toBeVisible();

    await addMood(page, { text: 'First mood this week', rating: 6 });

    await expect(page.locator('.stat-card').first()).toBeVisible();
    await expect(page.locator('.stat-card-empty')).toHaveCount(0);
});
