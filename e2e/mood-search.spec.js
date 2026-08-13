import { test, expect } from '@playwright/test';
import { registerAndLogin, addMood } from './helpers.js';

test('text search filters the mood list', async ({ page }) => {
    await registerAndLogin(page);
    await addMood(page, { text: 'Hiking trip', rating: 8, notes: 'Great weather for it' });
    await addMood(page, { text: 'Quiet evening', rating: 6, notes: 'Read a book' });

    await page.fill('#q', 'Hiking');
    await page.click('button:has-text("Filter")');

    await expect(page.locator('.mood-card', { hasText: 'Hiking trip' })).toBeVisible();
    await expect(page.locator('.mood-card', { hasText: 'Quiet evening' })).toHaveCount(0);

    await page.click('a:has-text("Clear")');
    await expect(page.locator('.mood-card', { hasText: 'Quiet evening' })).toBeVisible();
});

test('rating range filter narrows the list', async ({ page }) => {
    await registerAndLogin(page);
    await addMood(page, { text: 'Rough patch', rating: 2 });
    await addMood(page, { text: 'Pretty good', rating: 8 });

    await page.fill('#minRating', '7');
    await page.fill('#maxRating', '10');
    await page.click('button:has-text("Filter")');

    await expect(page.locator('.mood-card', { hasText: 'Pretty good' })).toBeVisible();
    await expect(page.locator('.mood-card', { hasText: 'Rough patch' })).toHaveCount(0);
});

test('pagination controls appear once there are more moods than one page', async ({ page }) => {
    await registerAndLogin(page);

    // MoodController.PAGE_SIZE is 5, so a 6th mood forces a second page.
    for (let i = 1; i <= 6; i++) {
        await addMood(page, { text: `Mood number ${i}`, rating: 5 });
    }

    await expect(page.locator('.pagination')).toBeVisible();
    await expect(page.locator('.pagination-status')).toContainText('Page 1 of 2');
    await expect(page.getByRole('link', { name: 'Next' })).toBeVisible();

    await page.getByRole('link', { name: 'Next' }).click();
    await expect(page.locator('.pagination-status')).toContainText('Page 2 of 2');
    await expect(page.getByRole('link', { name: 'Previous' })).toBeVisible();
});
