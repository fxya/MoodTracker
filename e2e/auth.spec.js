import { test, expect } from '@playwright/test';
import { registerAndLogin, uniqueUsername, TEST_PASSWORD } from './helpers.js';

test('register redirects to login with a success message, then login lands on the mood tracker', async ({
    page,
}) => {
    const username = uniqueUsername();

    await page.goto('/register');
    await page.fill('#username', username);
    await page.fill('#password', TEST_PASSWORD);
    await page.click('button:has-text("Register")');

    await page.waitForURL('**/login');
    await expect(page.locator('.alert-success')).toContainText('Registration successful');

    await page.fill('#username', username);
    await page.fill('#password', TEST_PASSWORD);
    await page.click('button:has-text("Log in")');

    await page.waitForURL('**/moodtracker');
    await expect(page.getByText(username)).toBeVisible();
    await expect(page.getByRole('link', { name: 'Trends' })).toBeVisible();
});

test('wrong password shows an error and does not log in', async ({ page }) => {
    const { username } = await registerAndLogin(page);

    // Log out first so we have a clean unauthenticated attempt to test.
    await page.click('button:has-text("Logout")');
    await page.waitForURL('**/login**');

    await page.fill('#username', username);
    await page.fill('#password', 'definitely-wrong');
    await page.click('button:has-text("Log in")');

    await page.waitForURL('**/login?error**');
    await expect(page.locator('.alert-danger')).toContainText('Invalid username or password');
});

test('logout returns to login with a logged-out message', async ({ page }) => {
    await registerAndLogin(page);

    await page.click('button:has-text("Logout")');

    await page.waitForURL('**/login?logout**');
    await expect(page.locator('.alert-success')).toContainText('logged out');
});
