import { test, expect } from '@playwright/test';
import { registerAndLogin, addMood } from './helpers.js';

// Combined into fewer register/login round trips - see the note in
// mood-crud.spec.js about the app's own POST /login rate limit budget.

test('CSV export includes the download header, the logged mood, and its link is visible', async ({
    page,
}) => {
    await registerAndLogin(page);
    await addMood(page, { text: 'Exportable mood', rating: 7, notes: 'For the export test' });

    await expect(page.getByRole('link', { name: 'Export CSV' })).toHaveAttribute(
        'href',
        '/moodtracker/export/csv',
    );

    const response = await page.request.get('/moodtracker/export/csv');

    expect(response.status()).toBe(200);
    expect(response.headers()['content-disposition']).toContain('attachment; filename="moods.csv"');
    const body = await response.text();
    expect(body).toContain('Date,Mood,Rating,Tag,Notes,TemperatureC,PrecipitationMm');
    expect(body).toContain('Exportable mood');
    expect(body).toContain('For the export test');
});

test('JSON export includes the download header, the logged mood, and its link is visible', async ({
    page,
}) => {
    await registerAndLogin(page);
    await addMood(page, { text: 'Exportable JSON mood', rating: 3 });

    await expect(page.getByRole('link', { name: 'Export JSON' })).toHaveAttribute(
        'href',
        '/moodtracker/export/json',
    );

    const response = await page.request.get('/moodtracker/export/json');

    expect(response.status()).toBe(200);
    expect(response.headers()['content-disposition']).toContain(
        'attachment; filename="moods.json"',
    );
    const body = await response.json();
    expect(Array.isArray(body)).toBe(true);
    expect(body.some((mood) => mood.mood === 'Exportable JSON mood' && mood.moodRating === 3)).toBe(
        true,
    );
});
