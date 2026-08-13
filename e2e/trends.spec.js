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

    await expect(page.locator('tbody tr')).toHaveCount(2);
    await expect(page.locator('#moodTrendChart')).toBeVisible();

    // No location was ever set for this user, so there's no weather data -
    // the weather-dependent sections should stay hidden rather than render empty.
    await expect(page.locator('#weatherEmptyState')).toBeVisible();
    await expect(page.locator('#temperatureSection')).toBeHidden();
    await expect(page.locator('#precipitationSection')).toBeHidden();
    await expect(page.locator('#correlationSection')).toBeHidden();
});
