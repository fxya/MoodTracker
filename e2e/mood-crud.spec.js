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

test('rating tiers map to low/mid/high badge testids', async () => {
    await addMood(sharedPage, { text: 'Low tier mood', rating: 2 });
    await addMood(sharedPage, { text: 'Mid tier mood', rating: 5, notes: 'Right in the middle' });
    await addMood(sharedPage, { text: 'High tier mood', rating: 9 });

    const lowCard = sharedPage.locator('[data-testid="mood-card"]', { hasText: 'Low tier mood' });
    const midCard = sharedPage.locator('[data-testid="mood-card"]', { hasText: 'Mid tier mood' });
    const highCard = sharedPage.locator('[data-testid="mood-card"]', { hasText: 'High tier mood' });

    await expect(lowCard.locator('[data-testid^="rating-badge-"]')).toHaveAttribute(
        'data-testid',
        'rating-badge-low',
    );
    await expect(midCard.locator('[data-testid^="rating-badge-"]')).toHaveAttribute(
        'data-testid',
        'rating-badge-mid',
    );
    await expect(midCard).toContainText('Right in the middle');
    await expect(highCard.locator('[data-testid^="rating-badge-"]')).toHaveAttribute(
        'data-testid',
        'rating-badge-high',
    );
});

test('mood tag renders as a badge and appears as the weekly summary top tag', async () => {
    await addMood(sharedPage, { text: 'Tagged mood', rating: 7, tag: 'Excited' });

    const card = sharedPage.locator('[data-testid="mood-card"]', { hasText: 'Tagged mood' });
    await expect(card.locator('[data-testid="mood-tag-badge"]')).toHaveText('Excited');
    await expect(sharedPage.locator('[data-testid="top-tag"]')).toContainText('Excited');
});

test('editing a mood persists the change', async () => {
    await addMood(sharedPage, { text: 'Original text', rating: 5 });

    await sharedPage
        .locator('[data-testid="mood-card"]', { hasText: 'Original text' })
        .getByRole('link', { name: 'Edit', exact: true })
        .click();
    await sharedPage.waitForURL('**/moodtracker/*/edit');

    await sharedPage.fill('#mood-text', 'Edited text');
    await sharedPage.fill('#mood-rating', '9');
    await sharedPage.click('button:has-text("Save Changes")');

    await sharedPage.waitForURL('**/moodtracker');
    await expect(sharedPage.locator('[data-testid="alert-success"]')).toContainText('Mood updated');
    const card = sharedPage.locator('[data-testid="mood-card"]', { hasText: 'Edited text' });
    await expect(card).toBeVisible();
    await expect(card.locator('[data-testid^="rating-badge-"]')).toHaveAttribute(
        'data-testid',
        'rating-badge-high',
    );
    await expect(
        sharedPage.locator('[data-testid="mood-card"]', { hasText: 'Original text' }),
    ).toHaveCount(0);
});

test('weather can be added via edit when the mood had none, then corrected', async () => {
    await addMood(sharedPage, { text: 'Weatherless mood', rating: 6 });

    await sharedPage
        .locator('[data-testid="mood-card"]', { hasText: 'Weatherless mood' })
        .getByRole('link', { name: 'Edit', exact: true })
        .click();
    await sharedPage.waitForURL('**/moodtracker/*/edit');

    // No location is set for this user, so this mood has no weather yet -
    // filling these in exercises the "create a Weather" path.
    await sharedPage.fill('#temperature-c', '21.5');
    await sharedPage.fill('#precipitation-mm', '0');
    await sharedPage.click('button:has-text("Save Changes")');
    await sharedPage.waitForURL('**/moodtracker');

    let card = sharedPage.locator('[data-testid="mood-card"]', { hasText: 'Weatherless mood' });
    await expect(card).toContainText('21.5°C');
    await expect(card).toContainText('0.0mm rain');

    // Editing again with only one field filled must leave the other untouched.
    await card.getByRole('link', { name: 'Edit', exact: true }).click();
    await sharedPage.waitForURL('**/moodtracker/*/edit');
    await expect(sharedPage.locator('#temperature-c')).toHaveValue('21.5');
    await sharedPage.fill('#temperature-c', '25');
    await sharedPage.fill('#precipitation-mm', '');
    await sharedPage.click('button:has-text("Save Changes")');
    await sharedPage.waitForURL('**/moodtracker');

    card = sharedPage.locator('[data-testid="mood-card"]', { hasText: 'Weatherless mood' });
    await expect(card).toContainText('25.0°C');
    await expect(card).toContainText('0.0mm rain');
});

test('deleting a mood removes it from the list', async () => {
    await addMood(sharedPage, { text: 'To be deleted', rating: 4 });

    sharedPage.once('dialog', (dialog) => dialog.accept());
    await sharedPage
        .locator('[data-testid="mood-card"]', { hasText: 'To be deleted' })
        .getByRole('button', { name: 'Delete', exact: true })
        .click();

    await sharedPage.waitForURL('**/moodtracker');
    await expect(sharedPage.locator('[data-testid="alert-success"]')).toContainText('Mood deleted');
    await expect(
        sharedPage.locator('[data-testid="mood-card"]', { hasText: 'To be deleted' }),
    ).toHaveCount(0);
});

// Needs its own fresh user - the assertion depends on there being zero moods
// logged yet, which the shared session above no longer satisfies.
test('weekly summary stat card appears after logging a mood', async ({ page }) => {
    await registerAndLogin(page);

    await expect(page.locator('[data-testid="stat-card-empty"]')).toBeVisible();

    await addMood(page, { text: 'First mood this week', rating: 6 });

    await expect(page.locator('[data-testid="stat-card"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="stat-card-empty"]')).toHaveCount(0);
});
