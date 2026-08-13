import { test, expect } from '@playwright/test';
import { registerAndLogin } from './helpers.js';

// The actual reset link can't be exercised here - there's no way for Playwright to read
// a real email, and CI/this sandbox can't reach a real SMTP relay anyway (same reasoning
// as weather/backfill being excluded from e2e, see README). These specs cover the request
// flow's UI and the invalid-token state; the token-based reset itself is covered by
// PasswordResetControllerTest.

test('requesting a reset link for a real username shows the generic success message', async ({
    page,
}) => {
    const { username } = await registerAndLogin(page);

    await page.click('button:has-text("Logout")');
    await page.waitForURL('**/login**');

    await page.goto('/forgot-password');
    await page.fill('#username', username);
    await page.click('button:has-text("Send reset link")');

    await expect(page.locator('[data-testid="alert-success"]')).toContainText(
        "we've sent a password reset link",
    );
});

test('requesting a reset link for an unknown username shows the same generic message', async ({
    page,
}) => {
    await page.goto('/forgot-password');
    await page.fill('#username', 'no-such-user-at-all');
    await page.click('button:has-text("Send reset link")');

    await expect(page.locator('[data-testid="alert-success"]')).toContainText(
        "we've sent a password reset link",
    );
});

test('a bogus reset token shows the invalid/expired state, not a form', async ({ page }) => {
    await page.goto('/reset-password?token=this-token-does-not-exist');

    await expect(page.locator('[data-testid="alert-danger"]')).toContainText(
        'invalid or has expired',
    );
    await expect(page.locator('#new-password')).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Request a new link' })).toBeVisible();
});
