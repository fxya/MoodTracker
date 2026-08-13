import { test, expect } from '@playwright/test';
import { registerAndLogin, TEST_PASSWORD } from './helpers.js';

test('location and time zone persist after saving', async ({ page }) => {
    await registerAndLogin(page);

    await page.goto('/settings');
    await page.fill('#location', 'London');
    await page.selectOption('#time-zone', 'Europe/London');
    await page.click('button:has-text("Save Settings")');

    await expect(page.locator('[data-testid="alert-success"]')).toContainText('Settings saved');

    await page.reload();
    await expect(page.locator('#location')).toHaveValue('London');
    await expect(page.locator('#time-zone')).toHaveValue('Europe/London');
});

test('theme preference persists via localStorage and applies data-theme on reload', async ({
    page,
}) => {
    await registerAndLogin(page);
    await page.goto('/settings');

    await expect(page.locator('[data-testid="theme-option"][value="system"]')).toBeChecked();

    await Promise.all([
        page.waitForNavigation(),
        page.locator('[data-testid="theme-option"][value="dark"]').click(),
    ]);
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBe('dark');
    expect(await page.evaluate(() => document.documentElement.dataset.theme)).toBe('dark');
    await expect(page.locator('[data-testid="theme-option"][value="dark"]')).toBeChecked();

    await page.reload();
    await expect(page.locator('[data-testid="theme-option"][value="dark"]')).toBeChecked();
    expect(await page.evaluate(() => document.documentElement.dataset.theme)).toBe('dark');

    await Promise.all([
        page.waitForNavigation(),
        page.locator('[data-testid="theme-option"][value="system"]').click(),
    ]);
    expect(await page.evaluate(() => localStorage.getItem('theme'))).toBeNull();
});

test('CSV import adds valid rows and reports how many were skipped', async ({ page }) => {
    await registerAndLogin(page);
    await page.goto('/settings');

    const csv =
        'Date,Mood,Rating,Tag,Notes,TemperatureC,PrecipitationMm\r\n' +
        '2026-01-15T10:30:00Z,Imported Happy,8,Content,From a CSV,14.5,0.2\r\n' +
        'not-a-date,Broken row,3,,,,\r\n';

    await page.setInputFiles('#import-file', {
        name: 'moods.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from(csv),
    });
    await page.click('button:has-text("Import")');

    await expect(page.locator('[data-testid="alert-success"]')).toContainText(
        'Imported 1 of 2 row(s) from the CSV',
    );

    await page.goto('/moodtracker');
    const card = page.locator('[data-testid="mood-card"]', { hasText: 'Imported Happy' });
    await expect(card).toBeVisible();
    await expect(card.locator('[data-testid="mood-tag-badge"]')).toHaveText('Content');
    await expect(card).toContainText('14.5°C');
    await expect(page.locator('[data-testid="mood-card"]', { hasText: 'Broken row' })).toHaveCount(
        0,
    );
});

test('changing password takes effect on next login', async ({ page }) => {
    const { username } = await registerAndLogin(page);
    const newPassword = 'NewPass456!';

    await page.goto('/settings');
    await page.fill('#current-password', TEST_PASSWORD);
    await page.fill('#new-password', newPassword);
    await page.fill('#confirm-password', newPassword);
    await page.click('button:has-text("Change Password")');

    await expect(page.locator('[data-testid="alert-success"]')).toContainText('Password changed');

    await page.click('button:has-text("Logout")');
    await page.waitForURL('**/login**');

    // Only the "new password now works" side is checked here (not "old
    // password now fails") to keep this suite's total POST /login count well
    // under the app's own rate limit - see the note in mood-crud.spec.js.
    await page.fill('#username', username);
    await page.fill('#password', newPassword);
    await page.click('button:has-text("Log in")');
    await page.waitForURL('**/moodtracker');
});

test('deleting the account logs the user out and invalidates their credentials', async ({
    page,
}) => {
    const { username } = await registerAndLogin(page);

    await page.goto('/settings');
    await page.fill('#delete-password', TEST_PASSWORD);
    page.once('dialog', (dialog) => dialog.accept());
    await page.click('button:has-text("Delete Account")');

    await page.waitForURL('**/login?accountDeleted**');
    await expect(page.locator('[data-testid="alert-success"]')).toContainText(
        'account has been deleted',
    );

    await page.fill('#username', username);
    await page.fill('#password', TEST_PASSWORD);
    await page.click('button:has-text("Log in")');
    await page.waitForURL('**/login?error**');
});
