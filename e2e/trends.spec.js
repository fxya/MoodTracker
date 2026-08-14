import { test, expect } from '@playwright/test';
import { registerAndLogin, addMood } from './helpers.js';

test('trends page renders the mood table and trend chart, with weather sections hidden', async ({
    page,
}) => {
    await registerAndLogin(page);
    await addMood(page, { text: 'First entry', rating: 6 });
    await addMood(page, { text: 'Second entry', rating: 8 });

    const apiResponse = page.waitForResponse('**/api/moods');
    await page.goto('/moods');
    await apiResponse;

    await expect(page.locator('#allMoodsTable tbody tr')).toHaveCount(2);
    await expect(page.locator('#moodTrendChart')).toBeVisible();
    await expect(page.locator('#moodTrendChart')).toHaveAttribute('aria-label', /Mood Rating/);

    // No location was ever set for this user, so there's no weather data -
    // the weather-dependent sections should stay hidden rather than render empty.
    await expect(page.locator('#weatherEmptyState')).toBeVisible();
    await expect(page.locator('#temperatureSection')).toBeHidden();
    await expect(page.locator('#precipitationSection')).toBeHidden();
    await expect(page.locator('#correlationSection')).toBeHidden();
});

test('mood trend chart has a screen-reader-accessible data table alongside it', async ({
    page,
}) => {
    await registerAndLogin(page);
    await addMood(page, { text: 'First entry', rating: 6 });
    await addMood(page, { text: 'Second entry', rating: 8 });

    const apiResponse = page.waitForResponse('**/api/moods');
    await page.goto('/moods');
    await apiResponse;

    const table = page.locator('#moodTrendChart + .sr-only table');
    await expect(table).toHaveCount(1);
    await expect(table.locator('caption')).toHaveText('Mood Rating over time');
    await expect(table.locator('tbody tr')).toHaveCount(2);
});

test('the All Saved Moods table paginates once there are more than one page of entries', async ({
    page,
}) => {
    await registerAndLogin(page);
    for (let i = 1; i <= 12; i++) {
        await addMood(page, { text: `Entry ${i}`, rating: 5 });
    }

    const apiResponse = page.waitForResponse('**/api/moods');
    await page.goto('/moods');
    await apiResponse;

    const pagination = page.locator('[data-testid="pagination"]');
    await expect(pagination).toBeVisible();
    await expect(page.locator('#allMoodsTable tbody tr')).toHaveCount(10);
    await expect(page.locator('[data-testid="pagination-status"]')).toHaveText('Page 1 of 2');
    await expect(page.getByRole('button', { name: 'Previous' })).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Next' })).toBeEnabled();

    await page.getByRole('button', { name: 'Next' }).click();
    await expect(page.locator('#allMoodsTable tbody tr')).toHaveCount(2);
    await expect(page.locator('[data-testid="pagination-status"]')).toHaveText('Page 2 of 2');
    await expect(page.getByRole('button', { name: 'Next' })).toBeDisabled();

    await page.getByRole('button', { name: 'Previous' }).click();
    await expect(page.locator('#allMoodsTable tbody tr')).toHaveCount(10);
    await expect(page.locator('[data-testid="pagination-status"]')).toHaveText('Page 1 of 2');
});

test('mood calendar heatmap renders one cell per day with an aria-label', async ({ page }) => {
    await registerAndLogin(page);
    await addMood(page, { text: 'Only entry', rating: 7 });

    const apiResponse = page.waitForResponse('**/api/moods');
    await page.goto('/moods');
    await apiResponse;

    await expect(page.locator('#heatmapSection')).toBeVisible();
    const cells = page.locator('#heatmapGrid > div');
    // A single day still snaps out to a full Sun-Sat week (7 cells), with
    // only the logged day's cell carrying real data.
    await expect(cells).toHaveCount(7);
    await expect(page.locator('#heatmapGrid > div[aria-label*="average mood 7.0"]')).toHaveCount(1);
});
